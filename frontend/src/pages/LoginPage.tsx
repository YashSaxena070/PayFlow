import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Zap, ShieldCheck, ArrowRight, Lock, Mail } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { apiRequest } from '../services/api';

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('admin@acme.com');
  const [password, setPassword] = useState('password123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const data = await apiRequest<{
        token: string;
        userId?: string;
        email?: string;
        merchantId?: string;
        merchantName?: string;
        role?: string;
        user?: { id?: string; email?: string; role?: string };
        merchant?: { id?: string; name?: string };
      }>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });

      login(data.token, {
        userId: data.userId || data.user?.id || '',
        email: data.email || data.user?.email || email,
        role: data.role || data.user?.role || 'MERCHANT_ADMIN',
        merchantId: data.merchantId || data.merchant?.id || '',
        token: data.token,
      });

      navigate('/');
    } catch (err: any) {
      setError(err.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setLoading(false);
    }
  };

  const setDemoCreds = (demoEmail: string) => {
    setEmail(demoEmail);
    setPassword('password123');
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center items-center px-4 py-12 relative overflow-hidden">
      {/* Background glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

      <div className="w-full max-w-md space-y-8 relative z-10">
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-emerald-500 text-slate-950 shadow-xl shadow-emerald-500/20 mb-4">
            <Zap className="w-6 h-6 fill-current" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-white">Sign in to PayFlow Console</h2>
          <p className="mt-2 text-sm text-slate-400">Enterprise AI Payment Orchestration Platform</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl backdrop-blur">
          {error && (
            <div className="mb-6 p-4 rounded-lg bg-rose-950/60 border border-rose-800 text-rose-300 text-xs">
              <p className="font-semibold text-rose-200">Login Failed</p>
              <p className="mt-0.5">{error}</p>
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">Work Email</label>
              <div className="relative">
                <Mail className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg pl-9 pr-4 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-emerald-500 transition-colors"
                  placeholder="name@company.com"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">Password</label>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg pl-9 pr-4 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-emerald-500 transition-colors"
                  placeholder="••••••••••••"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full mt-2 py-2.5 px-4 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white font-medium rounded-lg shadow-lg shadow-emerald-600/20 transition-all flex items-center justify-center space-x-2 text-sm"
            >
              <span>{loading ? 'Authenticating...' : 'Sign In'}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          <div className="mt-8 pt-6 border-t border-slate-800">
            <p className="text-xs text-slate-400 font-semibold mb-3">Quick Demo Accounts (Pre-seeded):</p>
            <div className="grid grid-cols-3 gap-2">
              <button
                type="button"
                onClick={() => setDemoCreds('admin@acme.com')}
                className="px-2.5 py-1.5 text-xs bg-slate-950 hover:bg-slate-800 border border-slate-800 rounded text-slate-300 hover:text-emerald-400 text-center transition-colors"
              >
                Acme Admin
              </button>
              <button
                type="button"
                onClick={() => setDemoCreds('operator@acme.com')}
                className="px-2.5 py-1.5 text-xs bg-slate-950 hover:bg-slate-800 border border-slate-800 rounded text-slate-300 hover:text-emerald-400 text-center transition-colors"
              >
                Operator
              </button>
              <button
                type="button"
                onClick={() => setDemoCreds('analyst@acme.com')}
                className="px-2.5 py-1.5 text-xs bg-slate-950 hover:bg-slate-800 border border-slate-800 rounded text-slate-300 hover:text-emerald-400 text-center transition-colors"
              >
                Analyst
              </button>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-center space-x-2 text-xs text-slate-500">
          <ShieldCheck className="w-4 h-4 text-emerald-500/80" />
          <span>PCI DSS Level 1 Scoped Architecture • Strict Multi-Tenancy</span>
        </div>
      </div>
    </div>
  );
};

