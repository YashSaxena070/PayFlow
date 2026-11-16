import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  RotateCcw,
  Clock,
  ShieldAlert,
  Copy,
  Check,
} from 'lucide-react';
import { apiRequest } from '../services/api';
import { Payment, PaymentAttempt } from '../types';
import { StatusBadge } from '../components/StatusBadge';

export const PaymentDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [attempts, setAttempts] = useState<PaymentAttempt[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Refund modal state
  const [isRefundOpen, setIsRefundOpen] = useState(false);
  const [refundAmount, setRefundAmount] = useState('');
  const [refundReason, setRefundReason] = useState('Customer requested refund');
  const [refundLoading, setRefundLoading] = useState(false);
  const [refundError, setRefundError] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState(false);

  const navigate = useNavigate();

  const fetchPaymentDetails = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [paymentData, attemptsData] = await Promise.all([
        apiRequest<Payment>(`/payments/${id}`),
        apiRequest<PaymentAttempt[]>(`/payments/${id}/attempts`),
      ]);
      setPayment(paymentData);
      setAttempts(attemptsData || []);
      setRefundAmount(((paymentData.amountMinor || 0) / 100).toFixed(2));
    } catch (err: any) {
      setError(err.message || 'Failed to load transaction details');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPaymentDetails();
  }, [id]);

  const handleRefundSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!payment) return;
    setRefundLoading(true);
    setRefundError(null);

    try {
      const minor = Math.round(parseFloat(refundAmount) * 100);
      const idempotencyKey = crypto.randomUUID();

      await apiRequest(`/payments/${payment.id}/refunds`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          amountMinor: minor,
          reason: refundReason,
        }),
      });

      setIsRefundOpen(false);
      fetchPaymentDetails();
    } catch (err: any) {
      setRefundError(err.message || 'Refund processing failed');
    } finally {
      setRefundLoading(false);
    }
  };

  const copyIdempotency = () => {
    if (payment?.idempotencyKey) {
      navigator.clipboard.writeText(payment.idempotencyKey);
      setCopiedKey(true);
      setTimeout(() => setCopiedKey(false), 2000);
    }
  };

  if (loading) {
    return (
      <div className="py-24 text-center">
        <Clock className="w-6 h-6 animate-spin text-[#FF5A36] mx-auto mb-2" />
        <p className="text-sm text-[#78716C]">Loading payment lifecycle telemetry...</p>
      </div>
    );
  }

  if (error || !payment) {
    return (
      <div className="p-8 text-center bg-white border border-[#EBE6DD] rounded-2xl max-w-lg mx-auto shadow-sm">
        <ShieldAlert className="w-10 h-10 text-[#991B1B] mx-auto mb-3" />
        <h3 className="text-base font-semibold text-[#191816]">Payment Not Found</h3>
        <p className="text-xs text-[#78716C] mt-1">{error || 'Could not locate transaction'}</p>
        <button
          onClick={() => navigate('/payments')}
          className="mt-4 px-5 py-2.5 bg-[#111827] hover:bg-black text-white text-xs font-medium rounded-full shadow-sm"
        >
          Return to Payments
        </button>
      </div>
    );
  }

  const canRefund = payment.status === 'SUCCEEDED' || payment.status === 'PARTIALLY_REFUNDED';

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      {/* Back button & Action Header */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => navigate('/payments')}
          className="inline-flex items-center space-x-2 text-xs font-semibold text-[#78716C] hover:text-[#191816] transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Transactions</span>
        </button>

        {canRefund && (
          <button
            onClick={() => setIsRefundOpen(true)}
            className="px-4 py-2 bg-[#FED7CA] hover:bg-[#FBC5B4] text-[#991B1B] border border-[#FCA5A5] rounded-full text-xs font-semibold transition-colors flex items-center space-x-1.5 shadow-sm"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Issue Refund</span>
          </button>
        )}
      </div>

      {/* Overview Card */}
      <div className="bg-white border border-[#EBE6DD] rounded-2xl p-6 shadow-sm">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-[#EBE6DD] pb-6">
          <div>
            <div className="flex items-center space-x-3">
              <h1 className="text-2xl font-mono font-medium text-[#191816]">{payment.orderId}</h1>
              <StatusBadge status={payment.status} />
            </div>
            <p className="text-xs text-[#78716C] font-mono mt-1">ID: {payment.id}</p>
          </div>

          <div className="text-right">
            <span className="text-xs text-[#78716C] uppercase tracking-wider font-semibold">Captured Amount</span>
            <p className="text-3xl font-medium text-[#191816] tracking-tight">
              {(payment.amountMinor / 100).toFixed(2)} {payment.currency}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-6 text-xs">
          <div>
            <span className="text-[#78716C] block font-medium">Payment Method:</span>
            <span className="font-semibold text-[#191816] mt-1 block uppercase">{payment.paymentMethod}</span>
          </div>

          <div>
            <span className="text-[#78716C] block font-medium">Idempotency Key:</span>
            <button
              onClick={copyIdempotency}
              className="font-mono text-[#191816] mt-1 flex items-center space-x-1 hover:text-[#FF5A36]"
            >
              <span className="truncate max-w-[120px]">{payment.idempotencyKey}</span>
              {copiedKey ? <Check className="w-3 h-3 text-[#2C410F]" /> : <Copy className="w-3 h-3 text-[#78716C]" />}
            </button>
          </div>

          <div>
            <span className="text-[#78716C] block font-medium">Created Timestamp:</span>
            <span className="text-[#191816] mt-1 block">{new Date(payment.createdAt).toLocaleString()}</span>
          </div>

          <div>
            <span className="text-[#78716C] block font-medium">Updated Timestamp:</span>
            <span className="text-[#191816] mt-1 block">{new Date(payment.updatedAt).toLocaleString()}</span>
          </div>
        </div>
      </div>

      {/* Attempt Execution Timeline */}
      <div className="bg-white border border-[#EBE6DD] rounded-2xl p-6 shadow-sm space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-[#191816]">Provider Attempt Timeline</h2>
          <p className="text-xs text-[#78716C] mt-0.5">
            Sequential orchestration attempts executed against payment gateways
          </p>
        </div>

        <div className="space-y-3">
          {attempts.length === 0 ? (
            <p className="text-xs text-[#78716C] py-4 text-center">No attempt records logged.</p>
          ) : (
            attempts.map((att) => (
              <div
                key={att.id}
                className="p-4 rounded-xl bg-[#FAF7F2] border border-[#EBE6DD] space-y-2 text-xs"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <span className="font-semibold text-[#191816] uppercase px-2.5 py-0.5 rounded-full bg-white border border-[#EBE6DD] text-[10px]">
                      Attempt #{att.attemptNumber}
                    </span>
                    <span className="font-mono font-semibold text-[#191816] uppercase">{att.providerCode}</span>
                  </div>
                  <StatusBadge status={att.status} size="sm" />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-2 pt-2 border-t border-[#EBE6DD] text-[#78716C]">
                  <div>
                    <span className="text-[#78716C]">Provider Ref:</span>
                    <p className="font-mono text-[#191816] mt-0.5 font-medium">{att.providerReference || 'Pending generation'}</p>
                  </div>
                  <div>
                    <span className="text-[#78716C]">Submitted:</span>
                    <p className="text-[#191816] mt-0.5 font-medium">{new Date(att.submittedAt).toLocaleTimeString()}</p>
                  </div>
                  <div>
                    <span className="text-[#78716C]">Completed:</span>
                    <p className="text-[#191816] mt-0.5 font-medium">
                      {att.completedAt ? new Date(att.completedAt).toLocaleTimeString() : 'In Progress'}
                    </p>
                  </div>
                </div>

                {att.failureCode && (
                  <div className="p-3 rounded-xl bg-[#FED7CA]/60 border border-[#FCA5A5] text-[#991B1B] text-xs">
                    <span className="font-semibold font-mono">[{att.failureCode}]:</span>{' '}
                    {att.failureMessageSafe}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>

      {/* Refund Modal */}
      {isRefundOpen && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white border border-[#EBE6DD] rounded-3xl max-w-md w-full p-6 space-y-4 shadow-2xl animate-in zoom-in-95 duration-150">
            <h3 className="font-semibold text-lg text-[#191816]">Issue Refund for {payment.orderId}</h3>

            {refundError && (
              <div className="p-3 bg-[#FED7CA]/70 border border-[#FCA5A5] rounded-xl text-[#991B1B] text-xs">
                {refundError}
              </div>
            )}

            <form onSubmit={handleRefundSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">
                  Refund Amount ({payment.currency})
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  max={(payment.amountMinor / 100).toFixed(2)}
                  required
                  value={refundAmount}
                  onChange={(e) => setRefundAmount(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Refund Reason</label>
                <input
                  type="text"
                  required
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                />
              </div>

              <div className="flex items-center justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsRefundOpen(false)}
                  className="px-4 py-2.5 rounded-full bg-[#EFEAE2] hover:bg-[#E5DFD4] text-[#191816] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={refundLoading}
                  className="px-5 py-2.5 rounded-full bg-[#FF5A36] hover:bg-[#E84B29] disabled:opacity-50 text-white text-xs font-semibold shadow-md shadow-[#FF5A36]/20"
                >
                  {refundLoading ? 'Processing...' : 'Confirm Refund'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
