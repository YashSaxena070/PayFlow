import React from 'react';

interface Props {
  status: string;
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<Props> = ({ status, size = 'md' }) => {
  const normalized = (status || '').toUpperCase();

  const getStyle = () => {
    switch (normalized) {
      case 'SUCCEEDED':
      case 'AUTO_RESOLVED':
        return 'bg-emerald-950/80 text-emerald-400 border-emerald-800';
      case 'FAILED':
        return 'bg-rose-950/80 text-rose-400 border-rose-800';
      case 'PENDING':
      case 'PROCESSING':
      case 'OPEN':
        return 'bg-amber-950/80 text-amber-400 border-amber-800';
      case 'REQUIRES_ACTION':
        return 'bg-cyan-950/80 text-cyan-400 border-cyan-800';
      case 'PARTIALLY_REFUNDED':
        return 'bg-purple-950/80 text-purple-400 border-purple-800';
      case 'REFUNDED':
        return 'bg-indigo-950/80 text-indigo-400 border-indigo-800';
      case 'CANCELLED':
        return 'bg-slate-800 text-slate-400 border-slate-700';
      default:
        return 'bg-slate-800 text-slate-300 border-slate-700';
    }
  };

  const px = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs';

  return (
    <span className={`inline-flex items-center font-medium rounded-full border ${getStyle()} ${px}`}>
      <span className="w-1.5 h-1.5 mr-1.5 rounded-full bg-current opacity-75" />
      {normalized}
    </span>
  );
};

