import { useMemo, useState, type ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
  Activity,
  ArrowDownLeft,
  ArrowUpRight,
  BookUser,
  ChevronRight,
  CircleDollarSign,
  Copy,
  ExternalLink,
  Eye,
  EyeOff,
  FileKey,
  Globe,
  Image as ImageIcon,
  Lock,
  LogOut,
  Plus,
  RefreshCw,
  RotateCcw,
  Send,
  Unlock,
  Wifi,
} from 'lucide-react';
import type { ERC20Token, EVMTransaction, NFTToken } from '../utils/evmRpc';
import type { Transaction } from '../utils/historyMerge';
import type { PoctraSection } from './PoctraDashboardShell';
import { PoctraPopupActivityList } from './PoctraPopupActivityList';
import {
  PoctraPopupMiniAction,
  PoctraPopupOverviewHeader,
  PoctraPopupPageHeader,
  PoctraPopupShell,
} from './PoctraPopupShell';

type PopupDashboardProps = {
  section: PoctraSection;
  onNavigate: (section: PoctraSection) => void;
  walletLabel: string;
  walletAddress: string;
  networkLabel: string;
  networkState: 'connected' | 'disconnected' | 'checking';
  balance: number | null;
  balanceLoading: boolean;
  balanceError: boolean;
  balanceFiat?: string;
  encryptedBalance: number;
  encryptedBalanceLoading: boolean;
  privacyEnabled: boolean;
  pendingTransfers: number;
  transactions: Transaction[];
  transactionsLoading: boolean;
  refreshing: boolean;
  octraAssets: ReactNode;
  evmAddress?: string;
  evmBalance?: string | null;
  evmBalanceFiat?: string;
  evmNetwork: string;
  evmSymbol: string;
  evmConnected: boolean;
  evmLoading: boolean;
  evmTokens: ERC20Token[];
  evmNfts: NFTToken[];
  evmTransactions: EVMTransaction[];
  themeControl: ReactNode;
  resolveAddressLabel?: (address: string) => string | undefined;
  onWalletClick: () => void;
  onNetworkClick: () => void;
  onSettingsClick: () => void;
  onRefresh: () => void;
  onRefreshEvm: () => void;
  onSend: () => void;
  onReceive: () => void;
  onEncrypt: () => void;
  onDecrypt: () => void;
  onPrivateSend: () => void;
  onClaim: () => void;
  onSelectTransaction: (hash: string, pending: boolean) => void;
  onOpenExplorer: () => void;
  onOpenEvm: () => void;
  onOpenEvmNetwork: () => void;
  onSendEvm: () => void;
  onCopyEvmAddress: () => void;
  onSendToken: (token: ERC20Token) => void;
  onSendNft: (nft: NFTToken) => void;
  onOpenEvmTransaction: (hash: string) => void;
  onAddErc20: () => void;
  onAddNft: () => void;
  onOpenRpc: () => void;
  onOpenDApps: () => void;
  onOpenAddressBook: () => void;
  onOpenExport: () => void;
  onOpenExpanded: () => void;
  onLock: () => void;
  onReset: () => void;
};

type SettingsRowProps = {
  icon: LucideIcon;
  title: string;
  subtitle: string;
  onClick: () => void;
  tone?: 'default' | 'danger';
};

function formatBalance(value: number | null): string {
  return value === null ? '--' : value.toLocaleString('en-US', { maximumFractionDigits: 8, useGrouping: false });
}

function shortAddress(value?: string): string {
  if (!value) return 'Loading account...';
  if (value.length <= 23) return value;
  return `${value.slice(0, 11)}...${value.slice(-11)}`;
}

function SettingsRow({ icon: Icon, title, subtitle, onClick, tone = 'default' }: SettingsRowProps) {
  return (
    <button type="button" className={`poctra-mobile-settings-row ${tone === 'danger' ? 'is-danger' : ''}`} onClick={onClick}>
      <span className="poctra-mobile-settings-icon"><Icon aria-hidden="true" /></span>
      <span><b>{title}</b><small>{subtitle}</small></span>
      <ChevronRight aria-hidden="true" />
    </button>
  );
}

function AssetIcon({ symbol, logo }: { symbol: string; logo?: string }) {
  if (logo) return <span className="poctra-mobile-asset-icon"><img src={logo} alt="" /></span>;
  return <span className="poctra-mobile-asset-icon">{symbol.slice(0, 2).toUpperCase()}</span>;
}

export function PoctraPopupDashboard(props: PopupDashboardProps) {
  const [privateBalanceVisible, setPrivateBalanceVisible] = useState(false);
  const privacyTransactions = useMemo(
    () => props.transactions.filter((tx) => ['encrypt', 'decrypt', 'stealth', 'claim', 'private'].includes((tx.op_type || '').toLowerCase())),
    [props.transactions],
  );

  const navigatePrimary = (section: PoctraSection) => {
    props.onNavigate(section);
  };

  return (
    <PoctraPopupShell section={props.section} onNavigate={navigatePrimary}>
      {props.section === 'overview' && (
        <div className="poctra-mobile-screen poctra-mobile-overview">
          <PoctraPopupOverviewHeader
            walletLabel={props.walletLabel}
            address={shortAddress(props.walletAddress)}
            networkLabel={props.networkLabel}
            networkState={props.networkState}
            onWalletClick={props.onWalletClick}
            onNetworkClick={props.onNetworkClick}
            onSettingsClick={props.onSettingsClick}
            onExpandedClick={props.onOpenExpanded}
          />

          <section className="poctra-mobile-balance-hero">
            {props.balanceLoading ? (
              <div className="poctra-mobile-balance-loading"><RefreshCw className="animate-spin" /> Syncing balance</div>
            ) : props.balanceError ? (
              <button type="button" className="poctra-mobile-balance-error" onClick={props.onRefresh}>Balance unavailable. Retry</button>
            ) : (
              <>
                <span>Public Balance</span>
                <div><strong>{formatBalance(props.balance)}</strong><b>OCT</b></div>
                <p><i />{props.balanceFiat || '~ $0.00 USD'}</p>
              </>
            )}
          </section>

          <section className="poctra-mobile-wallet-actions" aria-label="Wallet actions">
            <button type="button" onClick={props.onSend}><span><Send /></span><b>Send</b></button>
            <button type="button" onClick={props.onReceive}><span><ArrowDownLeft /></span><b>Receive</b></button>
            <button type="button" onClick={props.onEncrypt}><span><Lock /></span><b>Encrypt</b></button>
          </section>

          <section className="poctra-mobile-section poctra-mobile-recent">
            <div className="poctra-mobile-section-heading">
              <h2>Recent Activity</h2>
              <span className="poctra-mobile-heading-actions">
                {props.transactionsLoading && <small><RefreshCw className="animate-spin" /> Syncing</small>}
                <button type="button" onClick={() => props.onNavigate('activity')}>All</button>
              </span>
            </div>
            <PoctraPopupActivityList
              walletAddress={props.walletAddress}
              transactions={props.transactions}
              loading={props.transactionsLoading}
              limit={7}
              resolveLabel={props.resolveAddressLabel}
              onSelect={props.onSelectTransaction}
            />
          </section>
        </div>
      )}

      {props.section === 'assets' && (
        <div className="poctra-mobile-screen poctra-mobile-assets">
          <PoctraPopupPageHeader title="Assets" description="Octra assets and EVM account" />

          <section className="poctra-mobile-section">
            <div className="poctra-mobile-section-heading is-stacked">
              <span><h2>EVM Account</h2><p>{props.evmNetwork}</p></span>
            </div>
            <button type="button" className="poctra-mobile-evm-card" onClick={props.onOpenEvm}>
              <span className="poctra-mobile-asset-icon is-evm"><CircleDollarSign /></span>
              <span><b>EVM Account</b><small>{shortAddress(props.evmAddress)}</small></span>
              <span className="poctra-mobile-asset-balance"><b>{props.evmLoading ? '--' : props.evmBalance || '0'}</b><small>{props.evmSymbol}</small></span>
            </button>
          </section>

          <section className="poctra-mobile-section poctra-mobile-octra-assets">
            <div className="poctra-mobile-section-heading is-stacked">
              <span><h2>Octra Assets</h2><p>Native OCT and OCS01 tokens</p></span>
            </div>
            <button type="button" className="poctra-mobile-native-asset" onClick={props.onSend}>
              <span className="poctra-mobile-octra-mark" aria-hidden="true" />
              <span><b>Octra</b><small>OCT</small></span>
              <span className="poctra-mobile-asset-balance"><b>{formatBalance(props.balance)}</b><small>{props.balanceFiat || '~ $0.00 USD'}</small></span>
            </button>
            {props.octraAssets}
          </section>
        </div>
      )}

      {props.section === 'privacy' && (
        <div className="poctra-mobile-screen poctra-mobile-privacy">
          <PoctraPopupPageHeader
            title="Privacy"
            description={props.networkLabel}
            actions={<PoctraPopupMiniAction icon={RefreshCw} label="Refresh" onClick={props.onRefresh} loading={props.refreshing} accent="privacy" />}
          />

          <section className="poctra-mobile-private-balance">
            <span><small>Encrypted Balance</small><strong>{props.encryptedBalanceLoading ? 'Syncing...' : privateBalanceVisible ? `${formatBalance(props.encryptedBalance)} OCT` : '•••••••• OCT'}</strong></span>
            <button type="button" onClick={() => setPrivateBalanceVisible((visible) => !visible)} aria-label={privateBalanceVisible ? 'Hide encrypted balance' : 'Show encrypted balance'}>
              {privateBalanceVisible ? <EyeOff /> : <Eye />}
            </button>
          </section>

          <section className="poctra-mobile-privacy-actions">
            <button type="button" onClick={props.onEncrypt}><Lock /><span><b>Encrypt</b><small>Move OCT into private balance</small></span></button>
            <button type="button" onClick={props.onDecrypt} disabled={!props.privacyEnabled}><Unlock /><span><b>Decrypt</b><small>Return OCT to public balance</small></span></button>
            <button type="button" onClick={props.onPrivateSend} disabled={!props.privacyEnabled}><EyeOff /><span><b>Stealth</b><small>Send from private balance</small></span></button>
            <button type="button" onClick={props.onClaim}><ArrowDownLeft /><span><b>Claim</b><small>{props.pendingTransfers > 0 ? `${props.pendingTransfers} transfers available` : 'Find private transfers'}</small></span></button>
          </section>

          <section className="poctra-mobile-section">
            <div className="poctra-mobile-section-heading"><h2>Recent Activity</h2></div>
            <PoctraPopupActivityList
              walletAddress={props.walletAddress}
              transactions={privacyTransactions}
              loading={props.transactionsLoading}
              limit={7}
              emptyLabel="No privacy activity yet"
              resolveLabel={props.resolveAddressLabel}
              onSelect={props.onSelectTransaction}
            />
          </section>
        </div>
      )}

      {props.section === 'activity' && (
        <div className="poctra-mobile-screen">
          <PoctraPopupPageHeader
            title="Activity Center"
            description="Transactions for the active Octra network"
            onBack={() => props.onNavigate('overview')}
            actions={<PoctraPopupMiniAction icon={RefreshCw} label="Refresh" onClick={props.onRefresh} loading={props.refreshing} />}
          />
          <PoctraPopupActivityList
            walletAddress={props.walletAddress}
            transactions={props.transactions}
            loading={props.transactionsLoading}
            resolveLabel={props.resolveAddressLabel}
            onSelect={props.onSelectTransaction}
          />
          <button type="button" className="poctra-mobile-inline-action" onClick={props.onOpenExplorer}><ExternalLink /> View All on Explorer</button>
        </div>
      )}

      {props.section === 'evm' && (
        <div className="poctra-mobile-screen poctra-mobile-evm">
          <PoctraPopupPageHeader
            title="EVM Account"
            description={`${shortAddress(props.evmAddress)} · ${props.evmNetwork}`}
            onBack={() => props.onNavigate('assets')}
            actions={
              <>
                <PoctraPopupMiniAction icon={RefreshCw} label="Refresh" onClick={props.onRefreshEvm} loading={props.evmLoading} />
                <PoctraPopupMiniAction icon={Wifi} label="Network" onClick={props.onOpenEvmNetwork} />
              </>
            }
          />

          <section className="poctra-mobile-evm-balance">
            <span>Total Balance</span>
            <div><strong>{props.evmLoading ? '--' : props.evmBalance || '0'}</strong><b>{props.evmSymbol}</b></div>
            {props.evmBalanceFiat && <p>{props.evmBalanceFiat}</p>}
            <em className={props.evmConnected ? 'is-connected' : 'is-disconnected'}>{props.evmConnected ? 'RPC Ready' : 'RPC Unavailable'}</em>
          </section>

          <section className="poctra-mobile-wallet-actions is-evm-actions is-two-actions">
            <button type="button" onClick={props.onSendEvm} disabled={!props.evmConnected}><span><Send /></span><b>Send</b></button>
            <button type="button" onClick={props.onCopyEvmAddress}><span><Copy /></span><b>Receive</b></button>
          </section>

          <section className="poctra-mobile-section">
            <div className="poctra-mobile-section-heading is-stacked">
              <span><h2>ERC20 Assets</h2><p>{props.evmNetwork}</p></span>
              <PoctraPopupMiniAction icon={Plus} label="Add" onClick={props.onAddErc20} />
            </div>
            <div className="poctra-mobile-asset-list">
              {props.evmTokens.map((token) => (
                <button key={`${token.chainId}-${token.address}`} type="button" onClick={() => props.onSendToken(token)}>
                  <AssetIcon symbol={token.symbol} logo={token.logo} />
                  <span><b>{token.symbol}</b><small>{token.name}</small></span>
                  <span className="poctra-mobile-asset-balance"><b>{token.isLoading ? '--' : token.balance}</b><small>{token.symbol}</small></span>
                </button>
              ))}
              {!props.evmLoading && props.evmTokens.length === 0 && <p className="poctra-mobile-list-state">No ERC20 assets</p>}
            </div>
          </section>

          <section className="poctra-mobile-section">
            <div className="poctra-mobile-section-heading is-stacked">
              <span><h2>NFTs</h2><p>Collectibles on {props.evmNetwork}</p></span>
              <PoctraPopupMiniAction icon={Plus} label="Add" onClick={props.onAddNft} />
            </div>
            <div className="poctra-mobile-asset-list">
              {props.evmNfts.map((nft) => (
                <button key={`${nft.chainId}-${nft.contractAddress}-${nft.tokenId}`} type="button" onClick={() => props.onSendNft(nft)}>
                  <span className="poctra-mobile-asset-icon">{nft.imageUrl ? <img src={nft.imageUrl} alt="" /> : <ImageIcon />}</span>
                  <span><b>{nft.name}</b><small>{nft.symbol} #{nft.tokenId}</small></span>
                  <ChevronRight />
                </button>
              ))}
              {!props.evmLoading && props.evmNfts.length === 0 && <p className="poctra-mobile-list-state">No NFTs added</p>}
            </div>
          </section>

          <section className="poctra-mobile-section">
            <div className="poctra-mobile-section-heading"><h2>Recent Activity</h2></div>
            <div className="poctra-mobile-evm-activity">
              {props.evmTransactions.slice(0, 7).map((tx) => (
                <button key={tx.hash} type="button" onClick={() => props.onOpenEvmTransaction(tx.hash)}>
                  <span className={tx.type === 'sent' ? 'is-outgoing' : 'is-incoming'}>{tx.type === 'sent' ? <ArrowUpRight /> : <ArrowDownLeft />}</span>
                  <span><b>{tx.type === 'sent' ? 'Sent' : 'Received'}</b><small>{shortAddress(tx.type === 'sent' ? tx.to : tx.from)}</small></span>
                  <strong>{tx.type === 'sent' ? '-' : '+'}{tx.value} {tx.tokenSymbol || props.evmSymbol}</strong>
                </button>
              ))}
              {!props.evmLoading && props.evmTransactions.length === 0 && <p className="poctra-mobile-list-state">No EVM activity yet</p>}
            </div>
          </section>
        </div>
      )}

      {props.section === 'settings' && (
        <div className="poctra-mobile-screen poctra-mobile-settings">
          <PoctraPopupPageHeader title="Settings" description="Network, security, and wallet preferences" onBack={() => props.onNavigate('overview')} />

          <h2>Wallet</h2>
          <div className="poctra-mobile-settings-list">
            <SettingsRow icon={Activity} title="Activity Center" subtitle="Review Octra transaction history" onClick={() => props.onNavigate('activity')} />
            <SettingsRow icon={BookUser} title="Address Book" subtitle="Saved recipients and wallet labels" onClick={props.onOpenAddressBook} />
            <SettingsRow icon={FileKey} title="Backup Keys" subtitle="View private wallet material" onClick={props.onOpenExport} />
          </div>

          <h2>Networks</h2>
          <div className="poctra-mobile-settings-list">
            <SettingsRow icon={Wifi} title="RPC Management" subtitle={`${props.networkLabel} · Octra network`} onClick={props.onOpenRpc} />
            <SettingsRow icon={CircleDollarSign} title="EVM Network" subtitle={props.evmNetwork} onClick={props.onOpenEvmNetwork} />
          </div>

          <h2>dApps</h2>
          <div className="poctra-mobile-settings-list">
            <SettingsRow icon={Globe} title="Connected dApps" subtitle="Review active wallet permissions" onClick={props.onOpenDApps} />
          </div>

          <h2>App</h2>
          <div className="poctra-mobile-settings-list">
            <div className="poctra-mobile-settings-row is-static">
              <span className="poctra-mobile-settings-icon"><Eye aria-hidden="true" /></span>
              <span><b>Appearance</b><small>Light or dark wallet theme</small></span>
              {props.themeControl}
            </div>
            <SettingsRow icon={ExternalLink} title="Expanded View" subtitle="Open the full desktop wallet" onClick={props.onOpenExpanded} />
            <SettingsRow icon={LogOut} title="Lock Wallet" subtitle="Require the wallet password again" onClick={props.onLock} />
            <SettingsRow icon={RotateCcw} title="Reset Wallet" subtitle="Remove local wallet data" onClick={props.onReset} tone="danger" />
          </div>
        </div>
      )}
    </PoctraPopupShell>
  );
}
