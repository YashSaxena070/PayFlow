import React from 'react';
import { LogOut, User, Bell } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

interface Props {
  onOpenSimulator: () => void;
}

export const Navbar: React.FC<Props> = ({ onOpenSimulator }) => {
  const { session, logout } = useAuth();

  return (
    <header className="h-16 bg-slate-950/80 backdrop-blur border-b border-slate-800 px-8 flex items-center justify-between shrink-0 sticky top-0 z-20">
      <div className="flex items-center space-x-4">
        <span className="text-xs font-semibold px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
          Environment: Live Sandbox
        </span>
      </div>

      <div className="flex items-center space-x-4">
        <button
          onClick={onOpenSimulator}
          className="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-xs shadow-md shadow-emerald-600/20 transition-all flex items-center space-x-1.5"
        >
          <span>⚡ Payment Simulator</span>
        </button>

        <div className="h-5 w-px bg-slate-800" />

        <div className="flex items-center space-x-3 text-sm text-slate-300">
          <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-400">
            <User className="w-4 h-4" />
          </div>
          <div className="hidden md:block text-left">
            <p className="text-xs font-medium text-slate-200">{session?.email || 'user@payflow.local'}</p>
            <p className="text-[10px] text-slate-500 capitalize">{session?.role?.toLowerCase()}</p>
          </div>
        </div>

        <button
          onClick={logout}
          title="Sign Out"
          className="p-2 text-slate-400 hover:text-rose-400 hover:bg-rose-950/30 rounded-lg transition-colors border border-transparent hover:border-rose-900/50"
        >
          <LogOut className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
};

