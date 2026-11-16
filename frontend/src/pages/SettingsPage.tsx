import React, { useEffect, useState } from 'react';
import { Plus, Trash2, Copy, Check, ShieldCheck, X } from 'lucide-react';
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
    <div className="space-y-8 max-w-4xl animate-in fade-in duration-300">
      <div>
        <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
          CREDENTIALS
        </span>
        <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Merchant Settings & Keys</h1>
        <p className="text-sm text-[#78716C] mt-1">
          Manage secure API access keys and server-to-server authentication
        </p>
      </div>

      {/* Tenant Profile */}
      <div className="bg-white border border-[#EBE6DD] rounded-2xl p-6 shadow-sm space-y-4">
        <h2 className="text-lg font-semibold text-[#191816]">Merchant Account Context</h2>
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div>
            <span className="text-[#78716C] font-medium block">Merchant ID:</span>
            <span className="font-mono text-[#191816] font-medium mt-1 block">{session?.merchantId || 'N/A'}</span>
          </div>
          <div>
            <span className="text-[#78716C] font-medium block">Current User Role:</span>
            <span className="text-[#2C410F] bg-[#E2F899] px-2.5 py-0.5 rounded-full font-semibold font-mono mt-1 inline-block">
              {session?.role || 'N/A'}
            </span>
          </div>
        </div>
      </div>

      {/* API Keys */}
      <div className="bg-white border border-[#EBE6DD] rounded-2xl p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-[#191816]">API Access Keys</h2>
            <p className="text-xs text-[#78716C] mt-0.5">
              Secret keys for programmatic server-to-server payment execution
            </p>
          </div>

          <button
            onClick={() => {
              setCreatedKey(null);
              setIsModalOpen(true);
            }}
            className="px-4 py-2 bg-[#FF5A36] hover:bg-[#E84B29] text-white font-medium text-xs rounded-full shadow-md shadow-[#FF5A36]/20 transition-all flex items-center space-x-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Generate New Key</span>
          </button>
        </div>

        <div className="overflow-x-auto rounded-xl border border-[#EBE6DD]">
          <table className="w-full text-left text-sm text-[#191816]">
            <thead className="bg-[#FAF7F2] text-[#78716C] text-xs uppercase tracking-wider border-b border-[#EBE6DD] font-medium">
              <tr>
                <th className="py-3 px-4">Name</th>
                <th className="py-3 px-4">Prefix</th>
                <th className="py-3 px-4">Role</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Created</th>
                <th className="py-3 px-4 text-right">Revoke</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EBE6DD]">
              {keys.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#78716C] text-xs">
                    No active API keys found.
                  </td>
                </tr>
              ) : (
                keys.map((k) => (
                  <tr key={k.id} className="hover:bg-[#FAF7F2]/80 transition-colors">
                    <td className="py-3 px-4 font-semibold text-[#191816] text-xs">{k.name}</td>
                    <td className="py-3 px-4 font-mono text-xs text-[#78716C]">{k.keyPrefix}...</td>
                    <td className="py-3 px-4 text-xs font-mono text-[#57534E]">{k.role}</td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                          !k.revoked
                            ? 'bg-[#E2F899] text-[#2C410F] border-[#D4EE7F]'
                            : 'bg-[#FED7CA] text-[#991B1B] border-[#FCA5A5]'
                        }`}
                      >
                        {!k.revoked ? 'Active' : 'Revoked'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs text-[#78716C]">
                      {new Date(k.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      {!k.revoked && (
                        <button
                          onClick={() => handleRevoke(k.id)}
                          className="p-1.5 text-[#78716C] hover:text-[#991B1B] rounded-lg hover:bg-[#FED7CA]/50 transition-colors"
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
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white border border-[#EBE6DD] rounded-3xl max-w-md w-full p-6 space-y-4 shadow-2xl animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-lg text-[#191816]">Create New API Key</h3>
              <button onClick={() => setIsModalOpen(false)} className="text-[#78716C] hover:text-[#191816] p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            {createdKey ? (
              <div className="space-y-4">
                <div className="p-4 rounded-2xl bg-[#E2F899]/60 border border-[#D4EE7F] text-xs text-[#2C410F] space-y-2">
                  <p className="font-semibold flex items-center space-x-1.5">
                    <ShieldCheck className="w-4 h-4 text-[#2C410F]" />
                    <span>Secret Key Generated - Save It Now!</span>
                  </p>
                  <p className="text-[11px] opacity-90">
                    This secret key will <strong>never</strong> be displayed again. Store it securely in your secret manager.
                  </p>
                  <div className="flex items-center justify-between p-3 bg-white rounded-xl border border-[#D4EE7F] font-mono text-[#191816] text-xs break-all">
                    <span>{createdKey.secretKey}</span>
                    <button
                      onClick={copySecret}
                      className="ml-2 p-1.5 rounded-lg hover:bg-[#FAF7F2] text-[#78716C] hover:text-[#191816] shrink-0"
                    >
                      {copied ? <Check className="w-4 h-4 text-[#2C410F]" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex justify-end">
                  <button
                    onClick={() => setIsModalOpen(false)}
                    className="px-5 py-2.5 rounded-full bg-[#111827] text-white hover:bg-black text-xs font-semibold"
                  >
                    Done
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleCreate} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-[#57534E] mb-1">Key Name / Description</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Production Backend Service"
                    value={keyName}
                    onChange={(e) => setKeyName(e.target.value)}
                    className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#57534E] mb-1">Role / Permissions</label>
                  <select
                    value={keyRole}
                    onChange={(e) => setKeyRole(e.target.value)}
                    className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
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
                    className="px-4 py-2.5 rounded-full bg-[#EFEAE2] hover:bg-[#E5DFD4] text-[#191816] text-xs font-semibold"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={submitting}
                    className="px-5 py-2.5 rounded-full bg-[#FF5A36] hover:bg-[#E84B29] disabled:opacity-50 text-white text-xs font-semibold shadow-md shadow-[#FF5A36]/20"
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
