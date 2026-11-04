import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  RotateCcw,
  CheckCircle2,
  XCircle,
  Clock,
  ShieldAlert,
  Copy,
  Check,
  ExternalLink,
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
        <Clock className="w-6 h-6 animate-spin text-emerald-400 mx-auto mb-2" />
        <p className="text-sm text-slate-400">Loading payment lifecycle telemetry...</p>
      </div>
    );
  }

  if (error || !payment) {
    return (
      <div className="p-8 text-center bg-slate-900 border border-slate-800 rounded-xl max-w-lg mx-auto">
        <ShieldAlert className="w-10 h-10 text-rose-400 mx-auto mb-3" />
        <h3 className="text-base font-semibold text-white">Payment Not Found</h3>
        <p className="text-xs text-slate-400 mt-1">{error || 'Could not locate transaction'}</p>
        <button
          onClick={() => navigate('/payments')}
          className="mt-4 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium rounded-lg"
        >
          Return to Payments
        </button>
      </div>
    );
  }

  const canRefund = payment.status === 'SUCCEEDED' || payment.status === 'PARTIALLY_REFUNDED';

  return (
    <div className="space-y-6">
      {/* Back button & Action Header */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => navigate('/payments')}
          className="inline-flex items-center space-x-2 text-xs font-medium text-slate-400 hover:text-slate-100 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Transactions</span>
        </button>

        {canRefund && (
          <button
            onClick={() => setIsRefundOpen(true)}
            className="px-3.5 py-1.5 bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-800/60 rounded-lg text-xs font-medium transition-colors flex items-center space-x-1.5"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Issue Refund</span>
          </button>
        )}
      </div>

      {/* Overview Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-sm">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-slate-800/80 pb-6">
          <div>
            <div className="flex items-center space-x-3">
              <h1 className="text-xl font-bold text-white font-mono">{payment.orderId}</h1>
              <StatusBadge status={payment.status} />
            </div>
            <p className="text-xs text-slate-500 font-mono mt-1">ID: {payment.id}</p>
          </div>

          <div className="text-right">
            <span className="text-xs text-slate-500 uppercase tracking-wider">Captured Amount</span>
            <p className="text-2xl font-black text-white tracking-tight">
              {(payment.amountMinor / 100).toFixed(2)} {payment.currency}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-6 text-xs">
          <div>
            <span className="text-slate-500 block">Payment Method:</span>
            <span className="font-semibold text-slate-200 mt-1 block">{payment.paymentMethod}</span>
          </div>

          <div>
            <span className="text-slate-500 block">Idempotency Key:</span>
            <button
              onClick={copyIdempotency}
              className="font-mono text-slate-300 mt-1 flex items-center space-x-1 hover:text-emerald-400"
            >
              <span className="truncate max-w-[120px]">{payment.idempotencyKey}</span>
              {copiedKey ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
            </button>
          </div>

          <div>
            <span className="text-slate-500 block">Created Timestamp:</span>
            <span className="text-slate-300 mt-1 block">{new Date(payment.createdAt).toLocaleString()}</span>
          </div>

          <div>
            <span className="text-slate-500 block">Updated Timestamp:</span>
            <span className="text-slate-300 mt-1 block">{new Date(payment.updatedAt).toLocaleString()}</span>
          </div>
        </div>
      </div>

      {/* Attempt Execution Timeline */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-sm space-y-4">
        <div>
          <h2 className="text-base font-semibold text-white">Provider Attempt Timeline</h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Sequential orchestration attempts executed against payment gateways
          </p>
        </div>

        <div className="space-y-3">
          {attempts.length === 0 ? (
            <p className="text-xs text-slate-500 py-4 text-center">No attempt records logged.</p>
          ) : (
            attempts.map((att) => (
              <div
                key={att.id}
                className="p-4 rounded-lg bg-slate-950 border border-slate-800 space-y-2 text-xs"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <span className="font-bold text-white uppercase px-2 py-0.5 rounded bg-slate-800 text-[10px]">
                      Attempt #{att.attemptNumber}
                    </span>
                    <span className="font-mono font-semibold text-emerald-400">{att.providerCode}</span>
                  </div>
                  <StatusBadge status={att.status} size="sm" />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-2 pt-2 border-t border-slate-800/80 text-slate-400">
                  <div>
                    <span className="text-slate-500">Provider Ref:</span>
                    <p className="font-mono text-slate-200 mt-0.5">{att.providerReference || 'Pending generation'}</p>
                  </div>
                  <div>
                    <span className="text-slate-500">Submitted:</span>
                    <p className="text-slate-200 mt-0.5">{new Date(att.submittedAt).toLocaleTimeString()}</p>
                  </div>
                  <div>
                    <span className="text-slate-500">Completed:</span>
                    <p className="text-slate-200 mt-0.5">
                      {att.completedAt ? new Date(att.completedAt).toLocaleTimeString() : 'In Progress'}
                    </p>
                  </div>
                </div>

                {att.failureCode && (
                  <div className="p-2.5 rounded bg-rose-950/40 border border-rose-900/50 text-rose-300 text-xs">
                    <span className="font-semibold font-mono text-rose-200">[{att.failureCode}]:</span>{' '}
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
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <h3 className="font-semibold text-white">Issue Refund for {payment.orderId}</h3>

            {refundError && (
              <div className="p-3 bg-rose-950/50 border border-rose-800 rounded text-rose-300 text-xs">
                {refundError}
              </div>
            )}

            <form onSubmit={handleRefundSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">
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
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Refund Reason</label>
                <input
                  type="text"
                  required
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="flex items-center justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsRefundOpen(false)}
                  className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={refundLoading}
                  className="px-4 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 disabled:opacity-50 text-white text-xs font-medium"
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

