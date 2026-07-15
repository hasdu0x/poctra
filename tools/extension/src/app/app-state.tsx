import { createContext } from "preact";
import { useCallback, useContext, useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { ComponentChildren } from "preact";
import { reverseOctraIdentity } from "../core/identity";
import { fetchOctraActivity, fetchOctraBalance, fetchOctraTokens } from "../core/octra-rpc";
import { fetchUsdPrices } from "../core/portfolio";
import {
  clearUnlockedSession,
  hasStoredVault,
  loadUnlockedState,
  loadVaultEnvelope,
  saveUnlockedSession,
  saveVaultEnvelope,
  touchUnlockedSession
} from "../core/storage";
import type {
  AppRoute,
  DashboardSnapshot,
  DashboardTab,
  EvmNetwork,
  IdentityProvider,
  OctraNetwork,
  VaultData,
  WalletRecord
} from "../core/types";
import { createVault, encryptVault, encryptVaultWithSessionKey, hydrateVault, unlockVault } from "../core/vault";

type Phase = "loading" | "welcome" | "locked" | "unlocked";
type Toast = { id: number; kind: "success" | "error" | "info" | "warning"; message: string };

interface AppStateValue {
  phase: Phase;
  vault: VaultData | null;
  activeWallet: WalletRecord | null;
  octraNetwork: OctraNetwork | null;
  evmNetwork: EvmNetwork | null;
  identityProvider: IdentityProvider | null;
  route: AppRoute;
  tab: DashboardTab;
  snapshot: DashboardSnapshot | null;
  walletIdentityNames: Record<string, string>;
  refreshing: boolean;
  toast: Toast | null;
  surface: "popup" | "expanded" | "approval";
  createFirstWallet(wallet: WalletRecord, password: string): Promise<void>;
  unlock(password: string): Promise<void>;
  lock(): Promise<void>;
  updateVault(mutator: (vault: VaultData) => VaultData): Promise<void>;
  addWallet(wallet: WalletRecord): Promise<void>;
  navigate(route: AppRoute): void;
  back(): void;
  setTab(tab: DashboardTab): void;
  refresh(force?: boolean): Promise<void>;
  notify(message: string, kind?: Toast["kind"]): void;
  openExpanded(): void;
}

const emptyBalance = { amount: 0, raw: 0n, nonce: 0 };
const AppState = createContext<AppStateValue | null>(null);

export function AppStateProvider({ children }: { children: ComponentChildren }) {
  const [phase, setPhase] = useState<Phase>("loading");
  const [vault, setVault] = useState<VaultData | null>(null);
  const [vaultKey, setVaultKey] = useState("");
  const [routes, setRoutes] = useState<AppRoute[]>([{ name: "dashboard" }]);
  const [tab, setTab] = useState<DashboardTab>("overview");
  const [snapshot, setSnapshot] = useState<DashboardSnapshot | null>(null);
  const [walletIdentityNames, setWalletIdentityNames] = useState<Record<string, string>>({});
  const [refreshing, setRefreshing] = useState(false);
  const [toast, setToast] = useState<Toast | null>(null);
  const refreshSerial = useRef(0);
  const snapshotContext = useRef("");
  const toastTimer = useRef<number | null>(null);
  const surface = (document.documentElement.dataset.surface || "expanded") as AppStateValue["surface"];

  const activeWallet = useMemo(
    () => vault?.wallets.find((wallet) => wallet.address === vault.activeAddress) || vault?.wallets[0] || null,
    [vault]
  );
  const octraNetwork = useMemo(
    () => vault?.settings.octraNetworks.find((network) => network.id === vault.settings.activeOctraNetworkId) || null,
    [vault]
  );
  const evmNetwork = useMemo(
    () => vault?.settings.evmNetworks.find((network) => network.id === vault.settings.activeEvmNetworkId) || null,
    [vault]
  );
  const identityProvider = useMemo(() => {
    if (!vault || !octraNetwork) return null;
    const activeId = vault.settings.activeIdentityProviderByNetwork[octraNetwork.networkType];
    return vault.settings.identityProviders.find((provider) => provider.id === activeId) ||
      vault.settings.identityProviders.find((provider) => provider.networkType === octraNetwork.networkType) || null;
  }, [vault, octraNetwork]);

  useEffect(() => {
    void (async () => {
      const stored = await hasStoredVault();
      if (!stored) {
        setPhase("welcome");
        return;
      }
      const session = await loadUnlockedState();
      if (!session) {
        setPhase("locked");
        return;
      }
      setVault(hydrateVault(session.vault));
      setVaultKey(session.vaultKey);
      setPhase("unlocked");
    })();
  }, []);

  useEffect(() => {
    if (phase !== "unlocked" || !vault || !vaultKey) return;
    const touch = () => void touchUnlockedSession(vault, vaultKey);
    const events: Array<keyof WindowEventMap> = ["pointerdown", "keydown", "focus"];
    events.forEach((event) => window.addEventListener(event, touch, { passive: true }));
    return () => events.forEach((event) => window.removeEventListener(event, touch));
  }, [phase, vault, vaultKey]);

  useEffect(() => {
    document.documentElement.dataset.theme = vault?.settings.theme || "dark";
  }, [vault?.settings.theme]);

  useEffect(() => {
    let cancelled = false;
    if (!vault || !octraNetwork || !identityProvider) { setWalletIdentityNames({}); return; }
    void Promise.all(vault.wallets.map(async (wallet) => [wallet.address, await reverseOctraIdentity(octraNetwork, identityProvider, wallet.address).catch(() => null)] as const)).then((entries) => {
      if (!cancelled) setWalletIdentityNames(Object.fromEntries(entries.filter((entry): entry is readonly [string, string] => Boolean(entry[1]))));
    });
    return () => { cancelled = true; };
  }, [vault?.wallets.map((wallet) => wallet.address).join("|"), octraNetwork?.id, identityProvider?.id]);

  const notify = useCallback((message: string, kind: Toast["kind"] = "info") => {
    if (toastTimer.current) window.clearTimeout(toastTimer.current);
    const next = { id: Date.now(), kind, message };
    setToast(next);
    toastTimer.current = window.setTimeout(() => setToast((current) => current?.id === next.id ? null : current), 2800);
  }, []);

  const createFirstWallet = useCallback(async (wallet: WalletRecord, password: string) => {
    const next = createVault(wallet);
    const envelope = await encryptVault(next, password);
    const unlocked = await unlockVault(envelope, password);
    await saveVaultEnvelope(envelope);
    await saveUnlockedSession(next, unlocked.sessionKey);
    setVault(next);
    setVaultKey(unlocked.sessionKey);
    setRoutes([{ name: "dashboard" }]);
    setPhase("unlocked");
    notify("Wallet created securely.", "success");
  }, [notify]);

  const unlock = useCallback(async (password: string) => {
    const envelope = await loadVaultEnvelope();
    if (!envelope) throw new Error("Wallet vault was not found.");
    const unlocked = await unlockVault(envelope, password);
    await saveUnlockedSession(unlocked.vault, unlocked.sessionKey);
    setVault(unlocked.vault);
    setVaultKey(unlocked.sessionKey);
    setPhase("unlocked");
  }, []);

  const lock = useCallback(async () => {
    await clearUnlockedSession();
    setVault(null);
    setVaultKey("");
    setSnapshot(null);
    setRoutes([{ name: "dashboard" }]);
    setPhase("locked");
  }, []);

  const updateVault = useCallback(async (mutator: (value: VaultData) => VaultData) => {
    if (!vault || !vaultKey) throw new Error("Unlock wallet before making changes.");
    const next = mutator(structuredClone(vault));
    const envelope = await loadVaultEnvelope();
    if (!envelope) throw new Error("Wallet vault was not found.");
    const encrypted = await encryptVaultWithSessionKey(next, envelope, vaultKey);
    await Promise.all([saveVaultEnvelope(encrypted), saveUnlockedSession(next, vaultKey)]);
    setVault(next);
    if (typeof chrome !== "undefined" && chrome.runtime?.sendMessage) void chrome.runtime.sendMessage({ type: "poctra-wallet-state-changed" }).catch(() => undefined);
  }, [vault, vaultKey]);

  const addWallet = useCallback(async (wallet: WalletRecord) => {
    await updateVault((current) => {
      if (current.wallets.some((item) => item.address === wallet.address)) throw new Error("This wallet is already in your vault.");
      return {
        ...current,
        wallets: [...current.wallets, { ...wallet, name: wallet.name || `Wallet ${current.wallets.length + 1}` }],
        activeAddress: wallet.address
      };
    });
    setSnapshot(null);
    notify("Wallet added.", "success");
  }, [updateVault, notify]);

  const navigate = useCallback((route: AppRoute) => setRoutes((current) => [...current, route]), []);
  const back = useCallback(() => setRoutes((current) => current.length > 1 ? current.slice(0, -1) : current), []);

  const refresh = useCallback(async (force = false) => {
    if (!activeWallet || !octraNetwork || !evmNetwork || !vault) return;
    if (!force && snapshot && Date.now() - snapshot.fetchedAt < 12_000) return;
    const context = `${activeWallet.address}:${octraNetwork.id}:${evmNetwork.id}`;
    const previous = snapshotContext.current === context ? snapshot : null;
    const serial = ++refreshSerial.current;
    setRefreshing(true);
    const evm = import("../core/evm-rpc");
    const [octraBalanceResult, evmBalanceResult, priceResult, identityResult, octraActivityResult, evmActivityResult, octraTokensResult, evmTokensResult] = await Promise.allSettled([
      fetchOctraBalance(octraNetwork, activeWallet.address),
      evm.then((module) => module.fetchEvmBalance(evmNetwork, activeWallet.evmAddress)),
      fetchUsdPrices(),
      identityProvider ? reverseOctraIdentity(octraNetwork, identityProvider, activeWallet.address) : Promise.resolve(null),
      fetchOctraActivity(octraNetwork, activeWallet.address, 7),
      evm.then((module) => module.fetchEvmActivity(evmNetwork, activeWallet.evmAddress, 7)),
      fetchOctraTokens(octraNetwork, activeWallet.address, vault.settings.octraAssets.map((asset) => asset.contractAddress)),
      evm.then((module) => module.fetchEvmTokens(evmNetwork, activeWallet.evmAddress, vault.settings.evmAssets))
    ]);
    if (serial !== refreshSerial.current) return;
    const octraBalance = octraBalanceResult.status === "fulfilled" ? octraBalanceResult.value : previous?.octraBalance || emptyBalance;
    const evmBalance = evmBalanceResult.status === "fulfilled" ? evmBalanceResult.value : { wei: previous?.evmBalanceWei || 0n, formatted: previous?.evmBalance || "0" };
    snapshotContext.current = context;
    setSnapshot({
      octraBalance,
      evmBalanceWei: evmBalance.wei,
      evmBalance: evmBalance.formatted,
      octraUsd: priceResult.status === "fulfilled" ? priceResult.value.OCT || null : previous?.octraUsd || null,
      identityName: identityResult.status === "fulfilled" ? identityResult.value : previous?.identityName || null,
      octraActivity: octraActivityResult.status === "fulfilled" ? octraActivityResult.value : previous?.octraActivity || [],
      evmActivity: evmActivityResult.status === "fulfilled" ? evmActivityResult.value : previous?.evmActivity || [],
      octraTokens: octraTokensResult.status === "fulfilled" ? octraTokensResult.value : previous?.octraTokens || [],
      evmTokens: evmTokensResult.status === "fulfilled" ? evmTokensResult.value : previous?.evmTokens || [],
      usdPrices: priceResult.status === "fulfilled" ? priceResult.value : previous?.usdPrices || {},
      errors: {
        ...resultError("octraBalance", octraBalanceResult),
        ...resultError("evmBalance", evmBalanceResult),
        ...resultError("octraActivity", octraActivityResult),
        ...resultError("evmActivity", evmActivityResult),
        ...resultError("octraTokens", octraTokensResult),
        ...resultError("evmTokens", evmTokensResult)
      },
      fetchedAt: Date.now()
    });
    if (serial === refreshSerial.current) setRefreshing(false);
  }, [activeWallet, octraNetwork, evmNetwork, identityProvider, vault, snapshot]);

  useEffect(() => {
    if (phase === "unlocked" && activeWallet) {
      const context = `${activeWallet.address}:${octraNetwork?.id || ""}:${evmNetwork?.id || ""}`;
      if (snapshotContext.current && snapshotContext.current !== context) setSnapshot(null);
      void refresh(true);
    }
  }, [phase, activeWallet?.address, octraNetwork?.id, evmNetwork?.id]);

  const openExpanded = useCallback(() => {
    const url = typeof chrome !== "undefined" && chrome.runtime?.getURL ? chrome.runtime.getURL("app.html") : "/app.html";
    if (typeof chrome !== "undefined" && chrome.tabs?.create) void chrome.tabs.create({ url });
    else window.open(url, "_blank", "noopener");
  }, []);

  const value: AppStateValue = {
    phase,
    vault,
    activeWallet,
    octraNetwork,
    evmNetwork,
    identityProvider,
    route: routes[routes.length - 1],
    tab,
    snapshot,
    walletIdentityNames,
    refreshing,
    toast,
    surface,
    createFirstWallet,
    unlock,
    lock,
    updateVault,
    addWallet,
    navigate,
    back,
    setTab,
    refresh,
    notify,
    openExpanded
  };

  return <AppState.Provider value={value}>{children}</AppState.Provider>;
}

function resultError(
  key: keyof DashboardSnapshot["errors"],
  result: PromiseSettledResult<unknown>
): DashboardSnapshot["errors"] {
  if (result.status === "fulfilled") return {};
  return { [key]: result.reason instanceof Error ? result.reason.message : "Data sync failed." };
}

export function useAppState(): AppStateValue {
  const value = useContext(AppState);
  if (!value) throw new Error("AppStateProvider is missing.");
  return value;
}
