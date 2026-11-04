import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  CreditCard,
  Layers,
  GitFork,
  RotateCcw,
  Scale,
  ShieldCheck,
  Settings,
  Zap,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const NAV_ITEMS = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/providers', label: 'Providers', icon: Layers },
  { to: '/routing', label: 'Routing Rules', icon: GitFork },
  { to: '/refunds', label: 'Refunds', icon: RotateCcw },
  { to: '/reconciliation', label: 'Reconciliation', icon: Scale },
  { to: '/audit', label: 'Audit Logs', icon: ShieldCheck },
  { to: '/settings', label: 'Settings & API Keys', icon: Settings },
];

export const Sidebar: React.FC = () => {
  const { session } = useAuth();

  return (
    <aside className="w-64 bg-slate-950 border-r border-slate-800 flex flex-col justify-between shrink-0">
      <div>
        <div className="h-16 flex items-center px-6 border-b border-slate-800">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 rounded-lg bg-emerald-500 flex items-center justify-center text-slate-950 font-black shadow-lg shadow-emerald-500/20">
              <Zap className="w-5 h-5 fill-current" />
            </div>
            <div>
              <span className="font-bold text-white tracking-tight text-lg">PayFlow</span>
              <span className="ml-1 text-xs uppercase px-1.5 py-0.5 rounded bg-emerald-950 text-emerald-400 font-semibold border border-emerald-800/50">
                AI IDE
              </span>
            </div>
          </div>
        </div>

        <nav className="p-4 space-y-1">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                      : 'text-slate-400 hover:text-slate-100 hover:bg-slate-900'
                  }`
                }
              >
                <Icon className="w-4 h-4" />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </div>

      <div className="p-4 border-t border-slate-800/80">
        <div className="p-3 bg-slate-900/60 rounded-lg border border-slate-800">
          <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold">Tenant</p>
          <p className="text-sm font-medium text-slate-200 truncate mt-0.5">
            {session?.merchantId ? `Merchant: ${session.merchantId.substring(0, 8)}...` : 'Demo Tenant'}
          </p>
          <div className="mt-2 flex items-center justify-between text-xs text-slate-400">
            <span>Role:</span>
            <span className="text-emerald-400 font-mono font-medium">{session?.role || 'ADMIN'}</span>
          </div>
        </div>
      </div>
    </aside>
  );
};

