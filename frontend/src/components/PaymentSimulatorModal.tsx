import React, { useState } from 'react';
import { X, Play, CheckCircle2, AlertTriangle, Clock, ExternalLink, Sparkles, ArrowRightLeft } from 'lucide-react';
import { apiRequest } from '../services/api';
import { Payment } from '../types';
import { StatusBadge } from './StatusBadge';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onPaymentCreated: (payment: Payment) => void;
}

export const PaymentSimulatorModal: React.FC<Props> = ({ isOpen, onClose, onPaymentCreated }) => {
  const [orderId, setOrderId] = useState(`ORD-${Date.now().toString().slice(-6)}`);
  const [amountMajor, setAmountMajor] = useState('128.00');
  const [currency, setCurrency] = useState('INR');
  const [paymentMethod, setPaymentMethod] = useState('CARD');
  const [preferredProvider, setPreferredProvider] = useState('AUTO');
  const [customerName, setCustomerName] = useState('Acme Enterprise Merchant');
  const [customerEmail, setCustomerEmail] = useState('billing@acme.com');
  const [customerPhone, setCustomerPhone] = useState('9876543210');

  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [result, setResult] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCurrencyChange = (newCurrency: string) => {
    setCurrency(newCurrency);
  };

  const handleLaunchRazorpayModal = (paymentToUse?: Payment) => {
    const currentPayment = paymentToUse || result;
    if (!currentPayment || currentPayment.status === 'SUCCEEDED') return;
    const rzpOrderId = currentPayment.metadata?.orderId || currentPayment.attempts?.[currentPayment.attempts.length - 1]?.providerReference;
    const keyId = currentPayment.metadata?.keyId || 'rzp_test_Rsa5CdGXTTiD3R';

    const loadRazorpayScript = () => {
      return new Promise<boolean>((resolve) => {
        if ((window as any).Razorpay) return resolve(true);
        const script = document.createElement('script');
        script.src = 'https://checkout.razorpay.com/v1/checkout.js';
        script.onload = () => resolve(true);
        script.onerror = () => resolve(false);
        document.body.appendChild(script);
      });
    };

    loadRazorpayScript().then((loaded) => {
      if (!loaded || !(window as any).Razorpay) {
        alert('Could not load Razorpay SDK. Please check your network connection.');
        return;
      }

      const options: any = {
        key: keyId,
        amount: currentPayment.amountMinor,
        currency: currentPayment.currency || 'INR',
        name: 'FluxPay Enterprise',
        description: `Order ${currentPayment.orderId}`,
        handler: async (response: any) => {
          console.log('Razorpay payment successful:', response);
          setError(null);
          await handleSyncStatus(currentPayment.id);
        },
        prefill: {
          name: customerName,
          email: customerEmail,
          contact: customerPhone,
        },
        notes: {
          order_id: currentPayment.orderId,
          payment_id: currentPayment.id,
          platform: 'FluxPay Multi-Gateway Orchestrator',
        },
        theme: {
          color: '#191816',
        },
        modal: {
          ondismiss: () => {
            console.log('Razorpay checkout modal closed by user');
          },
        },
      };

      const isRealOrder = rzpOrderId && rzpOrderId.startsWith('order_') && !rzpOrderId.startsWith('order_sim_');
      if (isRealOrder) {
        options.order_id = rzpOrderId;
      }

      try {
        const rzpInstance = new (window as any).Razorpay(options);
        rzpInstance.on('payment.failed', async (resp: any) => {
          console.warn('Razorpay payment failed:', resp.error);
          let desc = resp.error?.description || 'Payment rejected';
          if (desc.includes('International cards are not supported')) {
            desc += ' — Use domestic test card 4111 1111 1111 1111 (Visa) or UPI (success@razorpay).';
          }
          setError(`Razorpay: ${desc}. Triggering smart failover to next priority gateway...`);
          // Automatically trigger failover to next gateway in priority chain!
          await handleTriggerFailover(currentPayment.id, desc);
        });
        rzpInstance.open();
      } catch (err: any) {
        console.error('Error opening Razorpay modal:', err);
        setError(err.message || 'Failed to open Razorpay checkout modal');
      }
    });
  };

  const handleTriggerFailover = async (paymentId: string, reason?: string) => {
    setLoading(true);
    try {
      const updated = await apiRequest<Payment>(`/payments/${paymentId}/failover`, {
        method: 'POST',
        body: JSON.stringify({ reason: reason || 'Gateway payment declined or failed. Transferred to next gateway.' }),
      });
      setResult(updated);
      onPaymentCreated(updated);

      const latestAttempt = updated.attempts?.[updated.attempts.length - 1];
      const newProvider = latestAttempt?.providerCode?.toLowerCase();

      if (updated.status === 'SUCCEEDED') {
        setError(null);
      } else if (newProvider === 'cashfree' && updated.redirectUrl) {
        window.open(updated.redirectUrl, '_blank');
      } else if (newProvider === 'razorpay') {
        handleLaunchRazorpayModal(updated);
      }
    } catch (failoverErr: any) {
      console.error('Failover failed:', failoverErr);
      setError(`Failover failed: ${failoverErr.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleSimulate = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const amountMinor = Math.round(parseFloat(amountMajor) * 100);
      const idempotencyKey = crypto.randomUUID();

      const payment = await apiRequest<Payment>('/payments', {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          orderId,
          amountMinor,
          currency,
          paymentMethod,
          returnUrl: window.location.origin + '/payments',
          preferredProvider: preferredProvider === 'AUTO' ? null : preferredProvider.toLowerCase(),
        }),
      });

      setResult(payment);
      onPaymentCreated(payment);
      setOrderId(`ORD-${Date.now().toString().slice(-6)}`);

      // AUTOMATE REAL PAYMENT FLOW:
      const latestAttempt = payment.attempts?.[payment.attempts.length - 1];
      const activeProvider = latestAttempt?.providerCode?.toLowerCase();

      if (payment.status !== 'SUCCEEDED') {
        if (activeProvider === 'razorpay') {
          // Automatically launch real Razorpay checkout modal immediately
          handleLaunchRazorpayModal(payment);
        } else if (activeProvider === 'cashfree' && payment.redirectUrl) {
          // Open Cashfree hosted checkout page
          window.open(payment.redirectUrl, '_blank');
        }
      }
    } catch (err: any) {
      setError(err.message || 'Payment execution failed');
    } finally {
      setLoading(false);
    }
  };

  const handleSyncStatus = async (paymentIdToSync?: string) => {
    const targetId = paymentIdToSync || result?.id;
    if (!targetId) return;
    setSyncing(true);
    try {
      const updated = await apiRequest<Payment>(`/payments/${targetId}/sync`, { method: 'POST' });
      setResult(updated);
      onPaymentCreated(updated);
      if (updated.status === 'SUCCEEDED') {
        setError(null);
      }
    } catch (err: any) {
      console.error('Failed to sync provider status:', err);
    } finally {
      setSyncing(false);
    }
  };

  const latestAttempt = result?.attempts?.[result.attempts.length - 1];
  const activeProvider = latestAttempt?.providerCode?.toLowerCase();

  return (
    <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white border border-[#EBE6DD] rounded-3xl max-w-xl w-full shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200 max-h-[92vh] flex flex-col">
        <div className="px-6 py-4 border-b border-[#EBE6DD] flex items-center justify-between shrink-0">
          <div className="flex items-center space-x-2.5">
            <div className="w-8 h-8 rounded-full bg-[#E2F899] flex items-center justify-center text-[#2C410F]">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-semibold text-base text-[#191816]">Enterprise Virtual Terminal</h3>
              <p className="text-xs text-[#78716C]">Production Payment Gateway Orchestration & Live Multi-Gateway Checkout</p>
            </div>
          </div>
          <button onClick={onClose} className="text-[#78716C] hover:text-[#191816] p-1.5 rounded-lg hover:bg-[#FAF7F2]">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto">
          <form onSubmit={handleSimulate} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Order Reference ID</label>
                <input
                  type="text"
                  required
                  value={orderId}
                  onChange={(e) => setOrderId(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Amount</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.50"
                  required
                  value={amountMajor}
                  onChange={(e) => setAmountMajor(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                />
              </div>
            </div>

            {/* Customer Prefill Details */}
            <div className="p-3.5 bg-[#FAF7F2] border border-[#EBE6DD] rounded-2xl space-y-2">
              <div className="flex items-center justify-between text-xs font-semibold text-[#57534E]">
                <span className="flex items-center space-x-1.5 text-[#191816]">
                  <Sparkles className="w-3.5 h-3.5 text-[#FF5A36]" />
                  <span>Customer Billing & Identity Details</span>
                </span>
                <span className="text-[10px] text-[#78716C] font-normal">Passed to Gateway Checkout</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <div>
                  <label className="block text-[11px] text-[#78716C] mb-0.5">Name</label>
                  <input
                    type="text"
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                    className="w-full bg-white border border-[#EBE6DD] rounded-lg px-2.5 py-1.5 text-xs text-[#191816] focus:outline-none focus:border-[#FF5A36]"
                  />
                </div>
                <div>
                  <label className="block text-[11px] text-[#78716C] mb-0.5">Email</label>
                  <input
                    type="email"
                    value={customerEmail}
                    onChange={(e) => setCustomerEmail(e.target.value)}
                    className="w-full bg-white border border-[#EBE6DD] rounded-lg px-2.5 py-1.5 text-xs text-[#191816] focus:outline-none focus:border-[#FF5A36]"
                  />
                </div>
                <div>
                  <label className="block text-[11px] text-[#78716C] mb-0.5">Phone</label>
                  <input
                    type="tel"
                    value={customerPhone}
                    onChange={(e) => setCustomerPhone(e.target.value)}
                    className="w-full bg-white border border-[#EBE6DD] rounded-lg px-2.5 py-1.5 text-xs text-[#191816] focus:outline-none focus:border-[#FF5A36]"
                  />
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Currency</label>
                <select
                  value={currency}
                  onChange={(e) => handleCurrencyChange(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                >
                  <option value="INR">INR (₹)</option>
                  <option value="USD">USD ($)</option>
                  <option value="EUR">EUR (€)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Routing Policy</label>
                <select
                  value={preferredProvider}
                  onChange={(e) => setPreferredProvider(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                >
                  <option value="AUTO">✨ Dynamic Smart Routing (Active-Active)</option>
                  <option value="RAZORPAY">Razorpay Gateway</option>
                  <option value="STRIPE">Stripe Gateway</option>
                  <option value="CASHFREE">Cashfree Gateway</option>
                  <option value="ADYEN">Adyen Gateway</option>
                  <option value="PAYU">PayU Gateway</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Payment Method</label>
                <select
                  value={paymentMethod}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                >
                  <option value="CARD">Credit/Debit Card</option>
                  <option value="UPI">UPI</option>
                  <option value="NETBANKING">Netbanking</option>
                  <option value="WALLET">Digital Wallet</option>
                </select>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 bg-[#111827] hover:bg-black disabled:opacity-50 text-white font-medium rounded-full shadow-md transition-all flex items-center justify-center space-x-2 text-sm cursor-pointer"
            >
              {loading ? (
                <Clock className="w-4 h-4 animate-spin text-white" />
              ) : (
                <>
                  <Play className="w-3.5 h-3.5 fill-current text-[#E2F899]" />
                  <span>Process Secure Payment</span>
                </>
              )}
            </button>
          </form>

          {error && (
            <div className="p-4 rounded-2xl bg-[#FED7CA]/70 border border-[#FCA5A5] text-[#991B1B] text-xs flex items-start space-x-3">
              <AlertTriangle className="w-4 h-4 shrink-0 text-[#991B1B] mt-0.5" />
              <div className="flex-1">
                <p className="font-semibold">Execution / Gateway Notice</p>
                <p className="mt-0.5 leading-relaxed">{error}</p>
                {result && result.status !== 'SUCCEEDED' && (
                  <button
                    type="button"
                    onClick={() => handleTriggerFailover(result.id, error)}
                    className="mt-2.5 px-3 py-1 bg-[#991B1B] hover:bg-[#7F1D1D] text-white rounded-lg text-xs font-semibold flex items-center space-x-1.5 shadow-sm transition-all cursor-pointer"
                  >
                    <ArrowRightLeft className="w-3.5 h-3.5" />
                    <span>Cascade to Next Priority Gateway Now</span>
                  </button>
                )}
              </div>
            </div>
          )}

          {result && (
            <div className={`p-4 rounded-2xl border space-y-3 ${
              result.status === 'SUCCEEDED'
                ? 'bg-[#E2F899] border-[#D4EE7F] text-[#191816]'
                : result.status === 'REQUIRES_ACTION' || result.status === 'PROCESSING' || result.status === 'PENDING'
                ? 'bg-[#FEF3C7] border-[#FDE68A] text-[#92400E]'
                : 'bg-[#FED7CA] border-[#FCA5A5] text-[#991B1B]'
            }`}>
              <div className={`flex items-center justify-between border-b pb-2 ${
                result.status === 'SUCCEEDED'
                  ? 'border-[#D4EE7F]'
                  : result.status === 'REQUIRES_ACTION' || result.status === 'PENDING' || result.status === 'PROCESSING'
                  ? 'border-[#FDE68A]'
                  : 'border-[#FCA5A5]'
              }`}>
                <div className="flex items-center space-x-2">
                  {result.status === 'SUCCEEDED' ? (
                    <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                  ) : result.status === 'REQUIRES_ACTION' || result.status === 'PENDING' || result.status === 'PROCESSING' ? (
                    <Clock className="w-4 h-4 text-[#D97706] animate-pulse" />
                  ) : (
                    <AlertTriangle className="w-4 h-4 text-[#991B1B]" />
                  )}
                  <span className="text-xs font-semibold">
                    {result.status === 'SUCCEEDED'
                      ? 'Payment Successfully Captured & Settled'
                      : result.status === 'REQUIRES_ACTION'
                      ? 'Payment Initiated · Complete Real Checkout on Gateway'
                      : result.status === 'PENDING' || result.status === 'PROCESSING'
                      ? 'Payment In-Flight · Awaiting Customer Completion'
                      : 'Payment Failed on Gateway'}
                  </span>
                </div>
                <StatusBadge status={result.status} size="sm" />
              </div>

              {/* Multi-Gateway Cascading Journey */}
              {result.attempts && result.attempts.length > 1 && (
                <div className="p-3.5 rounded-2xl bg-[#191816] text-white text-xs space-y-2.5 shadow-md">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-1.5 font-semibold text-[#E2F899]">
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>Multi-Gateway Smart Failover Executed</span>
                    </div>
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-[#E2F899]/20 text-[#E2F899] font-mono">
                      {result.attempts.length} Cascaded Attempts
                    </span>
                  </div>

                  <div className="space-y-1.5 font-mono text-[11px] pt-1 border-t border-white/10">
                    {result.attempts.map((att) => (
                      <div
                        key={att.id}
                        className="flex items-center justify-between px-2.5 py-1.5 rounded-lg bg-white/5 border border-white/10"
                      >
                        <span className="text-white/80">
                          Attempt #{att.attemptNumber} · {att.providerCode.toUpperCase()}
                        </span>
                        <span className={att.status === 'SUCCEEDED' ? 'text-[#E2F899] font-semibold' : 'text-[#FED7CA]'}>
                          {att.status} {att.failureCode ? `[${att.failureCode}]` : ''}
                        </span>
                      </div>
                    ))}
                  </div>

                  <p className="text-[11px] text-white/60 leading-relaxed pt-1">
                    Processor {result.attempts[0].providerCode.toUpperCase()} was unavailable or rejected. FluxPay automatically triggered circuit failover to {result.attempts[result.attempts.length - 1].providerCode.toUpperCase()} to rescue the payment.
                  </p>
                </div>
              )}

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div>
                  <span className="text-[#57534E]">Payment ID:</span>
                  <p className="font-mono text-[#191816] font-medium truncate">{result.id}</p>
                </div>
                <div>
                  <span className="text-[#57534E]">Amount / Currency:</span>
                  <p className="text-[#191816] font-medium">
                    {(result.amountMinor / 100).toFixed(2)} {result.currency}
                  </p>
                </div>
                <div>
                  <span className="text-[#57534E]">Settled / Active Route:</span>
                  <p className="text-[#191816] font-medium uppercase font-semibold">
                    {activeProvider || 'Routed Provider'}
                  </p>
                </div>
                <div>
                  <span className="text-[#57534E]">Attempt Status:</span>
                  <p className="text-[#191816] font-medium">
                    {latestAttempt?.status || 'N/A'}
                  </p>
                </div>
              </div>

              {/* Razorpay Actions */}
              {activeProvider === 'razorpay' && (
                <div className="pt-2 border-t border-[#D4EE7F] space-y-2">
                  {result.status === 'SUCCEEDED' ? (
                    <div className="flex items-center space-x-2 text-xs font-semibold text-[#2C410F]">
                      <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                      <span>Payment completed & captured on Razorpay. Order is closed.</span>
                    </div>
                  ) : (
                    <>
                      <div className="bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl p-2.5 text-[11px] text-[#57534E] space-y-1">
                        <div className="font-semibold text-[#191816] flex items-center space-x-1">
                          <Sparkles className="w-3 h-3 text-[#FF5A36]" />
                          <span>Razorpay Live Test Credentials:</span>
                        </div>
                        <div>• <strong>Domestic Card:</strong> <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">4111 1111 1111 1111</code> (Visa) · CVV: <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">123</code> · OTP: <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">1234</code></div>
                        <div>• <strong>UPI:</strong> Test VPA <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">success@razorpay</code> or scan UPI QR in modal</div>
                      </div>

                      <div className="flex flex-wrap gap-2 items-center justify-between">
                        <div className="flex flex-wrap gap-2 items-center">
                          <button
                            type="button"
                            onClick={() => handleLaunchRazorpayModal(result)}
                            className="px-3.5 py-1.5 bg-[#191816] hover:bg-black text-white text-xs font-semibold rounded-full shadow-sm flex items-center space-x-1.5 transition-all cursor-pointer"
                          >
                            <Sparkles className="w-3.5 h-3.5 text-[#E2F899]" />
                            <span>Launch Razorpay Checkout</span>
                          </button>

                          {result.redirectUrl && (
                            <a
                              href={result.redirectUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-[#0369A1] underline text-xs font-semibold flex items-center space-x-1"
                            >
                              <span>Open Hosted Page ↗</span>
                              <ExternalLink className="w-3 h-3" />
                            </a>
                          )}
                        </div>

                        <div className="flex items-center space-x-2">
                          <button
                            type="button"
                            onClick={() => handleTriggerFailover(result.id, 'Manually switched to next gateway')}
                            className="px-3 py-1.5 bg-[#FED7CA] hover:bg-[#FCA5A5] text-[#991B1B] text-xs font-semibold rounded-full shadow-sm flex items-center space-x-1 transition-all cursor-pointer"
                          >
                            <ArrowRightLeft className="w-3 h-3" />
                            <span>Try Next Gateway</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => handleSyncStatus(result.id)}
                            disabled={syncing}
                            className="px-3 py-1.5 bg-white/80 hover:bg-white text-[#191816] border border-[#D4EE7F] text-xs font-medium rounded-full shadow-sm flex items-center space-x-1 cursor-pointer"
                          >
                            {syncing ? <Clock className="w-3 h-3 animate-spin" /> : <span>Sync Status</span>}
                          </button>
                        </div>
                      </div>
                    </>
                  )}
                </div>
              )}

              {/* Cashfree Actions */}
              {activeProvider === 'cashfree' && (
                <div className="pt-2 border-t border-[#D4EE7F] flex flex-wrap gap-2 items-center justify-between">
                  {result.status === 'SUCCEEDED' ? (
                    <div className="flex items-center space-x-2 text-xs font-semibold text-[#2C410F]">
                      <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                      <span>Payment captured on Cashfree Sandbox.</span>
                    </div>
                  ) : (
                    <>
                      <div className="flex items-center space-x-2">
                        {result.redirectUrl && (
                          <a
                            href={result.redirectUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="px-3.5 py-1.5 bg-[#191816] hover:bg-black text-white text-xs font-semibold rounded-full shadow-sm flex items-center space-x-1.5 transition-all cursor-pointer"
                          >
                            <Sparkles className="w-3.5 h-3.5 text-[#E2F899]" />
                            <span>Open Cashfree Checkout ↗</span>
                            <ExternalLink className="w-3 h-3" />
                          </a>
                        )}
                        <button
                          type="button"
                          onClick={() => handleTriggerFailover(result.id, 'Cascaded from Cashfree')}
                          className="px-3 py-1.5 bg-[#FED7CA] hover:bg-[#FCA5A5] text-[#991B1B] text-xs font-semibold rounded-full shadow-sm flex items-center space-x-1 transition-all cursor-pointer"
                        >
                          <ArrowRightLeft className="w-3 h-3" />
                          <span>Failover to Next Gateway</span>
                        </button>
                      </div>

                      <button
                        type="button"
                        onClick={() => handleSyncStatus(result.id)}
                        disabled={syncing}
                        className="px-3 py-1.5 bg-white/80 hover:bg-white text-[#191816] border border-[#D4EE7F] text-xs font-medium rounded-full shadow-sm flex items-center space-x-1 cursor-pointer"
                      >
                        {syncing ? <Clock className="w-3 h-3 animate-spin" /> : <span>Sync Status</span>}
                      </button>
                    </>
                  )}
                </div>
              )}

              {/* Stripe Actions */}
              {activeProvider === 'stripe' && (
                <div className="pt-2 border-t border-[#D4EE7F] flex flex-wrap gap-2 items-center justify-between text-xs">
                  <div className="flex items-center space-x-1.5 text-[#2C410F] font-semibold">
                    <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                    <span>Stripe Payment Intent {result.status}</span>
                  </div>
                  {latestAttempt?.providerReference?.startsWith('pi_') && (
                    <a
                      href={`https://dashboard.stripe.com/test/payments/${latestAttempt.providerReference}`}
                      target="_blank"
                      rel="noreferrer"
                      className="text-[#635BFF] underline font-semibold flex items-center space-x-1"
                    >
                      <span>View in Stripe Dashboard</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  )}
                </div>
              )}

              {/* Adyen Actions */}
              {activeProvider === 'adyen' && (
                <div className="pt-2 border-t border-[#D4EE7F] flex flex-wrap gap-2 items-center justify-between text-xs">
                  <div className="flex items-center space-x-1.5 text-[#2C410F] font-semibold">
                    <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                    <span>Adyen Gateway: {result.status}</span>
                  </div>
                  {result.redirectUrl && result.status !== 'SUCCEEDED' && (
                    <a
                      href={result.redirectUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-[#0369A1] underline font-semibold flex items-center space-x-1"
                    >
                      <span>Complete 3DS Action</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
