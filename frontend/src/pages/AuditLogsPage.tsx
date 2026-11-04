import React, { useEffect, useState } from 'react';
import { ShieldCheck, RefreshCw, Eye } from 'lucide-react';
import { apiRequest } from '../services/api';
import { AuditLogDto } from '../types';

export const AuditLogsPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLogDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedMeta, setSelectedMeta] = useState<string | null>(null);

  const fetchLogs = async () => {
    setLoading(true);
    try {
      const res = await apiRequest<{ content: AuditLogDto[] }>('/audit-logs?page=0&size=50');
      setLogs(res?.content || []);
    } catch (err) {
      console.error('Failed to load audit logs', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Immutable Audit Trail</h1>
          <p className="text-sm text-slate-400 mt-1">
            PCI DSS compliant append-only log with automatic sensitive data redaction
          </p>
        </div>

        <button
          onClick={fetchLogs}
          className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition-colors"
          title="Refresh"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-emerald-400' : ''}`} />
        </button>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-950 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="py-3.5 px-4">Action</th>
                <th className="py-3.5 px-4">Resource</th>
                <th className="py-3.5 px-4">Resource ID</th>
                <th className="py-3.5 px-4">Actor</th>
                <th className="py-3.5 px-4">Trace ID</th>
                <th className="py-3.5 px-4">Timestamp</th>
                <th className="py-3.5 px-4 text-right">Payload</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {logs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500 text-xs">
                    No audit records found.
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-800/30">
                    <td className="py-3.5 px-4 font-semibold text-emerald-400 text-xs font-mono">
                      {log.action}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-300 font-medium">
                      {log.resourceType}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-slate-400 truncate max-w-[120px]">
                      {log.resourceId}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-slate-400 truncate max-w-[100px]">
                      {log.actorId ? log.actorId.substring(0, 8) + '...' : 'System'}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-slate-500 truncate max-w-[120px]">
                      {log.correlationId}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-400">
                      {new Date(log.createdAt).toLocaleTimeString()}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => setSelectedMeta(log.metadataRedacted)}
                        className="text-xs text-slate-400 hover:text-white p-1"
                        title="View sanitized payload"
                      >
                        <Eye className="w-3.5 h-3.5 inline" />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {selectedMeta && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-lg w-full p-6 space-y-4 shadow-2xl">
            <h3 className="font-semibold text-white">Sanitized Metadata Inspector</h3>
            <pre className="p-3 bg-slate-950 rounded-lg text-xs font-mono text-emerald-400 overflow-x-auto max-h-60 border border-slate-800">
              {JSON.stringify(JSON.parse(selectedMeta || '{}'), null, 2)}
            </pre>
            <div className="flex justify-end">
              <button
                onClick={() => setSelectedMeta(null)}
                className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 hover:bg-slate-700 text-xs font-medium"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

