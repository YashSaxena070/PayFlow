import React, { useEffect, useState } from 'react';
import { Plus, RefreshCw, X } from 'lucide-react';
import { apiRequest } from '../services/api';
import { RoutingRuleDto } from '../types';

export const RoutingRulesPage: React.FC = () => {
  const [rules, setRules] = useState<RoutingRuleDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [name, setName] = useState('');
  const [strategy, setStrategy] = useState<'PRIORITY' | 'WEIGHTED'>('PRIORITY');
  const [priority, setPriority] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchRules = async () => {
    setLoading(true);
    try {
      const data = await apiRequest<RoutingRuleDto[]>('/routing-rules');
      setRules(data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch routing rules');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRules();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      await apiRequest('/routing-rules', {
        method: 'POST',
        body: JSON.stringify({
          name,
          strategy,
          priority,
          enabled: true,
          conditionsJson: '{}',
        }),
      });

      setIsModalOpen(false);
      setName('');
      fetchRules();
    } catch (err: any) {
      setError(err.message || 'Failed to create rule');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-1">
            ORCHESTRATION ENGINE
          </span>
          <h1 className="text-3xl font-medium tracking-tight text-[#191816]">Routing Rules & Policies</h1>
          <p className="text-sm text-[#78716C] mt-1">
            Configure priority failover and weighted traffic distribution across gateways
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={fetchRules}
            className="p-2.5 rounded-full bg-white hover:bg-[#FAF7F2] text-[#57534E] border border-[#EBE6DD] transition-colors shadow-sm"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#FF5A36]' : ''}`} />
          </button>
          <button
            onClick={() => setIsModalOpen(true)}
            className="px-5 py-2.5 bg-[#FF5A36] hover:bg-[#E84B29] text-white font-medium text-xs sm:text-sm rounded-full shadow-md shadow-[#FF5A36]/20 transition-all flex items-center space-x-1.5"
          >
            <Plus className="w-4 h-4" />
            <span>Create Rule</span>
          </button>
        </div>
      </div>

      <div className="bg-white border border-[#EBE6DD] rounded-2xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-[#191816]">
            <thead className="bg-[#FAF7F2] text-[#78716C] text-xs uppercase tracking-wider border-b border-[#EBE6DD] font-medium">
              <tr>
                <th className="py-3.5 px-4">Rule Name</th>
                <th className="py-3.5 px-4">Strategy</th>
                <th className="py-3.5 px-4">Priority Rank</th>
                <th className="py-3.5 px-4">Status</th>
                <th className="py-3.5 px-4">Version</th>
                <th className="py-3.5 px-4">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EBE6DD]">
              {rules.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-[#78716C] text-xs">
                    No custom rules defined. System is using default lowest-priority routing across eligible providers.
                  </td>
                </tr>
              ) : (
                rules.map((rule) => (
                  <tr key={rule.id} className="hover:bg-[#FAF7F2]/80 transition-colors">
                    <td className="py-3.5 px-4 font-semibold text-[#191816]">{rule.name}</td>
                    <td className="py-3.5 px-4">
                      <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-medium bg-[#FAF7F2] border border-[#EBE6DD] text-[#191816]">
                        {rule.strategy}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-xs font-mono font-medium text-[#191816]">{rule.priority}</td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                          rule.enabled
                            ? 'bg-[#E2F899] text-[#2C410F] border-[#D4EE7F]'
                            : 'bg-[#EFEAE2] text-[#78716C] border-[#DCD5C9]'
                        }`}
                      >
                        {rule.enabled ? 'Active' : 'Disabled'}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-xs font-mono text-[#78716C]">v{rule.version}</td>
                    <td className="py-3.5 px-4 text-xs text-[#78716C]">
                      {new Date(rule.createdAt).toLocaleDateString()}
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
              <h3 className="font-semibold text-lg text-[#191816]">Create Routing Rule</h3>
              <button onClick={() => setIsModalOpen(false)} className="text-[#78716C] hover:text-[#191816] p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            {error && (
              <div className="p-3 bg-[#FED7CA]/70 border border-[#FCA5A5] rounded-xl text-[#991B1B] text-xs">
                {error}
              </div>
            )}

            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Rule Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. 70/30 Stripe/Razorpay split"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Routing Strategy</label>
                <select
                  value={strategy}
                  onChange={(e) => setStrategy(e.target.value as any)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                >
                  <option value="PRIORITY">Priority Routing (Sequential Failover)</option>
                  <option value="WEIGHTED">Weighted Traffic Split</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#57534E] mb-1">Execution Priority (1 = Highest)</label>
                <input
                  type="number"
                  min="1"
                  max="100"
                  required
                  value={priority}
                  onChange={(e) => setPriority(parseInt(e.target.value) || 1)}
                  className="w-full bg-[#FAF7F2] border border-[#EBE6DD] rounded-xl px-3.5 py-2.5 text-sm text-[#191816] focus:outline-none focus:border-[#FF5A36] font-medium"
                />
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
                  {submitting ? 'Creating...' : 'Create Rule'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
