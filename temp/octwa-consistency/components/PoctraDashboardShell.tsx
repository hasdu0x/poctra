import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
  Activity,
  ChevronDown,
  Coins,
  EyeOff,
  LayoutDashboard,
  RefreshCw,
  Settings,
  Wifi,
} from 'lucide-react';

export type PoctraSection = 'overview' | 'assets' | 'activity' | 'privacy' | 'evm' | 'settings';

type ShellProps = {
  compact: boolean;
  section: PoctraSection;
  onSectionChange: (section: PoctraSection) => void;
  walletLabel: string;
  address: string;
  networkLabel: string;
  connected: boolean;
  refreshing: boolean;
  onWalletClick: () => void;
  onRefresh: () => void;
  children: ReactNode;
};

type NavItem = {
  id: PoctraSection;
  label: string;
  icon: LucideIcon;
  privacy?: boolean;
};

const NAV_ITEMS: NavItem[] = [
  { id: 'overview', label: 'Overview', icon: LayoutDashboard },
  { id: 'assets', label: 'Assets', icon: Coins },
  { id: 'activity', label: 'Activity', icon: Activity },
  { id: 'privacy', label: 'Privacy', icon: EyeOff, privacy: true },
  { id: 'settings', label: 'Settings', icon: Settings },
];

function WalletIdentity({ walletLabel, address, onWalletClick }: Pick<ShellProps, 'walletLabel' | 'address' | 'onWalletClick'>) {
  return (
    <button type="button" className="poctra-shell-wallet" onClick={onWalletClick}>
      <span className="poctra-shell-avatar" aria-hidden="true">
        {walletLabel.trim().charAt(0).toUpperCase() || 'W'}
      </span>
      <span className="min-w-0 text-left">
        <span className="poctra-shell-wallet-name">{walletLabel}</span>
        <span className="poctra-shell-wallet-address">{address}</span>
      </span>
      <ChevronDown className="poctra-shell-wallet-chevron" aria-hidden="true" />
    </button>
  );
}

function Navigation({ compact, section, onSectionChange }: Pick<ShellProps, 'compact' | 'section' | 'onSectionChange'>) {
  return (
    <nav className={compact ? 'poctra-shell-bottom-nav' : 'poctra-shell-rail'} aria-label="Wallet navigation">
      {!compact && (
        <div className="poctra-shell-mark" aria-label="OctWa">
          <span />
        </div>
      )}
      <div className={compact ? 'contents' : 'poctra-shell-rail-items'}>
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const active = section === item.id || (section === 'evm' && item.id === 'assets');
          return (
            <button
              key={item.id}
              type="button"
              className={`poctra-shell-nav-item ${active ? 'is-active' : ''} ${item.privacy ? 'is-privacy' : ''}`}
              onClick={() => onSectionChange(item.id)}
              aria-current={active ? 'page' : undefined}
            >
              <Icon aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}

export function PoctraDashboardShell({
  compact,
  section,
  onSectionChange,
  walletLabel,
  address,
  networkLabel,
  connected,
  refreshing,
  onWalletClick,
  onRefresh,
  children,
}: ShellProps) {
  return (
    <div className={`poctra-dashboard-surface ${compact ? 'is-popup' : 'is-expanded'}`}>
      {!compact && <Navigation compact={false} section={section} onSectionChange={onSectionChange} />}

      <div className="poctra-shell-workspace">
        <header className="poctra-shell-header">
          <WalletIdentity walletLabel={walletLabel} address={address} onWalletClick={onWalletClick} />
          <div className="poctra-shell-header-actions">
            <div className={`poctra-shell-network ${connected ? 'is-online' : 'is-offline'}`}>
              <Wifi aria-hidden="true" />
              <span>{networkLabel}</span>
            </div>
            <button
              type="button"
              className="poctra-shell-icon-action"
              onClick={onRefresh}
              disabled={refreshing}
              aria-label="Refresh wallet data"
              title="Refresh"
            >
              <RefreshCw className={refreshing ? 'animate-spin' : ''} aria-hidden="true" />
            </button>
            {compact && (
              <button
                type="button"
                className="poctra-shell-icon-action"
                onClick={() => onSectionChange('settings')}
                aria-label="Open settings"
                title="Settings"
              >
                <Settings aria-hidden="true" />
              </button>
            )}
          </div>
        </header>

        <main className="poctra-shell-content">{children}</main>
      </div>

      {compact && <Navigation compact section={section} onSectionChange={onSectionChange} />}
    </div>
  );
}
