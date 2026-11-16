import React, { useEffect, useState } from 'react';
import { Layers, Check, Save, AlertCircle, RefreshCw } from 'lucide-react';
import { apiRequest } from '../services/api';
import { ProviderDto } from '../types';

export const ProvidersPage: React.FC = () => {
  const [providers, setProviders] = useState<ProviderDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Local config edits map: providerId -> { enabled, priority, weight }
  const [edits, setEdits] = useState<Record<string, { enabled: boolean; priority: number; weight: number }>>({});

  const fetchProviders = async () => {
    setLoading(true);
    try {
      const data = await apiRequest<ProviderDto[]>('/providers');
      setProviders(data || []);
      const initialEdits: Record<string, { enabled: boolean; priority: number; weight: number }> = {};
      data?.forEach((p) => {
        initialEdits[p.id] = {
          enabled: p.merchantEnabled,
          priority: p.priority,
          weight: p.weight,
        };
      });
      setEdits(initialEdits);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch providers');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProviders();
  }, []);

  const handleSave = async (providerId: string) => {
    setSavingId(providerId);
    setError(null);
    setSuccessMsg(null);

    try {
      const edit = edits[providerId];
      await apiRequest(`/providers/${providerId}/configuration`, {
        method: 'PATCH',
        body: JSON.stringify(edit),
      });

      setSuccessMsg('Provider configuration updated successfully');
      setTimeout(() => setSuccessMsg(null), 3000);
      fetchProviders();
    } catch (err: any) {
      setError(err.message || 'Failed to update provider configuration');
    } finally {
      setSavingId(null);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
            GATEWAYS
          </span>
          <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Integrated Providers</h1>
          <p className="text-sm text-[#78716C] mt-1">
            Manage provider enablement, priority ranking, and traffic split weights across 5 gateways
          </p>
        </div>

        <button
          onClick={fetchProviders}
          className="p-2.5 rounded-full bg-white hover:bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] transition-colors shadow-sm"
          title="Refresh"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#FF5A36]' : ''}`} />
        </button>
      </div>

      {successMsg && (
        <div className="p-3.5 bg-[#E2F899] border border-[#D4EE7F] rounded-2xl text-[#2C410F] text-xs flex items-center space-x-2">
          <Check className="w-4 h-4" />
          <span className="font-medium">{successMsg}</span>
        </div>
      )}

      {error && (
        <div className="p-3.5 bg-[#FED7CA]/60 border border-[#FCA5A5] rounded-2xl text-[#991B1B] text-xs flex items-center space-x-2">
          <AlertCircle className="w-4 h-4" />
          <span className="font-medium">{error}</span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {providers.map((p) => {
          const edit = edits[p.id] || { enabled: p.merchantEnabled, priority: p.priority, weight: p.weight };

          return (
            <div
              key={p.id}
              className="bg-white border border-[#EBE6DD] rounded-2xl p-6 shadow-sm space-y-5"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3.5">
                  <div className="w-11 h-11 rounded-2xl bg-[#FAF7F2] border border-[#EBE6DD] flex items-center justify-center font-bold text-sm text-[#191816]">
                    {p.code.substring(0, 2).toUpperCase()}
                  </div>
                  <div>
                    <h3 className="font-semibold text-base text-[#191816]">{p.displayName}</h3>
                    <p className="text-xs text-[#78716C] font-mono">Adapter: {p.code.toLowerCase()}</p>
                  </div>
                </div>

                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={edit.enabled}
                    onChange={(e) =>
                      setEdits({
                        ...edits,
                        [p.id]: { ...edit, enabled: e.target.checked },
                      })
                    }
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-[#EBE6DD] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#FF5A36]"></div>
                </label>
              </div>

              {/* Capabilities */}
              <div className="space-y-2 text-xs">
                <span className="text-[#78716C] font-medium">Capabilities:</span>
                <div className="flex flex-wrap gap-1.5">
                  {p.supports3DS && (
                    <span className="px-2.5 py-0.5 rounded-full bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] font-medium">
                      3D Secure 2.0
                    </span>
                  )}
                  {p.supportsRefunds && (
                    <span className="px-2.5 py-0.5 rounded-full bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] font-medium">
                      Refunds API
                    </span>
                  )}
                  {p.supportsWebhooks && (
                    <span className="px-2.5 py-0.5 rounded-full bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] font-medium">
                      Webhooks
                    </span>
                  )}
                </div>
              </div>

              {/* Currencies & Methods */}
              <div className="grid grid-cols-2 gap-4 text-xs pt-2 border-t border-[#EBE6DD]">
                <div>
                  <span className="text-[#78716C] font-medium block mb-1">Currencies:</span>
                  <div className="flex flex-wrap gap-1">
                    {p.supportedCurrencies.map((c) => (
                      <span key={c} className="font-mono text-[11px] font-semibold text-[#191816] bg-[#FAF7F2] px-2 py-0.5 rounded-md border border-[#EBE6DD]">
                        {c}
                      </span>
                    ))}
                  </div>
                </div>

                <div>
                  <span className="text-[#78716C] font-medium block mb-1">Methods:</span>
                  <div className="flex flex-wrap gap-1">
                    {p.supportedMethods.map((m) => (
                      <span key={m} className="font-mono text-[11px] font-semibold text-[#191816] bg-[#FAF7F2] px-2 py-0.5 rounded-md border border-[#EBE6DD]">
                        {m}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              {/* Routing Priority & Weight Inputs */}
              <div className="grid grid-cols-2 gap-4 pt-3 border-t border-[#EBE6DD]">
                <div>
                  <label className="block text-xs font-semibold text-[#57534E] mb-1">
                    Routing Priority (1 = Top)
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="100"
                    value={edit.priority}
                    onChange={(e) =>
                      setEdits({
                        ...edits,
                        [p.id]: { ...edit, priority: parseInt(e.target.value) || 1 },
                      })
                    }
                    className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-xs text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#57534E] mb-1">
                    Traffic Weight (0-1000)
                  </label>
                  <input
                    type="number"
                    min="0"
                    max="1000"
                    value={edit.weight}
                    onChange={(e) =>
                      setEdits({
                        ...edits,
                        [p.id]: { ...edit, weight: parseInt(e.target.value) || 0 },
                      })
                    }
                    className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3 py-2 text-xs text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                  />
                </div>
              </div>

              <button
                onClick={() => handleSave(p.id)}
                disabled={savingId === p.id}
                className="w-full py-2.5 bg-[#111827] hover:bg-black disabled:opacity-50 text-white rounded-full text-xs font-semibold transition-colors flex items-center justify-center space-x-1.5 shadow-sm"
              >
                <Save className="w-3.5 h-3.5" />
                <span>{savingId === p.id ? 'Saving Changes...' : 'Save Configuration'}</span>
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
};
