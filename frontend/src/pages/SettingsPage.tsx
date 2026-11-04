import React, { useEffect, useState } from 'react';
import { Key, Plus, Trash2, Copy, Check, ShieldCheck, X } from 'lucide-react';
import { apiRequest } from '../services/api';
import { ApiKeyDto } from '../types';
import { useAuth } from '../context/AuthContext';

export const SettingsPage: React.FC = () => {
  const { session } = useAuth();
  const [keys, setKeys] = useState<ApiKeyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [keyName, setKeyName] = useState('');
  const [keyRole, setKeyRole] = useState('MERCHANT_OPERATOR');
  const [createdKey, setCreatedKey] = useState<ApiKeyDto | null>(null);
  const [copied, setCopied] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const fetchKeys = async () => {
    setLoading(true);
    try {
      const data = await apiRequest<ApiKeyDto[]>('/api-keys');
      setKeys(data || []);
    } catch (err) {
      console.error('Failed to load api keys', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchKeys();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const res = await apiRequest<ApiKeyDto>('/api-keys', {
        method: 'POST',
        body: JSON.stringify({ name: keyName, role: keyRole }),
      });
      setCreatedKey(res);
      fetchKeys();
    } catch (err: any) {
      alert(`Key creation failed: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRevoke = async (id: string) => {
    if (!confirm('Are you sure you want to revoke this API key? This action is immediate and cannot be undone.')) {
      return;
    }
    try {
      await apiRequest(`/api-keys/${id}`, { method: 'DELETE' });
      fetchKeys();
    } catch (err: any) {
      alert(`Revocation failed: ${err.message}`);
    }
  };

  const copySecret = () => {
    if (createdKey?.secretKey) {
      navigator.clipboard.writeText(createdKey.secretKey);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="space-y-8 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white">Merchant Settings & Credentials</h1>
        <p className="text-sm text-slate-400 mt-1">
          Manage secure API access keys and cryptographic authentication
        </p>
      </div>

      {/* Tenant Profile */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
        <h2 className="text-base font-semibold text-white">Merchant Account</h2>
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div>
            <span className="text-slate-500 block">Merchant ID:</span>
            <span className="font-mono text-slate-200 mt-0.5 block">{session?.merchantId || 'N/A'}</span>
          </div>
          <div>
            <span className="text-slate-500 block">Current User Role:</span>
            <span className="text-emerald-400 font-semibold font-mono mt-0.5 block">{session?.role || 'N/A'}</span>
          </div>
        </div>
      </div>

      {/* API Keys */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base font-semibold text-white">API Keys</h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Secret keys for programmatic server-to-server payment execution
            </p>
          </div>

          <button
            onClick={() => {
              setCreatedKey(null);
              setIsModalOpen(true);
            }}
            className="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-xs rounded-lg shadow-md shadow-emerald-600/20 transition-all flex items-center space-x-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Generate New Key</span>
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-950 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="py-3 px-4">Name</th>
                <th className="py-3 px-4">Prefix</th>
                <th className="py-3 px-4">Role</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Created</th>
                <th className="py-3 px-4 text-right">Revoke</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {keys.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500 text-xs">
                    No active API keys found.
                  </td>
                </tr>
              ) : (
                keys.map((k) => (
                  <tr key={k.id} className="hover:bg-slate-800/30">
                    <td className="py-3 px-4 font-semibold text-white text-xs">{k.name}</td>
                    <td className="py-3 px-4 font-mono text-xs text-slate-300">{k.keyPrefix}...</td>
                    <td className="py-3 px-4 text-xs font-mono text-emerald-400">{k.role}</td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                          !k.revoked
                            ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                            : 'bg-rose-950 text-rose-400 border border-rose-800'
                        }`}
                      >
                        {!k.revoked ? 'Active' : 'Revoked'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs text-slate-400">
                      {new Date(k.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      {!k.revoked && (
                        <button
                          onClick={() => handleRevoke(k.id)}
                          className="p-1.5 text-slate-500 hover:text-rose-400 rounded hover:bg-rose-950/30 transition-colors"
                          title="Revoke Key"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-white">Create New API Key</h3>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-white p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            {createdKey ? (
              <div className="space-y-4">
                <div className="p-4 rounded-lg bg-emerald-950/40 border border-emerald-800 text-xs text-emerald-200 space-y-2">
                  <p className="font-semibold text-emerald-100 flex items-center space-x-1.5">
                    <ShieldCheck className="w-4 h-4 text-emerald-400" />
                    <span>Secret Key Generated - Save It Now!</span>
                  </p>
                  <p className="text-[11px] text-emerald-300/80">
                    This secret key will <strong>never</strong> be displayed again. If you lose it, you will need to generate a new key.
                  </p>
                  <div className="flex items-center justify-between p-2.5 bg-slate-950 rounded border border-emerald-900 font-mono text-emerald-400 text-xs break-all">
                    <span>{createdKey.secretKey}</span>
                    <button
                      onClick={copySecret}
                      className="ml-2 p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white shrink-0"
                    >
                      {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex justify-end">
                  <button
                    onClick={() => setIsModalOpen(false)}
                    className="px-4 py-2 rounded-lg bg-emerald-600 text-white text-xs font-medium"
                  >
                    Done
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleCreate} className="space-y-4">
                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">Key Name / Description</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Production Backend Service"
                    value={keyName}
                    onChange={(e) => setKeyName(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">Role / Permissions</label>
                  <select
                    value={keyRole}
                    onChange={(e) => setKeyRole(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                  >
                    <option value="MERCHANT_OPERATOR">MERCHANT_OPERATOR (Create payments, trigger refunds)</option>
                    <option value="MERCHANT_ADMIN">MERCHANT_ADMIN (Full merchant administration)</option>
                    <option value="READ_ONLY_ANALYST">READ_ONLY_ANALYST (Read-only analytics & reporting)</option>
                  </select>
                </div>

                <div className="flex items-center justify-end space-x-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setIsModalOpen(false)}
                    className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={submitting}
                    className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-medium"
                  >
                    {submitting ? 'Generating...' : 'Generate Key'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

