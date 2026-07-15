export type WalletType = "generated" | "imported-mnemonic" | "imported-private-key" | "imported-evm-private-key";

export interface WalletRecord {
  address: string;
  privateKeyBase64: string;
  publicKeyHex: string;
  mnemonic?: string;
  evmAddress: string;
  name: string;
  type: WalletType;
  createdAt: number;
}

export interface OctraNetwork {
  id: string;
  name: string;
  networkType: "mainnet" | "devnet";
  rpcUrl: string;
  explorerUrl: string;
}

export interface EvmNetwork {
  id: string;
  name: string;
  chainId: number;
  symbol: string;
  rpcUrl: string;
  explorerUrl: string;
  testnet: boolean;
}

export interface IdentityProvider {
  id: string;
  name: string;
  networkType: "mainnet" | "devnet";
  contractAddress: string;
}

export interface AddressBookEntry {
  id: string;
  name: string;
  address: string;
  identityName?: string;
  kind: "octra" | "evm";
  createdAt: number;
}

export interface OctraAsset {
  contractAddress: string;
  name: string;
  symbol: string;
  decimals: number;
}

export interface EvmAsset {
  contractAddress: string;
  name: string;
  symbol: string;
  decimals: number;
  chainId: number;
}

export interface EvmNft {
  contractAddress: string;
  name: string;
  symbol: string;
  tokenId: string;
  chainId: number;
}

export interface ConnectedSite {
  origin: string;
  name: string;
  permissions: string[];
  connectedAt: number;
  lastUsedAt: number;
}

export interface VaultSettings {
  theme: "dark" | "light";
  autoLockMinutes: number;
  activeOctraNetworkId: string;
  activeEvmNetworkId: string;
  activeIdentityProviderByNetwork: Record<string, string>;
  octraNetworks: OctraNetwork[];
  evmNetworks: EvmNetwork[];
  identityProviders: IdentityProvider[];
  addressBook: AddressBookEntry[];
  octraAssets: OctraAsset[];
  evmAssets: EvmAsset[];
  evmNfts: EvmNft[];
  connectedSites: ConnectedSite[];
}

export interface VaultData {
  version: 1;
  wallets: WalletRecord[];
  activeAddress: string;
  settings: VaultSettings;
}

export interface VaultEnvelope {
  version: 1;
  kdf: "PBKDF2-SHA256";
  iterations: number;
  salt: string;
  iv: string;
  ciphertext: string;
  createdAt: number;
  updatedAt: number;
}

export interface OctraBalance {
  amount: number;
  raw: bigint;
  nonce: number;
}

export interface ActivityItem {
  hash: string;
  from: string;
  to: string;
  amount: number;
  timestamp: number;
  status: "pending" | "confirmed" | "rejected";
  opType: string;
  network: "octra" | "evm";
  symbol: string;
  message?: string;
}

export interface TokenBalance {
  contractAddress: string;
  name: string;
  symbol: string;
  decimals: number;
  raw: bigint;
  formatted: string;
  network: "octra" | "evm";
}

export interface DashboardSnapshot {
  octraBalance: OctraBalance;
  evmBalanceWei: bigint;
  evmBalance: string;
  octraUsd: number | null;
  identityName: string | null;
  octraActivity: ActivityItem[];
  evmActivity: ActivityItem[];
  octraTokens: TokenBalance[];
  evmTokens: TokenBalance[];
  usdPrices: Record<string, number>;
  errors: Partial<Record<"octraBalance" | "evmBalance" | "octraActivity" | "evmActivity" | "octraTokens" | "evmTokens", string>>;
  fetchedAt: number;
}

export interface TransactionProgress {
  state: "review" | "submitting" | "accepted" | "confirming" | "confirmed" | "rejected";
  hash?: string;
  message?: string;
}

export type AppRoute =
  | { name: "dashboard" }
  | { name: "send-oct" }
  | { name: "send-octra-asset"; contractAddress: string }
  | { name: "receive-oct" }
  | { name: "send-evm"; token?: string }
  | { name: "send-evm-nft"; contractAddress: string; tokenId: string }
  | { name: "receive-evm" }
  | { name: "evm-account" }
  | { name: "bridge" }
  | { name: "settings" }
  | { name: "wallets" }
  | { name: "rpc" }
  | { name: "evm-networks" }
  | { name: "identity" }
  | { name: "address-book" }
  | { name: "backup" }
  | { name: "connected-sites" }
  | { name: "portfolio" }
  | { name: "portfolio-asset"; symbol: string; network: "octra" | "evm" }
  | { name: "security-center" }
  | { name: "recovery-plan" }
  | { name: "approvals" }
  | { name: "about" }
  | { name: "activity" }
  | { name: "evm-activity" }
  | { name: "transaction"; item: ActivityItem }
  | { name: "add-octra-asset" }
  | { name: "add-evm-asset" }
  | { name: "add-evm-nft" };

export type DashboardTab = "overview" | "assets" | "trade";
