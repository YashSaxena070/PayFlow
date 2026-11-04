import React, { useState } from 'react';
import { X, Play, CheckCircle2, AlertTriangle, Clock, ExternalLink } from 'lucide-react';
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
  const [amountMajor, setAmountMajor] = useState('125.00');
  const [currency, setCurrency] = useState('USD');
  const [paymentMethod, setPaymentMethod] = useState('CARD');
  const [scenario, setScenario] = useState('SUCCESS');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

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
          returnUrl: 'https://merchant.example.com/checkout/return',
          testScenario: scenario,
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

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-xl w-full shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <span className="p-1.5 rounded-md bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              ⚡
            </span>
            <h3 className="font-semibold text-white">Payment Orchestration Simulator</h3>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-white p-1 rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSimulate} className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Order Reference ID</label>
              <input
                type="text"
                required
                value={orderId}
                onChange={(e) => setOrderId(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Amount</label>
              <input
                type="number"
                step="0.01"
                min="0.50"
                required
                value={amountMajor}
                onChange={(e) => setAmountMajor(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Currency</label>
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
              >
                <option value="USD">USD ($)</option>
                <option value="EUR">EUR (€)</option>
                <option value="INR">INR (₹)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Method</label>
              <select
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
              >
                <option value="CARD">Credit/Debit Card</option>
                <option value="UPI">UPI (Adyen only)</option>
                <option value="NETBANKING">Netbanking</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Test Scenario</label>
              <select
                value={scenario}
                onChange={(e) => setScenario(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
              >
                <option value="SUCCESS">Success (200)</option>
                <option value="DECLINE">Card Decline</option>
                <option value="TIMEOUT">Ambiguous Timeout</option>
                <option value="REQUIRES_ACTION">3DS Requires Action</option>
                <option value="5XX">Provider Error (5xx)</option>
              </select>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white font-medium rounded-lg shadow-lg shadow-emerald-600/20 transition-all flex items-center justify-center space-x-2 text-sm"
          >
            {loading ? (
              <Clock className="w-4 h-4 animate-spin" />
            ) : (
              <>
                <Play className="w-4 h-4 fill-current" />
                <span>Execute Payment Orchestration</span>
              </>
            )}
          </button>
        </form>

        {error && (
          <div className="mx-6 mb-6 p-4 rounded-lg bg-rose-950/50 border border-rose-800 text-rose-300 text-xs flex items-start space-x-3">
            <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400 mt-0.5" />
            <div>
              <p className="font-semibold text-rose-200">Execution Error</p>
              <p className="mt-0.5">{error}</p>
            </div>
          </div>
        )}

        {result && (
          <div className="mx-6 mb-6 p-4 rounded-lg bg-slate-950 border border-slate-800 space-y-3">
            <div className="flex items-center justify-between border-b border-slate-800/80 pb-2">
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                <span className="text-xs font-semibold text-slate-200">Payment Processed</span>
              </div>
              <StatusBadge status={result.status} size="sm" />
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <div>
                <span className="text-slate-500">Payment ID:</span>
                <p className="font-mono text-slate-300 truncate">{result.id}</p>
              </div>
              <div>
                <span className="text-slate-500">Amount / Currency:</span>
                <p className="text-slate-200 font-semibold">
                  {(result.amountMinor / 100).toFixed(2)} {result.currency}
                </p>
              </div>
              <div>
                <span className="text-slate-500">Provider Route:</span>
                <p className="text-slate-300">
                  {result.attempts?.[0]?.providerCode || 'Routed Provider'}
                </p>
              </div>
              <div>
                <span className="text-slate-500">Attempt Status:</span>
                <p className="text-slate-300">
                  {result.attempts?.[0]?.status || 'N/A'}
                </p>
              </div>
            </div>

            {result.redirectUrl && (
              <div className="pt-2 border-t border-slate-800 flex items-center justify-between text-xs">
                <span className="text-cyan-400">3DS Action URL Required:</span>
                <a
                  href={result.redirectUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="text-cyan-400 underline hover:text-cyan-300 flex items-center space-x-1"
                >
                  <span>Authenticate</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

