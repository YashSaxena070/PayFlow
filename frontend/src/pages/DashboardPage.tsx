import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  TrendingUp,
  CreditCard,
  CheckCircle2,
  Percent,
  ArrowRight,
  RefreshCw,
  AlertCircle,
  ExternalLink,
} from 'lucide-react';
import { apiRequest } from '../services/api';
import { AnalyticsOverview, Payment } from '../types';
import { StatusBadge } from '../components/StatusBadge';

interface Props {
  onOpenSimulator: () => void;
}

export const DashboardPage: React.FC<Props> = ({ onOpenSimulator }) => {
  const [analytics, setAnalytics] = useState<AnalyticsOverview | null>(null);
  const [recentPayments, setRecentPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const navigate = useNavigate();

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [overviewData, paymentsData] = await Promise.all([
        apiRequest<AnalyticsOverview>('/analytics/overview'),
        apiRequest<{ content: Payment[] }>('/payments?page=0&size=5'),
      ]);
      setAnalytics(overviewData);
      setRecentPayments(paymentsData?.content || []);
    } catch (err: any) {
      setError(err.message || 'Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  return (
    <div className="space-y-8 animate-in fade-in duration-300">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Payment Orchestration Overview</h1>
          <p className="text-sm text-slate-400 mt-1">
            Real-time multi-provider routing analytics, transaction telemetry & KPIs
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={loadData}
            className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition-colors"
            title="Refresh Metrics"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-emerald-400' : ''}`} />
          </button>
          <button
            onClick={onOpenSimulator}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-sm rounded-lg shadow-lg shadow-emerald-600/20 transition-all flex items-center space-x-2"
          >
            <span>⚡ New Test Payment</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-800 text-rose-300 text-sm flex items-start space-x-3">
          <AlertCircle className="w-5 h-5 shrink-0 text-rose-400 mt-0.5" />
          <div>
            <p className="font-semibold text-rose-200">Failed to fetch platform metrics</p>
            <p className="text-xs mt-0.5">{error}</p>
          </div>
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-medium uppercase tracking-wider">Total Volume</span>
            <TrendingUp className="w-4 h-4 text-emerald-400" />
          </div>
          <p className="text-2xl font-bold text-white tracking-tight">
            ${((analytics?.totalVolumeMinor || 0) / 100).toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
          <p className="text-xs text-slate-500 mt-1">Aggregated across active routes</p>
        </div>

        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-medium uppercase tracking-wider">Total Transactions</span>
            <CreditCard className="w-4 h-4 text-cyan-400" />
          </div>
          <p className="text-2xl font-bold text-white tracking-tight">
            {(analytics?.totalTransactions || 0).toLocaleString()}
          </p>
          <p className="text-xs text-slate-500 mt-1">Lifecycle payment intents created</p>
        </div>

        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-medium uppercase tracking-wider">Successful Captured</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <p className="text-2xl font-bold text-white tracking-tight">
            {(analytics?.successfulTransactions || 0).toLocaleString()}
          </p>
          <p className="text-xs text-emerald-500/80 mt-1">Fully authorized & settled</p>
        </div>

        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-medium uppercase tracking-wider">Success Rate</span>
            <Percent className="w-4 h-4 text-indigo-400" />
          </div>
          <p className="text-2xl font-bold text-white tracking-tight">
            {(analytics?.successRatePercent || 0).toFixed(1)}%
          </p>
          <p className="text-xs text-slate-500 mt-1">All gateway attempts conversion</p>
        </div>
      </div>

      {/* Provider Health Breakdown */}
      <div className="p-6 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
        <h2 className="text-base font-semibold text-white">Provider Performance Telemetry</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {analytics?.providerMetrics?.map((provider) => (
            <div
              key={provider.providerCode}
              className="p-4 rounded-lg bg-slate-950 border border-slate-800/80 space-y-3"
            >
              <div className="flex items-center justify-between">
                <span className="font-bold text-white text-sm">{provider.providerCode} Simulator</span>
                <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-950 text-emerald-400 border border-emerald-800">
                  {provider.successRatePercent.toFixed(1)}% Success
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <div>
                  <span className="text-slate-500">Attempts</span>
                  <p className="font-semibold text-slate-200 mt-0.5">{provider.totalAttempts}</p>
                </div>
                <div>
                  <span className="text-slate-500">Succeeded</span>
                  <p className="font-semibold text-emerald-400 mt-0.5">{provider.successfulAttempts}</p>
                </div>
                <div>
                  <span className="text-slate-500">Failed</span>
                  <p className="font-semibold text-rose-400 mt-0.5">{provider.failedAttempts}</p>
                </div>
              </div>
            </div>
          )) || (
            <p className="text-xs text-slate-500 col-span-2">No provider metrics recorded yet. Trigger a payment using the simulator.</p>
          )}
        </div>
      </div>

      {/* Recent Payments Table */}
      <div className="p-6 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-white">Recent Transactions</h2>
          <button
            onClick={() => navigate('/payments')}
            className="text-xs font-medium text-emerald-400 hover:text-emerald-300 flex items-center space-x-1"
          >
            <span>View all payments</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-950 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="py-3 px-4">Order ID</th>
                <th className="py-3 px-4">Amount</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Route</th>
                <th className="py-3 px-4">Created</th>
                <th className="py-3 px-4 text-right">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {recentPayments.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500 text-xs">
                    No transactions found. Click "New Test Payment" above to execute a simulated payment.
                  </td>
                </tr>
              ) : (
                recentPayments.map((p) => (
                  <tr
                    key={p.id}
                    onClick={() => navigate(`/payments/${p.id}`)}
                    className="hover:bg-slate-800/40 cursor-pointer transition-colors"
                  >
                    <td className="py-3 px-4 font-mono text-xs text-slate-200">{p.orderId}</td>
                    <td className="py-3 px-4 font-semibold text-white">
                      {(p.amountMinor / 100).toFixed(2)} {p.currency}
                    </td>
                    <td className="py-3 px-4">
                      <StatusBadge status={p.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-xs font-mono text-slate-400">
                      {p.attempts?.[0]?.providerCode || 'STRIPE'}
                    </td>
                    <td className="py-3 px-4 text-xs text-slate-400">
                      {new Date(p.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <ExternalLink className="w-3.5 h-3.5 inline text-slate-500 hover:text-emerald-400" />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

