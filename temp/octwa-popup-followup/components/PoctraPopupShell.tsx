import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
  ChevronDown,
  ChevronLeft,
  Coins,
  EyeOff,
  LayoutGrid,
  Maximize2,
  Settings,
  Wifi,
} from 'lucide-react';
import type { PoctraSection } from './PoctraDashboardShell';

type PopupShellProps = {
  section: PoctraSection;
  onNavigate: (section: PoctraSection) => void;
  children: ReactNode;
};

type OverviewHeaderProps = {
  walletLabel: string;
  address: string;
  networkLabel: string;
  networkState: 'connected' | 'disconnected' | 'checking';
  onWalletClick: () => void;
  onNetworkClick: () => void;
  onSettingsClick: () => void;
  onExpandedClick: () => void;
};

type PageHeaderProps = {
  title: string;
  description?: string;
  onBack?: () => void;
  actions?: ReactNode;
};

type MiniActionProps = {
  icon: LucideIcon;
  label: string;
  onClick: () => void;
  loading?: boolean;
  accent?: 'primary' | 'privacy';
  disabled?: boolean;
};

const PRIMARY_TABS: Array<{ id: PoctraSection; label: string; icon: LucideIcon; privacy?: boolean }> = [
  { id: 'overview', label: 'Overview', icon: LayoutGrid },
  { id: 'assets', label: 'Assets', icon: Coins },
  { id: 'privacy', label: 'Privacy', icon: EyeOff, privacy: true },
];

const PRIMARY_SECTIONS = new Set<PoctraSection>(PRIMARY_TABS.map((item) => item.id));

export function PoctraPopupShell({ section, onNavigate, children }: PopupShellProps) {
  const showBottomNavigation = PRIMARY_SECTIONS.has(section);

  return (
    <div className={`poctra-mobile-popup ${showBottomNavigation ? 'has-bottom-nav' : 'is-detail'}`}>
      <main key={section} className="poctra-mobile-scroll" data-popup-section={section}>
        {children}
      </main>

      {showBottomNavigation && (
        <nav className="poctra-mobile-bottom-nav" aria-label="Wallet navigation">
          {PRIMARY_TABS.map(({ id, label, icon: Icon, privacy }) => {
            const active = section === id;
            return (
              <button
                key={id}
                type="button"
                className={`poctra-mobile-nav-item ${active ? 'is-active' : ''} ${privacy ? 'is-privacy' : ''}`}
                onClick={() => onNavigate(id)}
                aria-current={active ? 'page' : undefined}
              >
                <Icon aria-hidden="true" />
                <span>{label}</span>
                <i aria-hidden="true" />
              </button>
            );
          })}
        </nav>
      )}
    </div>
  );
}

export function PoctraPopupOverviewHeader({
  walletLabel,
  address,
  networkLabel,
  networkState,
  onWalletClick,
  onNetworkClick,
  onSettingsClick,
  onExpandedClick,
}: OverviewHeaderProps) {
  return (
    <header className="poctra-mobile-overview-header">
      <button type="button" className="poctra-mobile-wallet-select" onClick={onWalletClick}>
        <span className="poctra-mobile-identicon" aria-hidden="true">
          {walletLabel.trim().charAt(0).toUpperCase() || 'W'}
        </span>
        <span className="poctra-mobile-wallet-copy">
          <b>{walletLabel}</b>
          <small>{address}</small>
        </span>
        <ChevronDown aria-hidden="true" />
      </button>

      <button
        type="button"
        className={`poctra-mobile-network ${networkState}`}
        onClick={onNetworkClick}
        aria-label={`Open ${networkLabel} RPC settings`}
      >
        <Wifi aria-hidden="true" />
        <span>{networkLabel}</span>
      </button>

      <button type="button" className="poctra-mobile-header-icon" onClick={onSettingsClick} aria-label="Open settings">
        <Settings aria-hidden="true" />
      </button>
      <button type="button" className="poctra-mobile-header-icon" onClick={onExpandedClick} aria-label="Open expanded wallet">
        <Maximize2 aria-hidden="true" />
      </button>
    </header>
  );
}

export function PoctraPopupPageHeader({ title, description, onBack, actions }: PageHeaderProps) {
  return (
    <header className="poctra-mobile-page-header">
      {onBack && (
        <button type="button" className="poctra-mobile-back" onClick={onBack} aria-label="Go back">
          <ChevronLeft aria-hidden="true" />
        </button>
      )}
      <span className="poctra-mobile-page-copy">
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </span>
      {actions && <span className="poctra-mobile-page-actions">{actions}</span>}
    </header>
  );
}

export function PoctraPopupMiniAction({ icon: Icon, label, onClick, loading, accent = 'primary', disabled }: MiniActionProps) {
  return (
    <button
      type="button"
      className={`poctra-mobile-mini-action is-${accent}`}
      onClick={onClick}
      disabled={disabled || loading}
    >
      <Icon className={loading ? 'animate-spin' : ''} aria-hidden="true" />
      <span>{label}</span>
    </button>
  );
}
