import type { ComponentChildren } from "preact";
import { useEffect, useMemo, useState } from "preact/hooks";
import {
  Activity,
  BookUser,
  ChevronRight,
  ExternalLink,
  KeyRound,
  Link2,
  RefreshCw,
  ShieldCheck,
  Trash2,
  Wifi
} from "lucide-preact";
import { useAppState } from "../app/app-state";
import { Button, Card, EmptyState, Header, IconButton, LoadingState, Sheet, StatusMessage, TokenIcon, TransactionStepper } from "../components/ui";
import { parseUnits, shortAddress } from "../core/encoding";
import { fetchEvmActivity, fetchEvmFeeOptions, fetchKnownApprovals, revokeErc20Approval, type EvmApproval } from "../core/evm-rpc";
import { fetchOctraActivity } from "../core/octra-rpc";
import type { ActivityItem } from "../core/types";

interface Holding {
  key: string;
  name: string;
  symbol: string;
  amount: number;
  usd: number | null;
  network: "octra" | "evm";
}

export function PortfolioScreen() {
  const app = useAppState();
  const holdings = useHoldings();
  const total = holdings.reduce((sum, item) => sum + (item.usd || 0), 0);
  return <Route><Header title="Portfolio" subtitle="All assets across active networks." onBack={app.back} actions={<IconButton icon={RefreshCw} label="Refresh portfolio" className={app.refreshing ? "is-spinning" : ""} onClick={() => void app.refresh(true)} />} /><div class="screen-content portfolio-screen"><Card className="portfolio-chart-card"><div class="portfolio-chart" style={{ background: pieGradient(holdings) }}><span><small>Total</small><strong>{formatUsd(total)}</strong></span></div><div class="portfolio-legend">{holdings.filter((item) => item.usd != null && item.usd > 0).map((item, index) => <span><i style={{ background: pieColor(index) }} />{item.symbol}<strong>{total > 0 ? Math.round((item.usd || 0) / total * 100) : 0}%</strong></span>)}</div></Card><div class="portfolio-list">{holdings.map((holding) => <button class="portfolio-row" key={holding.key} onClick={() => app.navigate({ name: "portfolio-asset", symbol: holding.symbol, network: holding.network })}><TokenIcon symbol={holding.symbol} size={36} /><div><strong>{holding.name}</strong><small>{holding.network === "octra" ? app.octraNetwork?.name : app.evmNetwork?.name}</small></div><span><strong>{trimNumber(holding.amount)} {holding.symbol}</strong><small>{holding.usd == null ? "Price unavailable" : formatUsd(holding.usd)}</small></span><ChevronRight size={14} /></button>)}</div></div></Route>;
}

export function PortfolioAssetScreen({ symbol, network }: { symbol: string; network: "octra" | "evm" }) {
  const app = useAppState();
  const holding = useHoldings().find((item) => item.symbol === symbol && item.network === network);
  const activity = activityFor(app.snapshot ? [...app.snapshot.octraActivity, ...app.snapshot.evmActivity] : [], network, symbol);
  return <Route><Header title={`${symbol} Activity`} subtitle={`${holding?.name || symbol} on the active network.`} onBack={app.back} actions={<IconButton icon={RefreshCw} label="Refresh activity" className={app.refreshing ? "is-spinning" : ""} onClick={() => void app.refresh(true)} />} /><div class="screen-content asset-history-screen">{holding ? <Card className="asset-balance-card"><TokenIcon symbol={holding.symbol} size={40} /><div><small>Balance</small><strong>{trimNumber(holding.amount)} {holding.symbol}</strong><span>{holding.usd == null ? "Price unavailable" : formatUsd(holding.usd)}</span></div></Card> : null}<ActivityRows items={activity} onOpen={(item) => app.navigate({ name: "transaction", item })} />{!activity.length ? <EmptyState>No {symbol} activity on the active network</EmptyState> : null}</div></Route>;
}

export function ActivityCenterScreen() {
  const app = useAppState();
  const [extended, setExtended] = useState<ActivityItem[] | null>(null);
  const [networkLimit, setNetworkLimit] = useState(7);
  const [loadingMore, setLoadingMore] = useState(false);
  const base = useMemo(() => [...(app.snapshot?.octraActivity || []), ...(app.snapshot?.evmActivity || [])].sort((a, b) => b.timestamp - a.timestamp), [app.snapshot]);
  const activity = extended || base;
  async function refresh() { setExtended(null); setNetworkLimit(7); await app.refresh(true); }
  async function loadMore() {
    if (!app.activeWallet || !app.octraNetwork || !app.evmNetwork) return;
    const next = Math.min(50, networkLimit + 7);
    setLoadingMore(true);
    const [octra, evm] = await Promise.all([
      fetchOctraActivity(app.octraNetwork, app.activeWallet.address, next).catch(() => app.snapshot?.octraActivity || []),
      fetchEvmActivity(app.evmNetwork, app.activeWallet.evmAddress, next).catch(() => app.snapshot?.evmActivity || [])
    ]);
    setExtended([...octra, ...evm].sort((a, b) => b.timestamp - a.timestamp)); setNetworkLimit(next); setLoadingMore(false);
  }
  const canLoadMore = networkLimit < 50 && ((extended ? extended.length : base.length) >= networkLimit || networkLimit === 7);
  return <Route><Header title="Activity Center" subtitle="Octra and EVM activity in one timeline." onBack={app.back} actions={<IconButton icon={RefreshCw} label="Refresh activity" className={app.refreshing ? "is-spinning" : ""} onClick={() => void refresh()} />} /><div class="screen-content activity-center"><ActivityRows items={activity} onOpen={(item) => app.navigate({ name: "transaction", item })} />{!activity.length && app.refreshing ? <LoadingState label="Syncing activity" /> : !activity.length ? <EmptyState>No activity on the active networks</EmptyState> : null}{activity.length && canLoadMore ? <button class="load-more" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? <RefreshCw class="spin" size={13} /> : <ChevronRight size={13} />}{loadingMore ? "Loading" : "Load more"}</button> : null}</div></Route>;
}

export function EvmActivityScreen() {
  const app = useAppState();
  const [items, setItems] = useState<ActivityItem[]>(app.snapshot?.evmActivity || []);
  const [limit, setLimit] = useState(7);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function load(nextLimit = limit) {
    if (!app.activeWallet || !app.evmNetwork) return;
    setLoading(true);
    setError("");
    try {
      const loaded = await fetchEvmActivity(app.evmNetwork, app.activeWallet.evmAddress, nextLimit);
      setItems(loaded);
      setLimit(nextLimit);
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setItems(app.snapshot?.evmActivity || []);
    setLimit(7);
    void load(7);
  }, [app.activeWallet?.evmAddress, app.evmNetwork?.id]);

  return <Route>
    <Header title="EVM Activity" subtitle={app.evmNetwork?.name || "Active EVM network"} onBack={app.back} actions={<IconButton icon={RefreshCw} label="Refresh EVM activity" className={loading ? "is-spinning" : ""} onClick={() => void load(limit)} />} />
    <div class="screen-content activity-center">
      <ActivityRows items={items} onOpen={(item) => app.navigate({ name: "transaction", item })} />
      {loading && !items.length ? <LoadingState label="Syncing EVM activity" /> : null}
      {!loading && !items.length && !error ? <EmptyState>No EVM activity on this network</EmptyState> : null}
      {error ? <StatusMessage kind="error" title="EVM activity unavailable" action={<button onClick={() => void load(limit)}>Retry</button>}>{error}</StatusMessage> : null}
      {items.length >= limit && limit < 50 ? <button class="load-more" disabled={loading} onClick={() => void load(Math.min(50, limit + 7))}>{loading ? <RefreshCw class="spin" size={13} /> : <ChevronRight size={13} />}{loading ? "Loading" : "Load more"}</button> : null}
    </div>
  </Route>;
}

export function SecurityCenterScreen() {
  const app = useAppState();
  const [checkedAt, setCheckedAt] = useState(Date.now());
  const rows = [
    { title: "Backup", detail: "Password verification protects key reveal", icon: KeyRound, route: { name: "backup" } as const },
    { title: "dApps", detail: `${app.vault?.settings.connectedSites.length || 0} connected sites`, icon: Link2, route: { name: "connected-sites" } as const },
    { title: "Contacts", detail: `${app.vault?.settings.addressBook.length || 0} saved recipients`, icon: BookUser, route: { name: "address-book" } as const },
    { title: "Network", detail: `${app.octraNetwork?.name || "Octra"} · ${app.evmNetwork?.name || "EVM"}`, icon: Wifi, route: { name: "rpc" } as const }
  ];
  return <Route><Header title="Security Center" subtitle="Review wallet security and permissions." onBack={app.back} actions={<IconButton icon={RefreshCw} label="Refresh checks" onClick={() => { setCheckedAt(Date.now()); void app.refresh(true); }} />} /><div class="screen-content security-center"><StatusMessage kind="success" title="Vault protected">Keys are encrypted locally and the unlocked key is kept only in trusted session storage.</StatusMessage><div class="security-grid">{rows.map(({ title, detail, icon: Icon, route }) => <button onClick={() => app.navigate(route)}><span><Icon size={18} /></span><strong>{title}</strong><small>{detail}</small></button>)}</div><small class="last-check">Last checked {new Date(checkedAt).toLocaleTimeString()}</small></div></Route>;
}

export function RecoveryPlanScreen() {
  const app = useAppState();
  const steps = [
    ["1", "Verify your backup", "Reveal each wallet key with your password and store it offline."],
    ["2", "Test recovery", "Use a separate browser profile and a burner wallet before relying on a backup."],
    ["3", "Review account access", "Remove connected sites and stale token approvals you no longer use."],
    ["4", "Keep networks recorded", "Keep trusted RPC and explorer URLs outside the browser profile."]
  ];
  return <Route><Header title="Recovery Plan" subtitle="A practical checklist for wallet recovery." onBack={app.back} /><div class="screen-content recovery-plan">{steps.map(([number, title, detail]) => <div class="recovery-step"><span>{number}</span><div><strong>{title}</strong><small>{detail}</small></div></div>)}<Button icon={KeyRound} onClick={() => app.navigate({ name: "backup" })}>Review Backup Keys</Button></div></Route>;
}

export function ApprovalsScreen() {
  const app = useAppState();
  const [items, setItems] = useState<EvmApproval[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<EvmApproval | null>(null);
  const [gas, setGas] = useState<Awaited<ReturnType<typeof fetchEvmFeeOptions>> | null>(null);
  const [gasMode, setGasMode] = useState<"market" | "fast" | "custom">("market");
  const [customGas, setCustomGas] = useState("");
  const [stage, setStage] = useState<"review" | "submitting" | "confirming" | "confirmed" | "rejected">("review");
  const [hash, setHash] = useState("");
  const [error, setError] = useState("");
  async function load() {
    if (!app.evmNetwork || !app.activeWallet || !app.vault) return;
    setLoading(true); setError("");
    try { setItems(await fetchKnownApprovals(app.evmNetwork, app.activeWallet.evmAddress, app.vault.settings.evmAssets)); }
    catch (cause) { setError(errorText(cause)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, [app.evmNetwork?.id, app.activeWallet?.evmAddress]);
  async function openReview(item: EvmApproval) {
    if (!app.evmNetwork) return;
    setSelected(item); setStage("review"); setHash(""); setError("");
    try { setGas(await fetchEvmFeeOptions(app.evmNetwork, 60_000n)); } catch (cause) { setError(errorText(cause)); }
  }
  async function revoke() {
    if (!selected || !app.evmNetwork || !app.activeWallet || !gas) return;
    setStage("submitting"); setError("");
    try {
      const gasPrice = gasMode === "fast" ? gas.fast : gasMode === "custom" ? safeUnits(customGas, 9) : gas.market;
      const tx = await revokeErc20Approval({ network: app.evmNetwork, wallet: app.activeWallet, token: selected.token, spender: selected.spenderAddress, gasPrice });
      setHash(tx.hash); setStage("confirming");
      if (!await tx.wait()) throw new Error("Approval revoke was reverted.");
      setStage("confirmed"); await load(); await app.refresh(true);
    } catch (cause) { setError(errorText(cause)); setStage("rejected"); }
  }
  const locked = stage !== "review" && stage !== "confirmed" && stage !== "rejected";
  return <Route><Header title="Approvals" subtitle="Known bridge and swap token allowances." onBack={app.back} actions={<IconButton icon={RefreshCw} label="Refresh approvals" className={loading ? "is-spinning" : ""} onClick={() => void load()} />} /><div class="screen-content approvals-screen"><StatusMessage title="Known spenders only">Checks the Octra bridge, Uniswap Permit2, and Universal Router.</StatusMessage>{loading && !items.length ? <LoadingState label="Scanning allowances" /> : items.length ? items.map((item) => <Card className="approval-row"><span><ShieldCheck size={17} /></span><div><strong>{item.token.symbol} approval</strong><small>{item.spenderName}</small><em>{item.allowance} {item.token.symbol}</em></div><Button compact variant="danger" icon={Trash2} onClick={() => void openReview(item)}>Revoke</Button></Card>) : <EmptyState>No active approvals found</EmptyState>}{error && !selected ? <StatusMessage kind="error" title={error} /> : null}</div><Sheet open={Boolean(selected)} title={stage === "review" ? "Review Revoke" : "Revoking Approval"} locked={locked} onClose={() => setSelected(null)} footer={stage === "review" ? <><Button variant="soft" onClick={() => setSelected(null)}>Cancel</Button><Button variant="danger" icon={Trash2} disabled={!gas} onClick={() => void revoke()}>Proceed</Button></> : stage === "confirmed" || stage === "rejected" ? <Button onClick={() => setSelected(null)}>Done</Button> : undefined}>{selected ? stage === "review" ? <div class="review-content"><Card className="review-summary"><div><span>Token</span><strong>{selected.token.symbol}</strong></div><div><span>Spender</span><strong>{selected.spenderName}</strong></div><div><span>Allowance</span><strong>{selected.allowance}</strong></div><div><span>Network</span><strong>{app.evmNetwork?.name}</strong></div></Card><EvmFeePanel gas={gas} mode={gasMode} setMode={setGasMode} custom={customGas} setCustom={setCustomGas} />{error ? <StatusMessage kind="error" title={error} /> : null}</div> : <div class="progress-content"><TransactionStepper steps={["Submit revoke", "Confirming", "Completed"]} active={stage === "submitting" ? 0 : stage === "confirming" ? 1 : stage === "confirmed" ? 3 : 1} failed={stage === "rejected"} />{error ? <StatusMessage kind="error" title={error} /> : null}{hash ? <Card className="hash-card"><div><span>Transaction hash</span><strong>{shortAddress(hash, 10)}</strong></div></Card> : null}{stage === "confirmed" && hash ? <Button compact variant="soft" icon={ExternalLink} onClick={() => window.open(`${app.evmNetwork?.explorerUrl}/tx/${hash}`, "_blank", "noopener")}>View on Explorer</Button> : null}</div> : null}</Sheet></Route>;
}

function ActivityRows({ items, onOpen }: { items: ActivityItem[]; onOpen: (item: ActivityItem) => void }) {
  return <div class="consumer-activity-list">{items.map((item) => <button onClick={() => onOpen(item)}><span class={`consumer-activity-icon ${item.network}`}><Activity size={16} /></span><div><strong>{item.opType || (item.amount >= 0 ? "Transaction" : "Transfer")}</strong><small>{item.network === "octra" ? "Octra" : "EVM"} · {item.timestamp > 0 ? new Date(item.timestamp).toLocaleString() : "Time unavailable"}</small><em>{shortAddress(item.to || item.from)}</em></div><span><strong>{trimNumber(Math.abs(item.amount))} {item.symbol}</strong><small class={item.status}>{item.status}</small></span></button>)}</div>;
}

function EvmFeePanel({ gas, mode, setMode, custom, setCustom }: { gas: Awaited<ReturnType<typeof fetchEvmFeeOptions>> | null; mode: "market" | "fast" | "custom"; setMode: (value: "market" | "fast" | "custom") => void; custom: string; setCustom: (value: string) => void }) {
  return <section class="fee-panel"><div class="section-label"><span>Network fee</span><small>Ethereum</small></div><div class="fee-options"><button class={mode === "market" ? "active" : ""} onClick={() => setMode("market")}><strong>Market</strong><small>{gas ? `${trimNumber(Number(gas.market) / 1e9)} Gwei` : "--"}</small></button><button class={mode === "fast" ? "active" : ""} onClick={() => setMode("fast")}><strong>Fast</strong><small>{gas ? `${trimNumber(Number(gas.fast) / 1e9)} Gwei` : "--"}</small></button><button class={mode === "custom" ? "active" : ""} onClick={() => setMode("custom")}><strong>Custom</strong><small>{custom ? `${custom} Gwei` : "Set gas"}</small></button></div>{mode === "custom" ? <label class="field"><span>Gas price (Gwei)</span><input value={custom} onInput={(event) => setCustom(event.currentTarget.value)} inputMode="decimal" /></label> : null}</section>;
}

function useHoldings(): Holding[] {
  const app = useAppState();
  const snapshot = app.snapshot;
  return useMemo(() => {
    if (!snapshot) return [];
    const prices = snapshot.usdPrices || {};
    const values: Holding[] = [
      { key: "oct", name: "Octra", symbol: "OCT", amount: snapshot.octraBalance.amount, usd: priceValue(snapshot.octraBalance.amount, prices.OCT), network: "octra" },
      { key: `eth:${app.evmNetwork?.chainId}`, name: "Ethereum", symbol: app.evmNetwork?.symbol || "ETH", amount: Number(snapshot.evmBalance || 0), usd: priceValue(Number(snapshot.evmBalance || 0), prices.ETH), network: "evm" }
    ];
    snapshot.octraTokens.forEach((token) => values.push({ key: `octra:${token.contractAddress}`, name: token.name, symbol: token.symbol, amount: Number(token.formatted), usd: priceValue(Number(token.formatted), prices[token.symbol]), network: "octra" }));
    snapshot.evmTokens.forEach((token) => values.push({ key: `evm:${token.contractAddress}`, name: token.name, symbol: token.symbol, amount: Number(token.formatted), usd: priceValue(Number(token.formatted), prices[token.symbol]), network: "evm" }));
    return values;
  }, [snapshot, app.evmNetwork?.chainId]);
}

function activityFor(items: ActivityItem[], network: "octra" | "evm", symbol: string): ActivityItem[] { return items.filter((item) => item.network === network && item.symbol.toLowerCase() === symbol.toLowerCase()).sort((a, b) => b.timestamp - a.timestamp); }
function priceValue(amount: number, price: number | undefined): number | null { return Number.isFinite(amount) && price ? amount * price : null; }
function formatUsd(value: number): string { return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: value < 1 ? 4 : 2 }).format(value); }
function trimNumber(value: number): string { return Number.isFinite(value) ? new Intl.NumberFormat("en-US", { maximumFractionDigits: 6 }).format(value) : "0"; }
function pieColor(index: number): string { return ["#10b981", "#60a5fa", "#f6c453", "#fb7185", "#a78bfa", "#22d3ee"][index % 6]; }
function pieGradient(items: Holding[]): string {
  const values = items.filter((item) => item.usd != null && item.usd > 0);
  const total = values.reduce((sum, item) => sum + (item.usd || 0), 0);
  if (!total) return "conic-gradient(var(--surface-strong) 0 100%)";
  let cursor = 0;
  return `conic-gradient(${values.map((item, index) => { const start = cursor; cursor += (item.usd || 0) / total * 100; return `${pieColor(index)} ${start}% ${cursor}%`; }).join(",")})`;
}
function safeUnits(value: string, decimals: number): bigint { try { return parseUnits(value || "0", decimals); } catch { return 0n; } }
function errorText(value: unknown): string { return value instanceof Error ? value.message : "Request failed."; }
function Route({ children }: { children: ComponentChildren }) { return <main class="route-screen">{children}</main>; }
