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
  Sparkles,
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
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
            CONTROL CENTER
          </span>
          <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Payment Orchestration Overview</h1>
          <p className="text-sm text-[#78716C] mt-1">
            Real-time multi-provider routing analytics, transaction telemetry & KPIs
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={loadData}
            className="p-2.5 rounded-full bg-white hover:bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] transition-colors shadow-sm"
            title="Refresh Metrics"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#FF5A36]' : ''}`} />
          </button>
          <button
            onClick={onOpenSimulator}
            className="px-5 py-2.5 bg-[#FF5A36] hover:bg-[#E84B29] text-white font-medium text-sm rounded-full shadow-md shadow-[#FF5A36]/20 transition-all flex items-center space-x-2"
          >
            <Sparkles className="w-4 h-4" />
            <span>Simulate Payment</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-[#FED7CA]/60 border border-[#FCA5A5] text-[#991B1B] text-sm flex items-start space-x-3">
          <AlertCircle className="w-5 h-5 shrink-0 text-[#991B1B] mt-0.5" />
          <div>
            <p className="font-semibold">Failed to fetch platform metrics</p>
            <p className="text-xs mt-0.5">{error}</p>
          </div>
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Volume */}
        <div className="p-6 rounded-2xl bg-white border border-[#EBE6DD] shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-[#78716C] mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider">Total Volume</span>
            <div className="w-8 h-8 rounded-full bg-[#E2F899] flex items-center justify-center text-[#2C410F]">
              <TrendingUp className="w-4 h-4" />
            </div>
          </div>
          <p className="text-3xl font-medium text-[#191816] tracking-tight">
            ${((analytics?.totalVolumeMinor || 0) / 100).toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
          <p className="text-xs text-[#78716C] mt-1.5 font-normal">Aggregated across active routes</p>
        </div>

        {/* Total Transactions */}
        <div className="p-6 rounded-2xl bg-white border border-[#EBE6DD] shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-[#78716C] mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider">Total Transactions</span>
            <div className="w-8 h-8 rounded-full bg-[#E0F2FE] flex items-center justify-center text-[#0369A1]">
              <CreditCard className="w-4 h-4" />
            </div>
          </div>
          <p className="text-3xl font-medium text-[#191816] tracking-tight">
            {(analytics?.totalTransactions || 0).toLocaleString()}
          </p>
          <p className="text-xs text-[#78716C] mt-1.5 font-normal">Lifecycle payment intents created</p>
        </div>

        {/* Successfully Captured */}
        <div className="p-6 rounded-2xl bg-white border border-[#EBE6DD] shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-[#78716C] mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider">Successful Captured</span>
            <div className="w-8 h-8 rounded-full bg-[#E2F899] flex items-center justify-center text-[#2C410F]">
              <CheckCircle2 className="w-4 h-4" />
            </div>
          </div>
          <p className="text-3xl font-medium text-[#191816] tracking-tight">
            {(analytics?.successfulTransactions || 0).toLocaleString()}
          </p>
          <p className="text-xs text-[#2C410F] mt-1.5 font-normal">Fully authorized & settled</p>
        </div>

        {/* Success Rate */}
        <div className="p-6 rounded-2xl bg-white border border-[#EBE6DD] shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-[#78716C] mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider">Success Rate</span>
            <div className="w-8 h-8 rounded-full bg-[#FED7CA] flex items-center justify-center text-[#991B1B]">
              <Percent className="w-4 h-4" />
            </div>
          </div>
          <p className="text-3xl font-medium text-[#191816] tracking-tight">
            {(analytics?.successRatePercent || 0).toFixed(1)}%
          </p>
          <p className="text-xs text-[#78716C] mt-1.5 font-normal">All gateway attempts conversion</p>
        </div>
      </div>

      {/* Provider Health Breakdown */}
      <div className="p-6 rounded-2xl bg-white border border-[#EBE6DD] shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-[#191816]">Provider Performance Telemetry</h2>
          <span className="text-xs text-[#78716C] font-normal">5 Connected Gateways</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {analytics?.providerMetrics?.map((provider) => (
            <div
              key={provider.providerCode}
              className="p-4 rounded-xl bg-[#FAF7F2] border border-[#EBE6DD] space-y-3"
            >
              <div className="flex items-center justify-between">
                <span className="font-semibold text-[#191816] text-sm uppercase">{provider.providerCode}</span>
                <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-[#E2F899] text-[#2C410F] border border-[#D4EE7F]">
                  {provider.successRatePercent.toFixed(1)}% Success
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2 text-xs pt-1">
                <div>
                  <span className="text-[#78716C] font-normal">Attempts</span>
                  <p className="font-medium text-[#191816] mt-0.5">{provider.totalAttempts}</p>
                </div>
                <div>
                  <span className="text-[#78716C] font-normal">Succeeded</span>
                  <p className="font-medium text-[#2C410F] mt-0.5">{provider.successfulAttempts}</p>
                </div>
                <div>
                  <span className="text-[#78716C] font-normal">Failed</span>
                  <p className="font-medium text-[#991B1B] mt-0.5">{provider.failedAttempts}</p>
                </div>
              </div>
            </div>
          )) || (
            <p className="text-xs text-[#78716C] col-span-2">No provider metrics recorded yet. Trigger a payment using the simulator.</p>
          )}
        </div>
      </div>

      {/* Recent Payments Table */}
      <div className="p-6 rounded-2xl bg-white border border-[#EBE6DD] shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-[#191816]">Recent Transactions</h2>
          <button
            onClick={() => navigate('/payments')}
            className="text-xs font-semibold text-[#FF5A36] hover:text-[#E84B29] flex items-center space-x-1"
          >
            <span>View all payments</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="overflow-x-auto rounded-xl border border-[#EBE6DD]">
          <table className="w-full text-left text-sm text-[#191816]">
            <thead className="bg-[#FAF7F2] text-[#78716C] text-xs uppercase tracking-wider border-b border-[#EBE6DD] font-medium">
              <tr>
                <th className="py-3 px-4">Order ID</th>
                <th className="py-3 px-4">Amount</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Route</th>
                <th className="py-3 px-4">Created</th>
                <th className="py-3 px-4 text-right">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EBE6DD]">
              {recentPayments.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#78716C] text-xs">
                    No transactions found. Click "Simulate Payment" above to execute a simulated payment.
                  </td>
                </tr>
              ) : (
                recentPayments.map((p) => (
                  <tr
                    key={p.id}
                    onClick={() => navigate(`/payments/${p.id}`)}
                    className="hover:bg-[#FAF7F2]/80 cursor-pointer transition-colors"
                  >
                    <td className="py-3 px-4 font-mono text-xs text-[#191816] font-medium">{p.orderId}</td>
                    <td className="py-3 px-4 font-medium text-[#191816]">
                      {(p.amountMinor / 100).toFixed(2)} {p.currency}
                    </td>
                    <td className="py-3 px-4">
                      <StatusBadge status={p.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-xs font-mono text-[#57534E] uppercase">
                      {p.attempts?.[0]?.providerCode || 'STRIPE'}
                    </td>
                    <td className="py-3 px-4 text-xs text-[#78716C]">
                      {new Date(p.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <ExternalLink className="w-3.5 h-3.5 inline text-[#78716C] hover:text-[#FF5A36]" />
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
