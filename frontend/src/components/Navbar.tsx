import React from 'react';
import { LogOut, User, Menu, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface Props {
  onOpenSimulator: () => void;
  onToggleSidebar?: () => void;
}

export const Navbar: React.FC<Props> = ({ onOpenSimulator, onToggleSidebar }) => {
  const { session, logout } = useAuth();

  return (
    <header className="h-20 bg-[#FAF7F2]/90 backdrop-blur-md border-b border-[#EBE6DD] px-4 sm:px-8 flex items-center justify-between shrink-0 sticky top-0 z-20">
      <div className="flex items-center space-x-3">
        {/* Mobile menu hamburger toggle */}
        {onToggleSidebar && (
          <button
            onClick={onToggleSidebar}
            className="md:hidden p-2 text-[#191816] hover:bg-[#EFEAE2] rounded-xl transition-colors"
            aria-label="Toggle navigation"
          >
            <Menu className="w-6 h-6" />
          </button>
        )}

        <div className="flex items-center space-x-2.5">
          <Link to="/" className="md:hidden font-bold text-xl text-[#191816] tracking-tight">
            FluxPay
          </Link>
          <span className="text-[11px] font-medium px-2.5 py-1 rounded-full bg-[#EFEAE2] text-[#57534E] border border-[#E0D9CE]">
            Live Sandbox
          </span>
        </div>
      </div>

      <div className="flex items-center space-x-3 sm:space-x-4">
        {/* Simulation trigger */}
        <button
          onClick={onOpenSimulator}
          className="px-4 py-2 rounded-full bg-[#111827] hover:bg-black text-white font-medium text-xs sm:text-sm shadow-sm transition-all flex items-center space-x-1.5"
        >
          <Sparkles className="w-3.5 h-3.5 text-[#FF5A36]" />
          <span>Simulate Payment</span>
        </button>

        <div className="h-6 w-px bg-[#EBE6DD] hidden sm:block" />

        {/* User profile */}
        <div className="flex items-center space-x-2.5 text-sm text-[#191816]">
          <div className="w-8 h-8 rounded-full bg-[#EFEAE2] border border-[#DCD5C9] flex items-center justify-center text-[#57534E]">
            <User className="w-4 h-4" />
          </div>
          <div className="hidden sm:block text-left">
            <p className="text-xs font-semibold text-[#191816] leading-tight truncate max-w-[140px]">
              {session?.email || 'admin@acme.com'}
            </p>
            <p className="text-[10px] font-medium text-[#78716C] uppercase">
              {session?.role || 'MERCHANT_ADMIN'}
            </p>
          </div>
        </div>

        {/* Sign out */}
        <button
          onClick={logout}
          title="Sign Out"
          className="p-2 text-[#78716C] hover:text-[#991B1B] hover:bg-[#FED7CA]/40 rounded-xl transition-colors"
        >
          <LogOut className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
};
