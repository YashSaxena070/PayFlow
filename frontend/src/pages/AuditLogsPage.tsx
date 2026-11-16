import React, { useEffect, useState } from 'react';
import { RefreshCw, Eye } from 'lucide-react';
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
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
            COMPLIANCE
          </span>
          <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Immutable Audit Trail</h1>
          <p className="text-sm text-[#78716C] mt-1">
            PCI DSS compliant append-only ledger with sensitive data tokenization and redaction
          </p>
        </div>

        <button
          onClick={fetchLogs}
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
                <th className="py-3.5 px-4">Action</th>
                <th className="py-3.5 px-4">Resource</th>
                <th className="py-3.5 px-4">Resource ID</th>
                <th className="py-3.5 px-4">Actor</th>
                <th className="py-3.5 px-4">Trace ID</th>
                <th className="py-3.5 px-4">Timestamp</th>
                <th className="py-3.5 px-4 text-right">Payload</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EBE6DD]">
              {logs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-[#78716C] text-xs">
                    No audit records found.
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.id} className="hover:bg-[#FAF7F2]/80 transition-colors">
                    <td className="py-3.5 px-4 font-semibold text-[#191816] text-xs font-mono">
                      {log.action}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#57534E] font-medium">
                      {log.resourceType}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-[#78716C] truncate max-w-[120px]">
                      {log.resourceId}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-[#78716C] truncate max-w-[100px]">
                      {log.actorId ? log.actorId.substring(0, 8) + '...' : 'System'}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-[#78716C] truncate max-w-[120px]">
                      {log.correlationId}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-[#78716C]">
                      {new Date(log.createdAt).toLocaleTimeString()}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => setSelectedMeta(log.metadataRedacted)}
                        className="text-xs text-[#78716C] hover:text-[#FF5A36] p-1.5 rounded-lg hover:bg-[#FAF7F2] transition-colors"
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
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white border border-[#EBE6DD] rounded-3xl max-w-lg w-full p-6 space-y-4 shadow-2xl animate-in zoom-in-95 duration-150">
            <h3 className="font-semibold text-lg text-[#191816]">Sanitized Metadata Inspector</h3>
            <pre className="p-4 bg-[#FAF7F2] rounded-2xl text-xs font-mono text-[#191816] overflow-x-auto max-h-60 border border-[#EBE6DD]">
              {JSON.stringify(JSON.parse(selectedMeta || '{}'), null, 2)}
            </pre>
            <div className="flex justify-end">
              <button
                onClick={() => setSelectedMeta(null)}
                className="px-5 py-2.5 rounded-full bg-[#111827] text-white hover:bg-black text-xs font-semibold"
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
