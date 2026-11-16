import React, { useEffect, useState } from 'react';
import { RefreshCw, ExternalLink } from 'lucide-react';
import { apiRequest } from '../services/api';
import { RefundDto } from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { useNavigate } from 'react-router-dom';

export const RefundsPage: React.FC = () => {
  const [refunds, setRefunds] = useState<RefundDto[]>([]);
  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  const fetchRefunds = async () => {
    setLoading(true);
    try {
      const res = await apiRequest<{ content: RefundDto[] }>('/refunds?page=0&size=50');
      setRefunds(res?.content || []);
    } catch (err) {
      console.error('Failed to load refunds', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRefunds();
  }, []);

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
            SETTLEMENTS
          </span>
          <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Refunds Management</h1>
          <p className="text-sm text-[#78716C] mt-1">
            Track full and partial customer refunds across integrated providers
          </p>
        </div>

        <button
          onClick={fetchRefunds}
          className="p-2.5 rounded-full bg-white hover:bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] transition-colors shadow-sm"
          title="Refresh"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#FF5A36]' : ''}`} />
        </button>
      </div>

      <div className="bg-white border border-[#EBE6DD] rounded-2xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-[#191816]">
            <thead className="bg-[#FAF7F2] text-[#78716C] text-xs uppercase tracking-wider border-b border-[#EBE6DD] font-medium">
              <tr>
                <th className="py-3.5 px-4">Refund ID</th>
                <th className="py-3.5 px-4">Amount</th>
                <th className="py-3.5 px-4">Status</th>
                <th className="py-3.5 px-4">Reason</th>
                <th className="py-3.5 px-4">Provider Reference</th>
                <th className="py-3.5 px-4">Timestamp</th>
                <th className="py-3.5 px-4 text-right">Payment</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EBE6DD]">
              {refunds.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-[#78716C] text-xs">
                    No refunds issued yet. Refunds can be initiated from any successful payment details page.
                  </td>
                </tr>
              ) : (
                refunds.map((r) => (
                  <tr key={r.id} className="hover:bg-[#FAF7F2]/80 transition-colors">
                    <td className="py-3.5 px-4 font-mono text-xs text-[#191816] font-medium">{r.id.substring(0, 13)}...</td>
                    <td className="py-3.5 px-4 font-medium text-[#991B1B]">
                      -{(r.amountMinor / 100).toFixed(2)} {r.currency}
                    </td>
                    <td className="py-3.5 px-4">
                      <StatusBadge status={r.status} size="sm" />
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#57534E]">{r.reason || 'N/A'}</td>
                    <td className="py-3.5 px-4 font-mono text-xs text-[#78716C]">
                      {r.providerRefundReference || 'Pending'}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#78716C]">
                      {new Date(r.createdAt).toLocaleString()}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => navigate(`/payments/${r.paymentId}`)}
                        className="text-xs text-[#FF5A36] hover:text-[#E84B29] font-semibold inline-flex items-center space-x-1"
                      >
                        <span>View</span>
                        <ExternalLink className="w-3 h-3" />
                      </button>
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
