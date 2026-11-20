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
  Sparkles,
  X,
  Globe,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

interface SidebarProps {
  isOpen?: boolean;
  onClose?: () => void;
}

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/providers', label: 'Providers', icon: Layers },
  { to: '/routing', label: 'Routing Rules', icon: GitFork },
  { to: '/refunds', label: 'Refunds', icon: RotateCcw },
  { to: '/reconciliation', label: 'Reconciliation', icon: Scale },
  { to: '/audit', label: 'Audit Logs', icon: ShieldCheck },
  { to: '/settings', label: 'Settings & API Keys', icon: Settings },
];

export const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  const { session } = useAuth();

  const sidebarContent = (
    <div className="flex flex-col justify-between h-full bg-[#FAF7F2] text-[#191816] border-r border-[#EBE6DD]">
      <div>
        {/* Brand header */}
        <div className="h-20 flex items-center justify-between px-6 border-b border-[#EBE6DD]">
          <NavLink to="/" className="flex items-center space-x-2.5">
            <span className="font-bold text-2xl tracking-tight text-[#191816]">FluxPay</span>
            <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded-full bg-[#FF5A36]/10 text-[#FF5A36] border border-[#FF5A36]/20">
              Console
            </span>
          </NavLink>

          {/* Close button on mobile */}
          {onClose && (
            <button
              onClick={onClose}
              className="md:hidden p-1.5 text-[#78716C] hover:text-[#191816] rounded-lg hover:bg-[#EFEAE2] transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          )}
        </div>

        {/* Navigation items */}
        <nav className="p-4 space-y-1">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={onClose}
                className={({ isActive }) =>
                  `flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
                    isActive
                      ? 'bg-[#111827] text-white shadow-sm'
                      : 'text-[#57534E] hover:text-[#191816] hover:bg-[#EFEAE2]'
                  }`
                }
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        {/* Quick link to Landing / Showcase */}
        <div className="px-4 pt-2">
          <NavLink
            to="/"
            onClick={onClose}
            className="flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium text-[#191816] bg-[#FED7CA]/70 hover:bg-[#FED7CA] border border-[#FBC5B4] transition-colors"
          >
            <div className="flex items-center space-x-2">
              <Sparkles className="w-3.5 h-3.5 text-[#FF5A36]" />
              <span>Public Showcase</span>
            </div>
            <Globe className="w-3.5 h-3.5 text-[#78716C]" />
          </NavLink>
        </div>
      </div>

      {/* Tenant / Session Footer */}
      <div className="p-4 border-t border-[#EBE6DD]">
        <div className="p-3.5 bg-white rounded-2xl border border-[#EBE6DD] shadow-sm">
          <p className="text-[10px] text-[#78716C] uppercase tracking-wider font-semibold">Active Tenant</p>
          <p className="text-xs font-semibold text-[#191816] truncate mt-0.5">
            {session?.merchantId ? `Merchant: ${session.merchantId.substring(0, 8)}...` : 'Acme Global Enterprise'}
          </p>
          <div className="mt-2 pt-2 border-t border-[#F3EFE8] flex items-center justify-between text-[11px] text-[#78716C]">
            <span>Role:</span>
            <span className="text-[#2C410F] bg-[#E2F899] px-2 py-0.5 rounded-full font-medium text-[10px]">
              {session?.role || 'ADMIN'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop Persistent Sidebar */}
      <aside className="hidden md:flex w-64 shrink-0 flex-col h-full">
        {sidebarContent}
      </aside>

      {/* Mobile Drawer */}
      {isOpen && (
        <div className="fixed inset-0 z-50 md:hidden flex">
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
            onClick={onClose}
          />
          {/* Drawer content */}
          <div className="relative w-72 max-w-[80vw] h-full shadow-2xl z-10 animate-in slide-in-from-left duration-200">
            {sidebarContent}
          </div>
        </div>
      )}
    </>
  );
};
