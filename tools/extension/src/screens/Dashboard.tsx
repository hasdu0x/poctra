import { useEffect, useRef, useState } from "preact/hooks";
import {
  ArrowDownToLine,
  ArrowLeftRight,
  ArrowUpRight,
  Blocks,
  Check,
  ChevronDown,
  ChevronRight,
  GripVertical,
  History,
  LayoutGrid,
  Maximize2,
  Pencil,
  Plus,
  RefreshCw,
  Settings,
  Trash2,
  WalletCards,
  Wifi,
  X
} from "lucide-preact";
import { useAppState } from "../app/app-state";
import { Button, Card, EmptyState, ExternalButton, Header, IconButton, LoadingState, Sheet, StatusMessage, TokenIcon, TransactionStepper, WalletAvatar } from "../components/ui";
import type { ActivityItem, WalletRecord } from "../core/types";
import type { TradeAssetId, TradeCandle, TradeQuote } from "../core/trade";
import { parseUnits, shortAddress } from "../core/encoding";
import { generateWalletRecord } from "../core/wallet";

export function DashboardScreen() {
  const app = useAppState();
  const [walletsOpen, setWalletsOpen] = useState(false);
  useEffect(() => { void app.refresh(); }, []);
  return (
    <main class={`dashboard-shell tab-${app.tab}`}>
      <div class="dashboard-scroll">
        {app.tab === "overview" ? <OverviewHeader onWallets={() => setWalletsOpen(true)} /> : <TabHeader />}
        <div class="dashboard-tab-motion" key={app.tab}>{app.tab === "overview" ? <Overview /> : app.tab === "assets" ? <Assets /> : <Trade />}</div>
      </div>
      <BottomNav />
      <WalletDrawer open={walletsOpen} onClose={() => setWalletsOpen(false)} />
    </main>
  );
}

function OverviewHeader({ onWallets }: { onWallets: () => void }) {
  const { activeWallet, snapshot, octraNetwork, navigate, surface, openExpanded } = useAppState();
  if (!activeWallet || !octraNetwork) return null;
  return (
    <header class={`overview-header ${surface === "popup" ? "has-expand" : "is-expanded"}`}>
      <button class="wallet-select" onClick={onWallets}>
        <WalletAvatar label={snapshot?.identityName || activeWallet.name} />
        <span><strong>{snapshot?.identityName || activeWallet.name}</strong><small class={surface === "expanded" ? "full-address" : ""}>{surface === "expanded" ? activeWallet.address : shortAddress(activeWallet.address, 11)}</small></span>
        <ChevronDown size={16} />
      </button>
      <button class="network-chip" aria-label={`Change Octra network. Current network: ${octraNetwork.networkType}`} title="Change Octra network" onClick={() => navigate({ name: "rpc" })}>
        <Wifi size={17} /><span>{capitalize(octraNetwork.networkType)}</span>
      </button>
      <IconButton icon={Settings} label="Settings" onClick={() => navigate({ name: "settings" })} />
      {surface === "popup" ? <IconButton icon={Maximize2} label="Open expanded view" onClick={openExpanded} /> : null}
    </header>
  );
}

function TabHeader() {
  const { tab, navigate, refreshing, refresh } = useAppState();
  return (
    <header class="tab-header">
      <div><h1>{tab === "assets" ? "Assets" : "Trade"}</h1><p>{tab === "assets" ? "Octra assets and EVM account" : "Swap wOCT with ETH or USDT"}</p></div>
      <div class="header-actions"><IconButton icon={RefreshCw} label="Refresh" className={refreshing ? "is-spinning" : ""} onClick={() => void refresh(true)} />{tab === "assets" ? <IconButton icon={Settings} label="Settings" onClick={() => navigate({ name: "settings" })} /> : null}</div>
    </header>
  );
}

function Overview() {
  const { snapshot, activeWallet, refreshing, refresh, navigate } = useAppState();
  const [limit, setLimit] = useState(7);
  if (!activeWallet) return null;
  const activity = snapshot?.octraActivity || [];
  const usd = snapshot?.octraUsd ? snapshot.octraBalance.amount * snapshot.octraUsd : null;
  return (
    <div class="overview-content">
      <section class="balance-block">
        <div class="eyebrow-row"><span>Public Balance</span>{refreshing ? <RefreshCw class="spin" size={13} /> : null}</div>
        <div class="balance-value"><strong>{formatNumber(snapshot?.octraBalance.amount || 0)}</strong><span>OCT</span></div>
        <p><i />~ {usd == null ? "$--" : formatUsd(usd)} USD</p>
      </section>
      <div class="quick-actions">
        <ActionButton icon={ArrowUpRight} label="Send" onClick={() => navigate({ name: "send-oct" })} />
        <ActionButton icon={ArrowDownToLine} label="Receive" onClick={() => navigate({ name: "receive-oct" })} />
        <ActionButton icon={ArrowLeftRight} label="Bridge" onClick={() => navigate({ name: "bridge" })} />
      </div>
      <section class="activity-section">
        <div class="section-heading"><h2>Recent Activity</h2><button onClick={() => navigate({ name: "activity" })}>All</button></div>
        {!snapshot && refreshing ? <LoadingState label="Syncing activity" /> : null}
        {snapshot && !activity.length ? <EmptyState>No recent activity</EmptyState> : null}
        <div class="activity-list">
          {activity.slice(0, limit).map((item) => <ActivityRow item={item} activeAddress={activeWallet.address} onClick={() => navigate({ name: "transaction", item })} key={item.hash} />)}
        </div>
        {snapshot?.errors.octraActivity ? <StatusMessage kind="warning" title="Activity sync unavailable" action={<button onClick={() => void refresh(true)}>Retry</button>}>{snapshot.errors.octraActivity}</StatusMessage> : null}
        {activity.length > limit ? <button class="load-more" onClick={() => setLimit((value) => value + 7)}><ChevronDown size={13} />Load more</button> : null}
        {!refreshing && snapshot && Date.now() - snapshot.fetchedAt > 30_000 ? <button class="load-more" onClick={() => void refresh(true)}><RefreshCw size={13} />Refresh activity</button> : null}
      </section>
    </div>
  );
}

export function ActivityRow({ item, activeAddress, onClick }: { item: ActivityItem; activeAddress: string; onClick?: () => void }) {
  const outgoing = item.from.toLowerCase() === activeAddress.toLowerCase();
  const title = outgoing ? "Sent" : "Received";
  const peer = outgoing ? item.to : item.from;
  return (
    <button class="activity-row" onClick={onClick}>
      <span class={`activity-icon ${outgoing ? "outgoing" : "incoming"}`}>{outgoing ? <ArrowUpRight size={18} /> : <ArrowDownToLine size={18} />}</span>
      <span class="activity-copy"><span class="activity-title"><strong>{title}</strong><em>{item.opType}</em></span><small>{peer ? `${outgoing ? "to" : "from"} ${shortAddress(peer)}` : "Address unavailable"}</small></span>
      <span class="activity-amount"><strong class={outgoing ? "" : "positive"}>{outgoing ? "-" : "+"}{formatNumber(item.amount)}</strong><small>{formatDate(item.timestamp)}</small></span>
    </button>
  );
}

function Assets() {
  const { snapshot, activeWallet, evmNetwork, navigate } = useAppState();
  if (!activeWallet || !evmNetwork) return null;
  return (
    <div class="assets-content">
      <section class="asset-group account-group">
        <div class="asset-group-heading"><div><h2>Accounts</h2><p>Separate Octra and EVM asset spaces</p></div></div>
        <Card className="asset-card evm-account-entry" onClick={() => navigate({ name: "evm-account" })}>
          <TokenIcon symbol="ETH" />
          <div><strong>{snapshot?.identityName?.endsWith(".eth") ? snapshot.identityName : "EVM Account"}</strong><small>{shortAddress(activeWallet.evmAddress)}</small></div>
          <span><strong>{evmNetwork.name}</strong><small>{snapshot?.evmBalance || "0"} {evmNetwork.symbol}</small></span>
          <ChevronRight size={16} />
        </Card>
      </section>
      <section class="asset-group">
        <div class="asset-group-heading"><div><h2>Octra Assets</h2><p>Native OCT and OCS01 tokens</p></div><Button compact variant="soft" icon={Plus} onClick={() => navigate({ name: "add-octra-asset" })}>Add</Button></div>
        <button class="asset-row primary-asset" onClick={() => navigate({ name: "send-oct" })}><TokenIcon symbol="OCT" /><div><strong>Octra</strong><small>OCT</small></div><span><strong>{formatNumber(snapshot?.octraBalance.amount || 0)}</strong><small>{snapshot?.octraUsd ? `~ ${formatUsd(snapshot.octraBalance.amount * snapshot.octraUsd)} USD` : "~ $-- USD"}</small></span></button>
        {(snapshot?.octraTokens || []).map((token) => <button class="asset-row" onClick={() => navigate({ name: "send-octra-asset", contractAddress: token.contractAddress })} key={token.contractAddress}><TokenIcon symbol={token.symbol} /><div><strong>{token.name}</strong><small>{token.symbol}</small></div><span><strong>{token.formatted}</strong><small>{token.symbol}</small></span></button>)}
      </section>
    </div>
  );
}

export function EvmAccountScreen() {
  const app = useAppState();
  const { snapshot, activeWallet, evmNetwork, navigate, vault, refreshing, refresh } = app;
  if (!activeWallet || !evmNetwork) return null;
  const balance = Number(snapshot?.evmBalance || 0);
  const usd = snapshot?.usdPrices.ETH ? balance * snapshot.usdPrices.ETH : null;
  const nfts = (vault?.settings.evmNfts || []).filter((nft) => nft.chainId === evmNetwork.chainId);
  return (
    <main class="route-screen">
      <Header title="EVM Account" subtitle={evmNetwork.name} onBack={app.back} actions={<><IconButton icon={History} label="EVM activity" onClick={() => navigate({ name: "evm-activity" })} /><IconButton icon={RefreshCw} label="Refresh EVM account" className={refreshing ? "is-spinning" : ""} onClick={() => void refresh(true)} /></>} />
      <div class="screen-content evm-account-screen">
        <Card className="evm-identity-card">
          <TokenIcon symbol="ETH" size={38} />
          <div><strong>{snapshot?.identityName?.endsWith(".eth") ? snapshot.identityName : shortAddress(activeWallet.evmAddress, 8)}</strong><small>{activeWallet.evmAddress}</small></div>
          <button onClick={() => navigate({ name: "evm-networks" })}>{evmNetwork.name}<ChevronRight size={13} /></button>
        </Card>
        <section class="evm-balance-block">
          <span>Total Balance</span>
          <div><strong>{formatNumber(balance)}</strong><em>{evmNetwork.symbol}</em></div>
          <small>~ {usd == null ? "$--" : formatUsd(usd)} USD</small>
        </section>
        <div class="quick-actions evm-quick-actions">
          <ActionButton icon={ArrowUpRight} label="Send" onClick={() => navigate({ name: "send-evm" })} />
          <ActionButton icon={ArrowDownToLine} label="Receive" onClick={() => navigate({ name: "receive-evm" })} />
          <ActionButton icon={Wifi} label="Network" onClick={() => navigate({ name: "evm-networks" })} />
        </div>
        <section class="asset-group">
          <div class="asset-group-heading"><div><h2>Assets</h2><p>Tokens on {evmNetwork.name}</p></div><div class="asset-heading-actions"><Button compact variant="soft" icon={Plus} onClick={() => navigate({ name: "add-evm-asset" })}>ERC20</Button><Button compact variant="soft" icon={Plus} onClick={() => navigate({ name: "add-evm-nft" })}>NFT</Button></div></div>
          <div class="asset-list evm-asset-list">
            <button class="asset-row primary-asset" onClick={() => navigate({ name: "send-evm" })}><TokenIcon symbol="ETH" /><div><strong>Ethereum</strong><small>{evmNetwork.symbol}</small></div><span><strong>{formatNumber(balance)}</strong><small>{usd == null ? "~ $-- USD" : `~ ${formatUsd(usd)} USD`}</small></span></button>
            {(snapshot?.evmTokens || []).map((token) => <button class="asset-row" onClick={() => navigate({ name: "send-evm", token: token.contractAddress })} key={token.contractAddress}><TokenIcon symbol={token.symbol} size={36} /><div><strong>{token.name}</strong><small>{token.symbol}</small></div><span><strong>{token.formatted}</strong><small>{token.symbol}</small></span></button>)}
            {nfts.map((nft) => <button class="asset-row nft-row" onClick={() => navigate({ name: "send-evm-nft", contractAddress: nft.contractAddress, tokenId: nft.tokenId })} key={`${nft.contractAddress}:${nft.tokenId}`}><TokenIcon symbol="NFT" size={36} /><div><strong>{nft.name}</strong><small>{nft.symbol} #{nft.tokenId}</small></div><span><strong>NFT</strong><small>{nft.symbol}</small></span></button>)}
          </div>
        </section>
      </div>
    </main>
  );
}

function Trade() {
  const app = useAppState();
  const { evmNetwork, snapshot, navigate, activeWallet } = app;
  const [from, setFrom] = useState<TradeAssetId>("ETH");
  const [to, setTo] = useState<TradeAssetId>("wOCT");
  const [amount, setAmount] = useState("");
  const [slippage, setSlippage] = useState("0.5");
  const [frame, setFrame] = useState<"1H" | "4H" | "1D" | "1W">("1H");
  const [candles, setCandles] = useState<TradeCandle[]>([]);
  const [chartError, setChartError] = useState("");
  const [chartLoading, setChartLoading] = useState(false);
  const [quote, setQuote] = useState<TradeQuote | null>(null);
  const [quoteError, setQuoteError] = useState("");
  const [quoting, setQuoting] = useState(false);
  const [review, setReview] = useState(false);
  const [progress, setProgress] = useState<"review" | "approving" | "swapping" | "confirmed" | "rejected">("review");
  const [progressLabel, setProgressLabel] = useState("");
  const [txHash, setTxHash] = useState("");
  const [gasMode, setGasMode] = useState<"market" | "fast" | "custom">("market");
  const [customGas, setCustomGas] = useState("");
  const quoteSerial = useRef(0);
  const chartSerial = useRef(0);
  const balances = { ETH: snapshot?.evmBalance || "0", wOCT: snapshot?.evmTokens.find((token) => token.symbol === "wOCT")?.formatted || "0", USDT: snapshot?.evmTokens.find((token) => token.symbol === "USDT")?.formatted || "0" };

  async function refreshChart(force = false) {
    const serial = ++chartSerial.current;
    setChartLoading(true); setChartError("");
    try {
      const loaded = await import("../core/trade").then((module) => module.fetchTradeCandles(frame, force));
      if (serial !== chartSerial.current) return;
      setCandles(loaded.candles);
      setChartError(loaded.warning || "");
    } catch (cause) {
      if (serial === chartSerial.current) setChartError(cause instanceof Error ? cause.message : "Chart refresh failed.");
    } finally { if (serial === chartSerial.current) setChartLoading(false); }
  }

  useEffect(() => { if (evmNetwork?.chainId === 1) void refreshChart(false); }, [frame, evmNetwork?.id]);
  useEffect(() => {
    const serial = ++quoteSerial.current;
    setQuote(null); setQuoteError("");
    if (!evmNetwork || evmNetwork.chainId !== 1 || !amount || Number(amount) <= 0) { setQuoting(false); return; }
    const timer = window.setTimeout(() => {
      setQuoting(true);
      void import("../core/trade").then((module) => module.quoteTrade({ network: evmNetwork, from, to, amount, slippageBps: Math.round(Number(slippage) * 100) }))
        .then((value) => { if (serial === quoteSerial.current) setQuote(value); })
        .catch((cause) => { if (serial === quoteSerial.current) setQuoteError(cause instanceof Error ? cause.message : "Swap quote failed."); })
        .finally(() => { if (serial === quoteSerial.current) setQuoting(false); });
    }, 550);
    return () => window.clearTimeout(timer);
  }, [amount, from, to, slippage, evmNetwork?.id]);

  function selectFrom(value: string) {
    const next = value as TradeAssetId;
    setFrom(next);
    if (next !== "wOCT" && to !== "wOCT") setTo("wOCT");
    if (next === to) setTo(next === "wOCT" ? "ETH" : "wOCT");
  }
  function selectTo(value: string) {
    const next = value as TradeAssetId;
    setTo(next);
    if (next !== "wOCT" && from !== "wOCT") setFrom("wOCT");
    if (next === from) setFrom(next === "wOCT" ? "ETH" : "wOCT");
  }
  async function execute() {
    if (!quote || !evmNetwork || !activeWallet) return;
    setProgress("approving"); setQuoteError("");
    try {
      const gasPrice = gasMode === "fast" ? quote.gasPrice * 125n / 100n : gasMode === "custom" ? parseUnits(customGas || "0", 9) : quote.gasPrice;
      const result = await import("../core/trade").then((module) => module.executeTrade({ network: evmNetwork, wallet: activeWallet, from, to, quote, gasPrice, onStage: (label) => { setProgressLabel(label); setProgress(label.toLowerCase().includes("swap") ? "swapping" : "approving"); } }));
      setTxHash(result.hash); setProgress("confirmed"); await app.refresh(true);
    } catch (cause) { setQuoteError(cause instanceof Error ? cause.message : "Swap failed."); setProgress("rejected"); }
  }
  function closeReview() {
    setReview(false);
    if (progress === "confirmed" || progress === "rejected") { setProgress("review"); setProgressLabel(""); setTxHash(""); setAmount(""); setQuote(null); }
  }
  const explorer = txHash && evmNetwork ? `${evmNetwork.explorerUrl}/tx/${txHash}` : "";
  return (
    <div class="trade-content">
      <div class="trade-network"><TokenIcon symbol="ETH" size={25} /><span><strong>{evmNetwork?.name || "EVM Network"}</strong><button onClick={() => navigate({ name: "evm-networks" })}>Change</button></span></div>
      <Card className="chart-card">
        <div class="chart-toolbar"><div>{(["1H", "4H", "1D", "1W"] as const).map((value) => <button type="button" aria-pressed={frame === value} class={frame === value ? "active" : ""} onClick={() => setFrame(value)} key={value}>{value}</button>)}</div><IconButton icon={RefreshCw} label="Refresh chart" className={chartLoading ? "is-spinning" : ""} onClick={() => void refreshChart(true)} /></div>
        <CandleChart candles={candles} loading={chartLoading} />
        {chartError ? <StatusMessage kind="warning" title="Chart rate limited">{chartError}</StatusMessage> : null}
      </Card>
      <Card className="swap-card">
        <div class="swap-heading"><div><h2>Swap</h2><p>Balances on the active network</p></div><IconButton icon={RefreshCw} label="Refresh balances" className={app.refreshing ? "is-spinning" : ""} onClick={() => void app.refresh(true)} /></div>
        <SwapField label="From" asset={from} amount={amount} balance={balances[from]} onAmount={setAmount} onAsset={selectFrom} options={["ETH", "wOCT", "USDT"]} />
        <button class="swap-direction" aria-label="Switch swap direction" title="Switch swap direction" onClick={() => { setFrom(to); setTo(from); }}><ArrowLeftRight size={17} /></button>
        <SwapField label="To" asset={to} amount={quote?.amountOut || (quoting ? "..." : "0.0")} balance={balances[to]} onAsset={selectTo} options={["wOCT", "ETH", "USDT"]} readOnly />
        <div class="slippage-row"><span>Slippage</span>{["0.5", "1", "3"].map((value) => <button type="button" aria-pressed={slippage === value} class={slippage === value ? "active" : ""} onClick={() => setSlippage(value)} key={value}>{value}%</button>)}</div>
        {quoteError ? <StatusMessage kind="error" title="Quote unavailable" action={<button onClick={() => { const value = amount; setAmount(""); window.setTimeout(() => setAmount(value), 0); }}>Retry</button>}>{quoteError}</StatusMessage> : <StatusMessage title={quoting ? "Fetching the best available quote" : quote ? "Quote ready for review" : "Enter an amount to request a quote"}>{quoting ? "Checking the Uniswap v4 route." : quote ? "Open the review to inspect price impact and fees." : "The route is calculated before you approve the swap."}</StatusMessage>}
        <Button icon={ArrowLeftRight} disabled={!quote || quoting || Number(amount) > Number(balances[from])} onClick={() => { setProgress("review"); setReview(true); }}>Review Swap</Button>
      </Card>
      <Sheet open={review} title="Review Swap" locked={progress === "approving" || progress === "swapping"} onClose={closeReview} footer={progress === "review" ? <><Button variant="soft" onClick={closeReview}>Cancel</Button><Button icon={ArrowLeftRight} onClick={() => void execute()}>Proceed</Button></> : progress === "confirmed" || progress === "rejected" ? <Button onClick={closeReview}>Done</Button> : null}>
        {progress === "review" && quote ? <div class="review-content"><Card className="review-summary">{[["Account", shortAddress(activeWallet?.evmAddress || "")], ["Network", evmNetwork?.name || ""], ["Swap", `${amount} ${from} to ${quote.amountOut} ${to}`], ["Minimum received", `${quote.minimumOut} ${to}`], ["Route", quote.route], ["Pool liquidity", quote.liquidityUsd == null ? "Unavailable" : formatUsd(quote.liquidityUsd)], ["Price impact", quote.priceImpactLabel]].map(([label, value]) => <div><span>{label}</span><strong>{value}</strong></div>)}</Card><section class="fee-panel"><div class="section-label"><span>Network fee</span><small>Ethereum</small></div><div class="fee-options"><button class={gasMode === "market" ? "active" : ""} onClick={() => setGasMode("market")}><strong>Market</strong><small>{quote.gasFeeEth} ETH</small></button><button class={gasMode === "fast" ? "active" : ""} onClick={() => setGasMode("fast")}><strong>Fast</strong><small>{formatGasFee(quote, 125)} ETH</small></button><button class={gasMode === "custom" ? "active" : ""} onClick={() => setGasMode("custom")}><strong>Custom</strong><small>{customGas ? `${customGas} Gwei` : "Set gas"}</small></button></div>{gasMode === "custom" ? <label class="field"><span>Gas price (Gwei)</span><input value={customGas} onInput={(event) => setCustomGas(event.currentTarget.value)} inputMode="decimal" /></label> : null}</section>{quote.liquidityUsd != null && quote.liquidityUsd < 25_000 ? <StatusMessage kind="warning" title="Low pool liquidity">Price movement may be larger than expected. Review minimum received.</StatusMessage> : null}</div> : <div class="progress-content"><TransactionStepper steps={["Token approval", "Swap", "Confirmed"]} active={progress === "approving" ? 0 : progress === "swapping" ? 1 : progress === "confirmed" ? 3 : 1} failed={progress === "rejected"} />{progressLabel ? <StatusMessage kind={progress === "rejected" ? "error" : "info"} title={progressLabel}>{quoteError || "Keep this window open while the network confirms each step."}</StatusMessage> : null}{txHash ? <Card className="hash-card"><div><span>Transaction hash</span><strong>{shortAddress(txHash, 10)}</strong></div></Card> : null}{progress === "confirmed" && explorer ? <ExternalButton url={explorer}>View on Explorer</ExternalButton> : null}</div>}
      </Sheet>
    </div>
  );
}

function SwapField({ label, asset, amount, balance, onAsset, options, onAmount, readOnly = false }: {
  label: string; asset: string; amount: string; balance: string; onAsset: (value: string) => void; options: string[];
  onAmount?: (value: string) => void; readOnly?: boolean;
}) {
  return <div class="swap-field"><div><span>{label}</span><small>Balance {balance} {asset}</small></div><div><input value={amount} readOnly={readOnly} onInput={(event) => onAmount?.(event.currentTarget.value)} inputMode="decimal" /><label><TokenIcon symbol={asset} size={28} /><select value={asset} onChange={(event) => onAsset(event.currentTarget.value)}>{options.map((item) => <option value={item}>{item}</option>)}</select></label></div></div>;
}

function ActionButton({ icon: Icon, label, onClick }: { icon: typeof ArrowUpRight; label: string; onClick: () => void }) {
  return <button class="action-button" onClick={onClick}><span><Icon size={19} /></span><strong>{label}</strong></button>;
}

function BottomNav() {
  const { tab, setTab } = useAppState();
  const items = [
    { id: "overview" as const, label: "Overview", icon: LayoutGrid },
    { id: "assets" as const, label: "Assets", icon: Blocks },
    { id: "trade" as const, label: "Trade", icon: ArrowLeftRight }
  ];
  return <nav class="bottom-nav" aria-label="Primary navigation">{items.map(({ id, label, icon: Icon }) => <button type="button" aria-current={tab === id ? "page" : undefined} class={tab === id ? "active" : ""} onClick={() => setTab(id)} key={id}><Icon size={18} /><span>{label}</span><i /></button>)}</nav>;
}

function WalletDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { vault, activeWallet, updateVault, addWallet, notify, navigate, walletIdentityNames } = useAppState();
  const [renaming, setRenaming] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [dragged, setDragged] = useState<number | null>(null);
  if (!vault || !activeWallet) return null;
  const walletCount = vault.wallets.length;

  async function createAdditional() {
    try { await addWallet(await generateWalletRecord(walletCount)); } catch (cause) { notify(cause instanceof Error ? cause.message : "Wallet creation failed.", "error"); }
  }

  return (
    <div class={`drawer-layer ${open ? "open" : ""}`} onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <aside class="wallet-drawer" role="dialog" aria-modal="true" aria-label="Wallets">
        <header><WalletCards size={19} /><h2>Wallets</h2><IconButton icon={X} label="Close" onClick={onClose} /></header>
        <div class="wallet-list">
          {vault.wallets.map((wallet, index) => (
            <article class={`wallet-item ${wallet.address === activeWallet.address ? "active" : ""}`} draggable onDragStart={() => setDragged(index)} onDragOver={(event) => event.preventDefault()} onDrop={() => {
              if (dragged == null || dragged === index) return;
              void updateVault((current) => { const wallets = [...current.wallets]; const [moved] = wallets.splice(dragged, 1); wallets.splice(index, 0, moved); return { ...current, wallets }; });
              setDragged(null);
            }} key={wallet.address}>
              <div class="wallet-main" role="button" aria-label={`Switch to ${wallet.name}`} aria-current={wallet.address === activeWallet.address ? "true" : undefined} tabIndex={0} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") void updateVault((current) => ({ ...current, activeAddress: wallet.address })).then(onClose); }} onClick={() => void updateVault((current) => ({ ...current, activeAddress: wallet.address })).then(onClose)}>
                <span class="wallet-number">{index + 1}</span>
                <span class="wallet-details"><span><strong>{wallet.name}</strong><button class="bare-icon" aria-label={`Rename ${wallet.name}`} title="Rename wallet" onClick={(event) => { event.stopPropagation(); setRenaming(wallet.address); setName(wallet.name); }}><Pencil size={13} /></button></span><small>{shortAddress(wallet.address)}{walletIdentityNames[wallet.address] ? ` · ${walletIdentityNames[wallet.address]}` : ""}</small></span>
                {wallet.address === activeWallet.address ? <Check class="wallet-check" size={16} /> : null}
              </div>
              <span class="wallet-type">{walletTypeLabel(wallet)}</span>
              <GripVertical class="drag-handle" size={16} />
              {vault.wallets.length > 1 ? <button class="delete-wallet" aria-label={`Delete ${wallet.name}`} title="Delete wallet" onClick={() => void updateVault((current) => ({ ...current, wallets: current.wallets.filter((item) => item.address !== wallet.address), activeAddress: current.activeAddress === wallet.address ? current.wallets.find((item) => item.address !== wallet.address)!.address : current.activeAddress }))}><Trash2 size={14} /></button> : null}
              {renaming === wallet.address ? <div class="rename-row"><input aria-label="Wallet name" value={name} onInput={(event) => setName(event.currentTarget.value)} autoFocus /><Button compact variant="soft" onClick={() => setRenaming(null)}>Cancel</Button><Button compact onClick={() => void updateVault((current) => ({ ...current, wallets: current.wallets.map((item) => item.address === wallet.address ? { ...item, name: name.trim().slice(0, 32) || "Wallet" } : item) })).then(() => setRenaming(null))}>Save</Button></div> : null}
            </article>
          ))}
        </div>
        <footer><Button icon={Plus} onClick={() => void createAdditional()}>Create</Button><Button icon={ArrowDownToLine} variant="soft" onClick={() => { onClose(); navigate({ name: "wallets" }); }}>Import</Button></footer>
      </aside>
    </div>
  );
}

function walletTypeLabel(wallet: WalletRecord): string {
  if (wallet.type === "generated") return "Generated";
  if (wallet.type === "imported-mnemonic") return "Imported (Seed)";
  if (wallet.type === "imported-evm-private-key") return "Imported (EVM Key)";
  return "Imported (Key)";
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 6 }).format(value);
}

function formatUsd(value: number): string {
  return new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
}

function CandleChart({ candles, loading }: { candles: TradeCandle[]; loading: boolean }) {
  if (!candles.length) return <div class="candle-chart"><span class="chart-placeholder">{loading ? "Loading chart" : "Chart unavailable"}</span></div>;
  const width = 320;
  const height = 142;
  const plotRight = 272;
  const padding = 8;
  const low = Math.min(...candles.map((item) => item.low));
  const high = Math.max(...candles.map((item) => item.high));
  const span = Math.max(Number.EPSILON, high - low);
  const xStep = (plotRight - padding * 2) / candles.length;
  const y = (price: number) => padding + (high - price) / span * (height - padding * 2);
  const last = candles[candles.length - 1];
  return <div class="candle-chart">
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label="wOCT candlestick chart">
      {[0, 0.5, 1].map((point) => <line x1={padding} x2={plotRight} y1={padding + point * (height - padding * 2)} y2={padding + point * (height - padding * 2)} class="chart-grid" />)}
      {candles.map((candle, index) => {
        const center = padding + xStep * index + xStep / 2;
        const open = y(candle.open);
        const close = y(candle.close);
        const up = candle.close >= candle.open;
        return <g class={up ? "candle-up" : "candle-down"} key={candle.timestamp}>
          <line x1={center} x2={center} y1={y(candle.high)} y2={y(candle.low)} />
          <rect x={center - Math.max(1.5, xStep * 0.28)} y={Math.min(open, close)} width={Math.max(3, xStep * 0.56)} height={Math.max(2, Math.abs(close - open))} />
        </g>;
      })}
      <line x1={padding} x2={plotRight} y1={y(last.close)} y2={y(last.close)} class="chart-last-line" />
      <text x={width - 4} y={Math.max(11, Math.min(height - 4, y(last.close) + 3))} text-anchor="end" class="chart-price">${formatChartPrice(last.close)}</text>
    </svg>
  </div>;
}

function formatChartPrice(value: number): string { return value < 0.01 ? value.toFixed(6) : value.toFixed(4); }

function formatGasFee(quote: TradeQuote, percent: number): string {
  const wei = quote.gasEstimate * quote.gasPrice * BigInt(percent) / 100n;
  const whole = Number(wei) / 1e18;
  return whole.toFixed(8).replace(/0+$/, "").replace(/\.$/, "");
}

function formatDate(timestamp: number): string {
  if (!Number.isFinite(timestamp) || timestamp <= 0) return "Time unavailable";
  const date = new Date(timestamp > 10_000_000_000 ? timestamp : timestamp * 1000);
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" }) + " " + date.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit", hour12: false });
}

function capitalize(value: string): string { return value.charAt(0).toUpperCase() + value.slice(1); }
