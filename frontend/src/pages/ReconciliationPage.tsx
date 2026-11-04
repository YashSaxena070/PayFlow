import React, { useEffect, useState } from 'react';
import { Scale, RefreshCw, Check, AlertCircle, X, ShieldAlert } from 'lucide-react';
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
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Reconciliation & Ambiguity Resolution</h1>
          <p className="text-sm text-slate-400 mt-1">
            Reconcile ambiguous timeouts, unconfirmed provider states, and ledger discrepancies
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={fetchCases}
            className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition-colors"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-emerald-400' : ''}`} />
          </button>
          <button
            onClick={handleScan}
            disabled={scanning}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white font-medium text-xs rounded-lg border border-slate-700 transition-all flex items-center space-x-1.5"
          >
            <Scale className={`w-4 h-4 ${scanning ? 'animate-spin text-emerald-400' : ''}`} />
            <span>{scanning ? 'Scanning Stale Attempts...' : 'Scan Stale Transactions'}</span>
          </button>
        </div>
      </div>

      {statusMsg && (
        <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg text-slate-200 text-xs flex items-center space-x-2">
          <Check className="w-4 h-4 text-emerald-400" />
          <span>{statusMsg}</span>
        </div>
      )}

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-950 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-800">
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
            <tbody className="divide-y divide-slate-800/60">
              {cases.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500 text-xs">
                    No discrepancies or open reconciliation cases found. Click "Scan Stale Transactions" to check unconfirmed states.
                  </td>
                </tr>
              ) : (
                cases.map((c) => (
                  <tr key={c.id} className="hover:bg-slate-800/30">
                    <td className="py-3.5 px-4 font-mono text-xs text-slate-300">{c.id.substring(0, 13)}...</td>
                    <td className="py-3.5 px-4 font-mono text-xs text-amber-400">{c.expectedStatus}</td>
                    <td className="py-3.5 px-4 font-mono text-xs text-slate-300">{c.providerStatus || 'UNKNOWN'}</td>
                    <td className="py-3.5 px-4">
                      <StatusBadge status={c.caseStatus} size="sm" />
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-400 truncate max-w-[200px]">
                      {c.resolutionReason || c.resolution || 'N/A'}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-400">
                      {new Date(c.createdAt).toLocaleTimeString()}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      {c.caseStatus === 'OPEN' ? (
                        <button
                          onClick={() => setActiveCase(c)}
                          className="px-2.5 py-1 bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-800 rounded text-xs font-medium"
                        >
                          Resolve
                        </button>
                      ) : (
                        <span className="text-xs text-slate-500">Settled</span>
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
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-white">Resolve Discrepancy Case</h3>
              <button onClick={() => setActiveCase(null)} className="text-slate-400 hover:text-white p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleResolve} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Target State Resolution</label>
                <select
                  value={resolution}
                  onChange={(e) => setResolution(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                >
                  <option value="MARK_SUCCEEDED">MARK_SUCCEEDED (Payment Confirmed Paid)</option>
                  <option value="MARK_FAILED">MARK_FAILED (Confirmed Failed / Dropped)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Audit Justification</label>
                <textarea
                  required
                  rows={3}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="flex items-center justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setActiveCase(null)}
                  className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-medium"
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

