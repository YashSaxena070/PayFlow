import React, { useEffect, useState } from 'react';
import { Scale, RefreshCw, Check, X } from 'lucide-react';
import { apiRequest } from '../services/api';
import { ReconciliationCaseDto } from '../types';
import { StatusBadge } from '../components/StatusBadge';

export const ReconciliationPage: React.FC = () => {
  const [cases, setCases] = useState<ReconciliationCaseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [scanning, setScanning] = useState(false);
  const [activeCase, setActiveCase] = useState<ReconciliationCaseDto | null>(null);
  const [resolution, setResolution] = useState('MARK_FAILED');
  const [reason, setReason] = useState('Manual verification with payment processor');
  const [submitting, setSubmitting] = useState(false);
  const [statusMsg, setStatusMsg] = useState<string | null>(null);

  const fetchCases = async () => {
    setLoading(true);
    try {
      const res = await apiRequest<{ content: ReconciliationCaseDto[] }>('/reconciliation-cases?page=0&size=50');
      setCases(res?.content || []);
    } catch (err) {
      console.error('Failed to load reconciliation cases', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCases();
  }, []);

  const handleScan = async () => {
    setScanning(true);
    setStatusMsg(null);
    try {
      const newCases = await apiRequest<ReconciliationCaseDto[]>('/reconciliation-cases/scan?staleSeconds=180', {
        method: 'POST',
      });
      setStatusMsg(`Scan complete. Found ${newCases?.length || 0} stale attempt(s).`);
      setTimeout(() => setStatusMsg(null), 4000);
      fetchCases();
    } catch (err: any) {
      setStatusMsg(`Scan failed: ${err.message}`);
    } finally {
      setScanning(false);
    }
  };

  const handleResolve = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeCase) return;
    setSubmitting(true);

    try {
      await apiRequest(`/reconciliation-cases/${activeCase.id}/resolve`, {
        method: 'POST',
        body: JSON.stringify({ resolution, reason }),
      });

      setActiveCase(null);
      fetchCases();
    } catch (err: any) {
      alert(`Resolution failed: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
            RECONCILIATION
          </span>
          <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Discrepancy & Ambiguity Resolution</h1>
          <p className="text-sm text-[#78716C] mt-1">
            Reconcile ambiguous timeouts, unconfirmed provider states, and avoid duplicate charges
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={fetchCases}
            className="p-2.5 rounded-full bg-white hover:bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] transition-colors shadow-sm"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#FF5A36]' : ''}`} />
          </button>
          <button
            onClick={handleScan}
            disabled={scanning}
            className="px-5 py-2.5 bg-[#111827] hover:bg-black disabled:opacity-50 text-white font-medium text-xs sm:text-sm rounded-full shadow-sm transition-all flex items-center space-x-2"
          >
            <Scale className={`w-4 h-4 ${scanning ? 'animate-spin text-[#FF5A36]' : ''}`} />
            <span>{scanning ? 'Scanning Stale Attempts...' : 'Scan Stale Transactions'}</span>
          </button>
        </div>
      </div>

      {statusMsg && (
        <div className="p-3.5 bg-white border border-[#EBE6DD] rounded-2xl text-[#191816] text-xs flex items-center space-x-2 shadow-sm">
          <Check className="w-4 h-4 text-[#2C410F]" />
          <span className="font-medium">{statusMsg}</span>
        </div>
      )}

      <div className="bg-white border border-[#EBE6DD] rounded-2xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-[#191816]">
            <thead className="bg-[#FAF7F2] text-[#78716C] text-xs uppercase tracking-wider border-b border-[#EBE6DD] font-medium">
              <tr>
                <th className="py-3.5 px-4">Case ID</th>
                <th className="py-3.5 px-4">Expected Status</th>
                <th className="py-3.5 px-4">Provider Status</th>
                <th className="py-3.5 px-4">Case Status</th>
                <th className="py-3.5 px-4">Reason / Notes</th>
                <th className="py-3.5 px-4">Opened</th>
                <th className="py-3.5 px-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EBE6DD]">
              {cases.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-[#78716C] text-xs">
                    No discrepancies or open reconciliation cases found. Click "Scan Stale Transactions" to check unconfirmed states.
                  </td>
                </tr>
              ) : (
                cases.map((c) => (
                  <tr key={c.id} className="hover:bg-[#FAF7F2]/80 transition-colors">
                    <td className="py-3.5 px-4 font-mono text-xs text-[#191816] font-medium">{c.id.substring(0, 13)}...</td>
                    <td className="py-3.5 px-4 font-mono text-xs font-semibold text-[#854D0E]">{c.expectedStatus}</td>
                    <td className="py-3.5 px-4 font-mono text-xs text-[#57534E]">{c.providerStatus || 'UNKNOWN'}</td>
                    <td className="py-3.5 px-4">
                      <StatusBadge status={c.caseStatus} size="sm" />
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#57534E] truncate max-w-[200px]">
                      {c.resolutionReason || c.resolution || 'N/A'}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#78716C]">
                      {new Date(c.createdAt).toLocaleTimeString()}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      {c.caseStatus === 'OPEN' ? (
                        <button
                          onClick={() => setActiveCase(c)}
                          className="px-3.5 py-1 bg-[#E2F899] hover:bg-[#D4EE7F] text-[#2C410F] border border-[#D4EE7F] rounded-full text-xs font-semibold shadow-sm"
                        >
                          Resolve
                        </button>
                      ) : (
                        <span className="text-xs text-[#78716C] font-medium">Settled</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {activeCase && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white border border-[#EBE6DD] rounded-3xl max-w-md w-full p-6 space-y-4 shadow-2xl animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-lg text-[#191816]">Resolve Discrepancy Case</h3>
              <button onClick={() => setActiveCase(null)} className="text-[#78716C] hover:text-[#191816] p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleResolve} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Target State Resolution</label>
                <select
                  value={resolution}
                  onChange={(e) => setResolution(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                >
                  <option value="MARK_SUCCEEDED">MARK_SUCCEEDED (Payment Confirmed Paid)</option>
                  <option value="MARK_FAILED">MARK_FAILED (Confirmed Failed / Dropped)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Audit Justification</label>
                <textarea
                  required
                  rows={3}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                />
              </div>

              <div className="flex items-center justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setActiveCase(null)}
                  className="px-4 py-2.5 rounded-full bg-[#EFEAE2] hover:bg-[#E5DFD4] text-[#191816] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2.5 rounded-full bg-[#FF5A36] hover:bg-[#E84B29] disabled:opacity-50 text-white text-xs font-semibold shadow-md shadow-[#FF5A36]/20"
                >
                  {submitting ? 'Resolving...' : 'Confirm Resolution'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
