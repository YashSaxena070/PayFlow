import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ShieldCheck, ArrowRight, Lock, Mail, ArrowLeft, Sparkles } from 'lucide-react';
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

      navigate('/dashboard');
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
    <div className="min-h-screen bg-[#FAF7F2] text-[#191816] flex flex-col justify-center items-center px-4 py-12 relative overflow-hidden">
      {/* Decorative background glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-[#FED7CA]/40 rounded-full blur-3xl pointer-events-none" />

      <div className="w-full max-w-md space-y-6 relative z-10">
        {/* Top return link & logo */}
        <div className="text-center">
          <Link
            to="/"
            className="inline-flex items-center space-x-2 text-xs font-semibold text-[#78716C] hover:text-[#191816] mb-6 transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to Public Simulator</span>
          </Link>

          <div className="flex justify-center mb-2">
            <span className="text-4xl font-extrabold tracking-tight text-[#191816]">FluxPay</span>
          </div>
          <p className="text-sm font-medium text-[#78716C]">
            Enterprise Multi-Gateway Orchestration Console
          </p>
        </div>

        {/* Login Card */}
        <div className="bg-white border border-[#EBE6DD] rounded-[28px] p-8 shadow-xl">
          {error && (
            <div className="mb-6 p-4 rounded-2xl bg-[#FED7CA]/60 border border-[#FCA5A5] text-[#991B1B] text-xs">
              <p className="font-bold">Login Failed</p>
              <p className="mt-0.5">{error}</p>
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[#57534E] mb-1.5">
                Work Email
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-[#8A857D] absolute left-3.5 top-3.5" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#E2DDD5] rounded-xl pl-10 pr-4 py-3 text-sm text-[#191816] placeholder-[#A8A29E] focus:outline-none focus:border-[#FF5A36] transition-colors font-medium"
                  placeholder="name@company.com"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[#57534E] mb-1.5">
                Password
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-[#8A857D] absolute left-3.5 top-3.5" />
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full bg-[#FAF7F2] border border-[#E2DDD5] rounded-xl pl-10 pr-4 py-3 text-sm text-[#191816] placeholder-[#A8A29E] focus:outline-none focus:border-[#FF5A36] transition-colors font-medium"
                  placeholder="••••••••••••"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full mt-2 py-3.5 px-6 bg-[#FF5A36] hover:bg-[#E84B29] disabled:opacity-50 text-white font-bold rounded-full shadow-lg shadow-[#FF5A36]/25 transition-all flex items-center justify-center space-x-2 text-sm"
            >
              <span>{loading ? 'Authenticating...' : 'Sign In to Console'}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          {/* Quick Demo Accounts */}
          <div className="mt-8 pt-6 border-t border-[#EBE6DD]">
            <p className="text-[11px] text-[#78716C] uppercase font-bold tracking-wider mb-3">
              Quick Demo Accounts (Pre-seeded):
            </p>
            <div className="grid grid-cols-3 gap-2">
              <button
                type="button"
                onClick={() => setDemoCreds('admin@acme.com')}
                className="px-2 py-2 text-xs font-semibold bg-[#FAF7F2] hover:bg-[#FED7CA]/50 border border-[#E2DDD5] rounded-xl text-[#191816] text-center transition-colors"
              >
                Acme Admin
              </button>
              <button
                type="button"
                onClick={() => setDemoCreds('operator@acme.com')}
                className="px-2 py-2 text-xs font-semibold bg-[#FAF7F2] hover:bg-[#FED7CA]/50 border border-[#E2DDD5] rounded-xl text-[#191816] text-center transition-colors"
              >
                Operator
              </button>
              <button
                type="button"
                onClick={() => setDemoCreds('analyst@acme.com')}
                className="px-2 py-2 text-xs font-semibold bg-[#FAF7F2] hover:bg-[#FED7CA]/50 border border-[#E2DDD5] rounded-xl text-[#191816] text-center transition-colors"
              >
                Analyst
              </button>
            </div>
          </div>
        </div>

        {/* Security badge */}
        <div className="flex items-center justify-center space-x-2 text-xs text-[#78716C]">
          <ShieldCheck className="w-4 h-4 text-[#FF5A36]" />
          <span>PCI-DSS Level 1 Scoped • Stripe, Razorpay, Cashfree, PayU, Adyen</span>
        </div>
      </div>
    </div>
  );
};
