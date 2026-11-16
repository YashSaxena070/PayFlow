import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Filter, ChevronLeft, ChevronRight, RefreshCw, ExternalLink } from 'lucide-react';
import { apiRequest } from '../services/api';
import { Payment } from '../types';
import { StatusBadge } from '../components/StatusBadge';

export const PaymentsPage: React.FC = () => {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [orderSearch, setOrderSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [currencyFilter, setCurrencyFilter] = useState<string>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const navigate = useNavigate();

  const fetchPayments = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '15');
      if (orderSearch.trim()) params.append('orderId', orderSearch.trim());
      if (statusFilter) params.append('status', statusFilter);
      if (currencyFilter) params.append('currency', currencyFilter);

      const res = await apiRequest<{ content: Payment[]; totalPages: number; totalElements: number }>(
        `/payments?${params.toString()}`
      );
      setPayments(res?.content || []);
      setTotalPages(res?.totalPages || 1);
      setTotalElements(res?.totalElements || 0);
    } catch (err) {
      console.error('Failed to load payments', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPayments();
  }, [page, statusFilter, currencyFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchPayments();
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
            TRANSACTIONS
          </span>
          <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Payments & Attempts</h1>
          <p className="text-sm text-[#78716C] mt-1">
            Total {totalElements} payment attempts recorded across all gateways
          </p>
        </div>

        <button
          onClick={fetchPayments}
          className="p-2.5 rounded-full bg-white hover:bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] transition-colors shadow-sm"
          title="Refresh"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#FF5A36]' : ''}`} />
        </button>
      </div>

      {/* Filter Bar */}
      <div className="p-4 bg-white border border-[#EBE6DD] rounded-2xl shadow-sm flex flex-col md:flex-row gap-3 items-center justify-between">
        <form onSubmit={handleSearch} className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-[#78716C] absolute left-3.5 top-3" />
          <input
            type="text"
            placeholder="Search Order ID..."
            value={orderSearch}
            onChange={(e) => setOrderSearch(e.target.value)}
            className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl pl-9 pr-4 py-2.5 text-xs text-[#191816] placeholder-[#A8A29E] focus:outline-none focus:border-[#FF5A36] font-medium"
          />
        </form>

        <div className="flex items-center space-x-3 w-full md:w-auto">
          <div className="flex items-center space-x-1.5 text-xs text-[#78716C]">
            <Filter className="w-3.5 h-3.5" />
            <span>Filter:</span>
          </div>

          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage(0);
            }}
            className="bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-xs text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
          >
            <option value="">All Statuses</option>
            <option value="SUCCEEDED">Succeeded</option>
            <option value="FAILED">Failed</option>
            <option value="PENDING">Pending</option>
            <option value="REQUIRES_ACTION">Requires Action</option>
            <option value="PARTIALLY_REFUNDED">Partially Refunded</option>
            <option value="REFUNDED">Refunded</option>
          </select>

          <select
            value={currencyFilter}
            onChange={(e) => {
              setCurrencyFilter(e.target.value);
              setPage(0);
            }}
            className="bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-xs text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
          >
            <option value="">All Currencies</option>
            <option value="USD">USD ($)</option>
            <option value="EUR">EUR (€)</option>
            <option value="INR">INR (₹)</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white border border-[#EBE6DD] rounded-2xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-[#191816]">
            <thead className="bg-[#FAF7F2] text-[#78716C] text-xs uppercase tracking-wider border-b border-[#EBE6DD] font-medium">
              <tr>
                <th className="py-3.5 px-4">Order ID</th>
                <th className="py-3.5 px-4">Amount</th>
                <th className="py-3.5 px-4">Status</th>
                <th className="py-3.5 px-4">Method</th>
                <th className="py-3.5 px-4">Route</th>
                <th className="py-3.5 px-4">Timestamp</th>
                <th className="py-3.5 px-4 text-right">View</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EBE6DD]">
              {payments.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-[#78716C] text-xs">
                    No transactions match current filters.
                  </td>
                </tr>
              ) : (
                payments.map((p) => (
                  <tr
                    key={p.id}
                    onClick={() => navigate(`/payments/${p.id}`)}
                    className="hover:bg-[#FAF7F2]/80 cursor-pointer transition-colors"
                  >
                    <td className="py-3.5 px-4 font-mono text-xs text-[#191816] font-medium">
                      {p.orderId}
                    </td>
                    <td className="py-3.5 px-4 font-medium text-[#191816]">
                      {(p.amountMinor / 100).toFixed(2)} {p.currency}
                    </td>
                    <td className="py-3.5 px-4">
                      <StatusBadge status={p.status} size="sm" />
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#57534E]">
                      {p.paymentMethod}
                    </td>
                    <td className="py-3.5 px-4 text-xs font-mono text-[#57534E] uppercase">
                      {p.attempts?.[0]?.providerCode || 'STRIPE'}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#78716C]">
                      {new Date(p.createdAt).toLocaleString()}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <ExternalLink className="w-4 h-4 inline text-[#78716C] hover:text-[#FF5A36]" />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        <div className="px-4 py-3 bg-[#FAF7F2] border-t border-[#EBE6DD] flex items-center justify-between text-xs text-[#78716C]">
          <span>
            Page {page + 1} of {Math.max(1, totalPages)}
          </span>
          <div className="flex items-center space-x-2">
            <button
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="p-1.5 rounded-lg bg-white border border-[#EBE6DD] disabled:opacity-30 hover:bg-[#FAF7F2] text-[#191816]"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
              className="p-1.5 rounded-lg bg-white border border-[#EBE6DD] disabled:opacity-30 hover:bg-[#FAF7F2] text-[#191816]"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
