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
      case 'HEALTHY':
        return 'bg-[#E2F899] text-[#2C410F] border-[#D4EE7F]';
      case 'FAILED':
      case 'DECLINED':
      case 'UNHEALTHY':
      case 'CLOSED':
        return 'bg-[#FED7CA] text-[#991B1B] border-[#FCA5A5]';
      case 'PENDING':
      case 'PROCESSING':
      case 'OPEN':
        return 'bg-[#FEF08A] text-[#854D0E] border-[#FDE047]';
      case 'REQUIRES_ACTION':
        return 'bg-[#BAE6FD] text-[#0369A1] border-[#7DD3FC]';
      case 'PARTIALLY_REFUNDED':
      case 'REFUNDED':
        return 'bg-[#E9D5FF] text-[#6B21A8] border-[#D8B4FE]';
      case 'CANCELLED':
      default:
        return 'bg-[#EFEAE2] text-[#57534E] border-[#DCD5C9]';
    }
  };

  const px = size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs';

  return (
    <span className={`inline-flex items-center font-medium rounded-full border ${getStyle()} ${px}`}>
      <span className="w-1.5 h-1.5 mr-1.5 rounded-full bg-current opacity-70" />
      {normalized}
    </span>
  );
};
