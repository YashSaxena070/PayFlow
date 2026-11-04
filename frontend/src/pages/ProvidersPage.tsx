import React, { useEffect, useState } from 'react';
import { Layers, ShieldCheck, Check, Save, AlertCircle, RefreshCw } from 'lucide-react';
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
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Payment Gateways & Providers</h1>
          <p className="text-sm text-slate-400 mt-1">
            Manage provider enablement, priority ranking, and traffic split weights
          </p>
        </div>

        <button
          onClick={fetchProviders}
          className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition-colors"
          title="Refresh"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-emerald-400' : ''}`} />
        </button>
      </div>

      {successMsg && (
        <div className="p-3 bg-emerald-950/60 border border-emerald-800 rounded-lg text-emerald-300 text-xs flex items-center space-x-2">
          <Check className="w-4 h-4" />
          <span>{successMsg}</span>
        </div>
      )}

      {error && (
        <div className="p-3 bg-rose-950/60 border border-rose-800 rounded-lg text-rose-300 text-xs flex items-center space-x-2">
          <AlertCircle className="w-4 h-4" />
          <span>{error}</span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {providers.map((p) => {
          const edit = edits[p.id] || { enabled: p.merchantEnabled, priority: p.priority, weight: p.weight };

          return (
            <div
              key={p.id}
              className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-sm space-y-5"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <div className="w-10 h-10 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-center font-bold text-white">
                    {p.code.substring(0, 2)}
                  </div>
                  <div>
                    <h3 className="font-semibold text-white">{p.displayName}</h3>
                    <p className="text-xs text-slate-500 font-mono">Adapter: {p.code.toLowerCase()}</p>
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
                  <div className="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
                </label>
              </div>

              {/* Capabilities */}
              <div className="space-y-2 text-xs">
                <span className="text-slate-500 font-medium">Capabilities:</span>
                <div className="flex flex-wrap gap-1.5">
                  {p.supports3DS && (
                    <span className="px-2 py-0.5 rounded bg-slate-950 text-slate-300 border border-slate-800">
                      3D Secure 2.0
                    </span>
                  )}
                  {p.supportsRefunds && (
                    <span className="px-2 py-0.5 rounded bg-slate-950 text-slate-300 border border-slate-800">
                      Refunds API
                    </span>
                  )}
                  {p.supportsWebhooks && (
                    <span className="px-2 py-0.5 rounded bg-slate-950 text-slate-300 border border-slate-800">
                      Webhooks
                    </span>
                  )}
                </div>
              </div>

              {/* Currencies & Methods */}
              <div className="grid grid-cols-2 gap-4 text-xs pt-2 border-t border-slate-800/80">
                <div>
                  <span className="text-slate-500 block mb-1">Currencies:</span>
                  <div className="flex flex-wrap gap-1">
                    {p.supportedCurrencies.map((c) => (
                      <span key={c} className="font-mono text-[11px] text-slate-300">
                        {c}
                      </span>
                    ))}
                  </div>
                </div>

                <div>
                  <span className="text-slate-500 block mb-1">Methods:</span>
                  <div className="flex flex-wrap gap-1">
                    {p.supportedMethods.map((m) => (
                      <span key={m} className="font-mono text-[11px] text-slate-300">
                        {m}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              {/* Routing Priority & Weight Inputs */}
              <div className="grid grid-cols-2 gap-4 pt-3 border-t border-slate-800/80">
                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">
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
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-emerald-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">
                    Weighted Traffic Split (0-1000)
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
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-emerald-500"
                  />
                </div>
              </div>

              <button
                onClick={() => handleSave(p.id)}
                disabled={savingId === p.id}
                className="w-full py-2 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 hover:text-white rounded-lg text-xs font-medium transition-colors flex items-center justify-center space-x-1.5"
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

