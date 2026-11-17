import React, { useState } from 'react';
import { X, Play, CheckCircle2, AlertTriangle, Clock, ExternalLink, Sparkles } from 'lucide-react';
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
  const [scenario, setScenario] = useState('AUTO_FAILOVER');
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [result, setResult] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCurrencyChange = (newCurrency: string) => {
    setCurrency(newCurrency);
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
          testScenario: scenario,
          preferredProvider: preferredProvider === 'AUTO' ? null : preferredProvider.toLowerCase(),
        }),
      });

      setResult(payment);
      onPaymentCreated(payment);
      // Generate fresh order ID for next run
      setOrderId(`ORD-${Date.now().toString().slice(-6)}`);
    } catch (err: any) {
      setError(err.message || 'Payment execution failed');
    } finally {
      setLoading(false);
    }
  };

  const handleSyncStatus = async () => {
    if (!result) return;
    setSyncing(true);
    try {
      const updated = await apiRequest<Payment>(`/payments/${result.id}/sync`, { method: 'POST' });
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

  const handleLaunchRazorpayModal = () => {
    if (!result || result.status === 'SUCCEEDED') return;
    const rzpOrderId = result.metadata?.orderId || result.attempts?.[0]?.providerReference;
    const keyId = result.metadata?.keyId || 'rzp_test_Rsa5CdGXTTiD3R';

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
        amount: result.amountMinor,
        currency: result.currency || 'INR',
        name: 'FluxPay Enterprise',
        description: `Order ${result.orderId}`,
        handler: async (response: any) => {
          console.log('Razorpay payment successful:', response);
          setError(null);
          await handleSyncStatus();
        },
        prefill: {
          name: 'Acme Test Merchant',
          email: 'admin@acme.com',
          contact: '9999999999',
        },
        theme: {
          color: '#191816',
        },
        modal: {
          ondismiss: () => {
            console.log('Razorpay checkout modal closed');
          },
        },
      };

      // Only attach order_id if it is a real Razorpay server-generated order (never a simulated ref)
      const isRealOrder = rzpOrderId && rzpOrderId.startsWith('order_') && !rzpOrderId.startsWith('order_sim_');
      if (isRealOrder) {
        options.order_id = rzpOrderId;
      }

      try {
        const rzpInstance = new (window as any).Razorpay(options);
        rzpInstance.on('payment.failed', (resp: any) => {
          console.warn('Razorpay payment failed:', resp.error);
          if (resp.error?.description) {
            let desc = resp.error.description;
            if (desc.includes('International cards are not supported')) {
              desc += ' — Use domestic test card 4111 1111 1111 1111 (Visa) or UPI (success@razorpay). Do not use 4242 international card.';
            }
            setError(`Razorpay: ${desc}`);
          }
        });
        rzpInstance.open();
      } catch (err: any) {
        console.error('Error opening Razorpay modal:', err);
        setError(err.message || 'Failed to open Razorpay checkout modal');
      }
    });
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white border border-[#EBE6DD] rounded-3xl max-w-xl w-full shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
        <div className="px-6 py-5 border-b border-[#EBE6DD] flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="w-8 h-8 rounded-full bg-[#FED7CA] flex items-center justify-center text-[#FF5A36]">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-semibold text-base text-[#191816]">Payment Orchestration Simulator</h3>
              <p className="text-xs text-[#78716C]">Test multi-gateway routing, timeouts, and failovers</p>
            </div>
          </div>
          <button onClick={onClose} className="text-[#78716C] hover:text-[#191816] p-1.5 rounded-lg hover:bg-[#FAF7F2]">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSimulate} className="p-6 space-y-4">
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

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
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
              <label className="block text-xs font-semibold text-[#57534E] mb-1">Route Gateway</label>
              <select
                value={preferredProvider}
                onChange={(e) => setPreferredProvider(e.target.value)}
                className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
              >
                <option value="AUTO">✨ Auto (Intelligent Dynamic Routing)</option>
                <option value="RAZORPAY">Razorpay (Live Sandbox)</option>
                <option value="STRIPE">Stripe (Live Sandbox)</option>
                <option value="CASHFREE">Cashfree (Live Sandbox)</option>
                <option value="ADYEN">Adyen (Live Sandbox)</option>
                <option value="PAYU">PayU (Live Sandbox)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[#57534E] mb-1">Method</label>
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

            <div>
              <label className="block text-xs font-semibold text-[#57534E] mb-1">Orchestration Scenario</label>
              <select
                value={scenario}
                onChange={(e) => setScenario(e.target.value)}
                className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
              >
                <option value="AUTO_FAILOVER">⚡ SMART FAILOVER (Simulate Gateway 502 ➔ Auto-Cascade to Backup)</option>
                <option value="REAL">REAL (Live Multi-Gateway Auto-Route)</option>
                <option value="SUCCESS">Simulate Success (Direct 200)</option>
                <option value="TIMEOUT">Simulate Ambiguous Timeout (➔ Reconcile)</option>
                <option value="DECLINE">Simulate Card Decline</option>
                <option value="REQUIRES_ACTION">Simulate 3DS Challenge</option>
              </select>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 px-4 bg-[#111827] hover:bg-black disabled:opacity-50 text-white font-medium rounded-full shadow-md transition-all flex items-center justify-center space-x-2 text-sm"
          >
            {loading ? (
              <Clock className="w-4 h-4 animate-spin text-white" />
            ) : (
              <>
                <Play className="w-3.5 h-3.5 fill-current text-[#E2F899]" />
                <span>Execute Payment Orchestration</span>
              </>
            )}
          </button>
        </form>

        {error && (
          <div className="mx-6 mb-6 p-4 rounded-2xl bg-[#FED7CA]/70 border border-[#FCA5A5] text-[#991B1B] text-xs flex items-start space-x-3">
            <AlertTriangle className="w-4 h-4 shrink-0 text-[#991B1B] mt-0.5" />
            <div>
              <p className="font-semibold">Execution Error</p>
              <p className="mt-0.5">{error}</p>
            </div>
          </div>
        )}

        {result && (
          <div className="mx-6 mb-6 p-4 rounded-2xl bg-[#E2F899] border border-[#D4EE7F] text-[#191816] space-y-3">
            <div className="flex items-center justify-between border-b border-[#D4EE7F] pb-2">
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                <span className="text-xs font-semibold">Payment Processed Successfully</span>
              </div>
              <StatusBadge status={result.status} size="sm" />
            </div>

            {/* Smart Cascading Failover Journey (when multiple attempts were executed) */}
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
                  Primary processor {result.attempts[0].providerCode.toUpperCase()} encountered downtime. FluxPay automatically triggered circuit failover to {result.attempts[result.attempts.length - 1].providerCode.toUpperCase()} and rescued the payment.
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
                <span className="text-[#57534E]">Settled Route:</span>
                <p className="text-[#191816] font-medium uppercase font-semibold">
                  {result.attempts?.[result.attempts.length - 1]?.providerCode || 'Routed Provider'}
                </p>
              </div>
              <div>
                <span className="text-[#57534E]">Attempt Status:</span>
                <p className="text-[#191816] font-medium">
                  {result.attempts?.[result.attempts.length - 1]?.status || 'N/A'}
                </p>
              </div>
            </div>

            {/* Razorpay Actions */}
            {result.attempts?.[0]?.providerCode?.toLowerCase() === 'razorpay' && (
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
                        <span>Razorpay Test Credentials Guide:</span>
                      </div>
                      <div>• <strong>Domestic Card:</strong> <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">4111 1111 1111 1111</code> (Visa) · CVV: <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">123</code> · OTP: <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">1234</code></div>
                      <div>• <strong>UPI:</strong> Use test VPA <code className="bg-white px-1 py-0.5 rounded border border-[#EBE6DD] text-[#191816]">success@razorpay</code> or select UPI QR in modal</div>
                      <div className="text-[10px] text-[#78716C] italic">* Note: Avoid 4242 4242 4242 4242 international card as Indian test gateways reject cross-border cards by default.</div>
                    </div>

                    <div className="flex flex-wrap gap-2 items-center justify-between">
                      <button
                        type="button"
                        onClick={handleLaunchRazorpayModal}
                        className="px-3.5 py-1.5 bg-[#191816] hover:bg-black text-white text-xs font-semibold rounded-full shadow-sm flex items-center space-x-1.5 transition-all cursor-pointer"
                      >
                        <Sparkles className="w-3.5 h-3.5 text-[#E2F899]" />
                        <span>Launch Razorpay Checkout Modal</span>
                      </button>

                      {result.redirectUrl && (
                        <a
                          href={result.redirectUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-[#0369A1] underline text-xs font-semibold flex items-center space-x-1"
                        >
                          <span>Open Hosted Page</span>
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      )}

                      <button
                        type="button"
                        onClick={handleSyncStatus}
                        disabled={syncing}
                        className="px-3 py-1.5 bg-white/80 hover:bg-white text-[#191816] border border-[#D4EE7F] text-xs font-medium rounded-full shadow-sm flex items-center space-x-1 cursor-pointer"
                      >
                        {syncing ? <Clock className="w-3 h-3 animate-spin" /> : <span>Sync Status</span>}
                      </button>
                    </div>
                  </>
                )}
              </div>
            )}

            {/* Cashfree Actions */}
            {result.attempts?.[0]?.providerCode?.toLowerCase() === 'cashfree' && (
              <div className="pt-2 border-t border-[#D4EE7F] flex flex-wrap gap-2 items-center justify-between">
                {result.status === 'SUCCEEDED' ? (
                  <div className="flex items-center space-x-2 text-xs font-semibold text-[#2C410F]">
                    <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                    <span>Payment captured on Cashfree Sandbox.</span>
                  </div>
                ) : (
                  <>
                    {result.redirectUrl && (
                      <a
                        href={result.redirectUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="px-3.5 py-1.5 bg-[#191816] hover:bg-black text-white text-xs font-semibold rounded-full shadow-sm flex items-center space-x-1.5 transition-all cursor-pointer"
                      >
                        <Sparkles className="w-3.5 h-3.5 text-[#E2F899]" />
                        <span>Open Cashfree Checkout</span>
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    )}
                    <button
                      type="button"
                      onClick={handleSyncStatus}
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
            {result.attempts?.[0]?.providerCode?.toLowerCase() === 'stripe' && (
              <div className="pt-2 border-t border-[#D4EE7F] flex flex-wrap gap-2 items-center justify-between text-xs">
                <div className="flex items-center space-x-1.5 text-[#2C410F] font-semibold">
                  <CheckCircle2 className="w-4 h-4 text-[#2C410F]" />
                  <span>Stripe Payment Intent {result.status}</span>
                </div>
                {result.attempts?.[0]?.providerReference?.startsWith('pi_') && (
                  <a
                    href={`https://dashboard.stripe.com/test/payments/${result.attempts?.[0]?.providerReference}`}
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
            {result.attempts?.[0]?.providerCode?.toLowerCase() === 'adyen' && (
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
  );
};
