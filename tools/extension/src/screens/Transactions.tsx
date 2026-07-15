import QRCode from "qrcode";
import type { ComponentChildren } from "preact";
import { useEffect, useMemo, useState } from "preact/hooks";
import {
  BookUser,
  ArrowLeftRight,
  Check,
  Copy,
  ExternalLink,
  History,
  Info,
  Maximize2,
  Plus,
  RefreshCw,
  Send,
  X
} from "lucide-preact";
import { useAppState } from "../app/app-state";
import {
  Button,
  Card,
  CopyButton,
  EmptyState,
  ExternalButton,
  Header,
  IconButton,
  NetworkFeeSelector,
  Sheet,
  StatusMessage,
  TokenIcon,
  TransactionStepper,
  WalletAvatar
} from "../components/ui";
import { formatUnits, parseUnits, shortAddress } from "../core/encoding";
import { burnWoct, mintWoct, readLockedEvent, waitForBridgeClaim, waitForIncomingOct } from "../core/bridge";
import { fetchEvmFeeOptions, sendErc20, sendEvmNative, sendNft } from "../core/evm-rpc";
import { isIdentityName, resolveOctraIdentity } from "../core/identity";
import { contractView, fetchOctraTransaction, fetchRecommendedFee, submitOctraContractCall, submitOctraTransfer } from "../core/octra-rpc";
import type { ActivityItem, EvmAsset, TransactionProgress } from "../core/types";

export function SendOctScreen() {
  const app = useAppState();
  const { activeWallet, octraNetwork, identityProvider, snapshot, vault } = app;
  const [recipient, setRecipient] = useState("");
  const [resolved, setResolved] = useState<{ name?: string; address: string } | null>(null);
  const [amount, setAmount] = useState("");
  const [messageOpen, setMessageOpen] = useState(false);
  const [message, setMessage] = useState("");
  const [fee, setFee] = useState(850_000n);
  const [feeMode, setFeeMode] = useState<"auto" | "fast" | "custom">("auto");
  const [customFee, setCustomFee] = useState("");
  const [review, setReview] = useState(false);
  const [progress, setProgress] = useState<TransactionProgress>({ state: "review" });
  const [error, setError] = useState("");
  const [contactsOpen, setContactsOpen] = useState(false);

  useEffect(() => { if (octraNetwork) void fetchRecommendedFee(octraNetwork).then(setFee); }, [octraNetwork?.id]);
  useEffect(() => {
    const clean = recipient.trim();
    if (!clean || !activeWallet || !octraNetwork || !identityProvider || !isIdentityName(clean)) { setResolved(null); return; }
    const timer = window.setTimeout(() => {
      void resolveOctraIdentity(octraNetwork, identityProvider, clean, activeWallet.address)
        .then((address) => setResolved(address ? { name: clean.toLowerCase(), address } : null))
        .catch(() => setResolved(null));
    }, 450);
    return () => window.clearTimeout(timer);
  }, [recipient, activeWallet?.address, octraNetwork?.id, identityProvider?.id]);

  if (!activeWallet || !octraNetwork || !vault) return null;
  const feeOu = selectedFeeOu(fee, feeMode, customFee);
  const available = Math.max(0, (snapshot?.octraBalance.amount || 0) - Number(feeOu) / 1_000_000);
  const destination = resolved?.address || recipient.trim();
  const valid = destination.startsWith("oct") && destination.length === 47 && Number(amount) > 0 && Number(amount) <= available;

  async function proceed() {
    setProgress({ state: "submitting" });
    setError("");
    try {
      const result = await submitOctraTransfer({ network: octraNetwork!, wallet: activeWallet!, recipient: destination, amount, feeOu, message: message.trim() || undefined });
      setProgress({ state: "accepted", hash: result.hash });
      await waitForOctra(octraNetwork!, result.hash, (state) => setProgress({ state, hash: result.hash }));
      await app.refresh(true);
    } catch (cause) {
      const message = errorMessage(cause);
      setError(message);
      setProgress((current) => ({ ...current, state: "rejected", message }));
    }
  }

  return (
    <Screen>
      <Header title="Send OCT" subtitle="Send OCT to an address or Octra Identity." onBack={app.back} />
      <div class="screen-content send-form">
        <label class={`field recipient-field ${resolved ? "resolved" : ""}`}><span>Recipient</span><input value={recipient} onInput={(event) => { setRecipient(event.currentTarget.value); setResolved(null); }} placeholder="foo.oct or Octra address" /><IconButton icon={BookUser} label="Address book" onClick={() => setContactsOpen(true)} /></label>
        {resolved ? <ResolvedRecipient name={resolved.name} address={resolved.address} onClear={() => { setRecipient(""); setResolved(null); }} /> : recipient.trim().startsWith("oct") && recipient.trim().length === 47 ? <ResolvedRecipient address={recipient.trim()} onClear={() => setRecipient("")} /> : null}
        {recipient && !resolved && isIdentityName(recipient) ? <StatusMessage kind="info" title="Identity lookup">Resolving this Octra name.</StatusMessage> : null}
        <label class="field amount-field"><span>Amount</span><input value={amount} onInput={(event) => setAmount(event.currentTarget.value)} inputMode="decimal" placeholder="0.0" /><button onClick={() => setAmount(available.toFixed(6).replace(/0+$/, "").replace(/\.$/, ""))}>Max</button><em>OCT</em></label>
        <div class="balance-note"><span>Available balance</span><strong>{available.toFixed(6)} OCT</strong></div>
        {!messageOpen ? <button class="add-message" onClick={() => setMessageOpen(true)}><Plus size={14} />Add Message</button> : <div class="message-field"><label class="field"><span>Message (optional)</span><textarea rows={3} value={message} onInput={(event) => setMessage(event.currentTarget.value)} maxLength={256} /></label><IconButton icon={X} label="Remove message" onClick={() => { setMessageOpen(false); setMessage(""); }} /></div>}
        <NetworkFeeSelector values={{ auto: `${formatUnits(fee, 6)} OCT`, fast: `${formatUnits(fee * 125n / 100n, 6)} OCT` }} selected={feeMode} onChange={setFeeMode} custom={customFee} onCustom={setCustomFee} />
        {recipient && !valid ? <StatusMessage kind="warning" title="Check transaction fields">Enter a valid recipient and amount within your available balance.</StatusMessage> : null}
      </div>
      <footer class="screen-footer"><Button icon={Send} disabled={!valid} onClick={() => { setProgress({ state: "review" }); setReview(true); }}>Review Send</Button></footer>
      <TransactionReview open={review} title="Review Send" progress={progress} hash={progress.hash} explorerUrl={progress.hash ? `${octraNetwork.explorerUrl}/tx.html?hash=${progress.hash}` : ""} onClose={() => setReview(false)} onProceed={() => void proceed()} error={error} rows={[['From', shortAddress(activeWallet.address)], ['To', resolved?.name || shortAddress(destination)], ['Amount', `${amount || '0'} OCT`], ['Network', octraNetwork.name], ['Network fee', `${formatUnits(feeOu, 6)} OCT`]]} />
      <Sheet open={contactsOpen} title="Address Book" onClose={() => setContactsOpen(false)}>{vault.settings.addressBook.filter((entry) => entry.kind === "octra").length ? <div class="selection-list">{vault.settings.addressBook.filter((entry) => entry.kind === "octra").map((entry) => <button onClick={() => { setRecipient(entry.identityName || entry.address); setResolved({ name: entry.identityName, address: entry.address }); setContactsOpen(false); }}><strong>{entry.name}</strong><small>{entry.identityName || shortAddress(entry.address)}</small></button>)}</div> : <EmptyState>No saved Octra contacts</EmptyState>}</Sheet>
    </Screen>
  );
}

export function SendOctraAssetScreen({ contractAddress }: { contractAddress: string }) {
  const app = useAppState();
  const token = app.snapshot?.octraTokens.find((item) => item.contractAddress === contractAddress);
  const [recipient, setRecipient] = useState("");
  const [resolved, setResolved] = useState<{ name?: string; address: string } | null>(null);
  const [amount, setAmount] = useState("");
  const [fee, setFee] = useState(1_100_000n);
  const [feeMode, setFeeMode] = useState<"auto" | "fast" | "custom">("auto");
  const [customFee, setCustomFee] = useState("");
  const [review, setReview] = useState(false);
  const [progress, setProgress] = useState<TransactionProgress>({ state: "review" });
  const [error, setError] = useState("");
  useEffect(() => { if (app.octraNetwork) void fetchRecommendedFee(app.octraNetwork, "call").then(setFee); }, [app.octraNetwork?.id]);
  useEffect(() => {
    const value = recipient.trim();
    if (!isIdentityName(value) || !app.activeWallet || !app.octraNetwork || !app.identityProvider) { setResolved(null); return; }
    const timer = window.setTimeout(() => void resolveOctraIdentity(app.octraNetwork!, app.identityProvider!, value, app.activeWallet!.address).then((address) => setResolved(address ? { name: value.toLowerCase(), address } : null)).catch(() => setResolved(null)), 450);
    return () => window.clearTimeout(timer);
  }, [recipient, app.activeWallet?.address, app.octraNetwork?.id, app.identityProvider?.id]);
  if (!token || !app.activeWallet || !app.octraNetwork) return <Screen><Header title="Send Asset" onBack={app.back} /><div class="screen-content"><EmptyState>Asset data is unavailable. Refresh Assets and try again.</EmptyState></div></Screen>;
  const destination = resolved?.address || recipient.trim();
  const feeOu = selectedFeeOu(fee, feeMode, customFee);
  const amountRaw = (() => { try { return parseUnits(amount || "0", token.decimals); } catch { return 0n; } })();
  const valid = destination.startsWith("oct") && destination.length === 47 && destination !== app.activeWallet.address && amountRaw > 0n && amountRaw <= token.raw && feeOu > 0n;
  async function proceed() {
    setProgress({ state: "submitting" }); setError("");
    try {
      const transaction = await submitOctraContractCall({ network: app.octraNetwork!, wallet: app.activeWallet!, contractAddress, method: "transfer", params: [destination, amountRaw.toString()], feeOu });
      setProgress({ state: "accepted", hash: transaction.hash });
      await waitForOctra(app.octraNetwork!, transaction.hash, (state) => setProgress({ state, hash: transaction.hash }));
      await app.refresh(true);
    } catch (cause) { const message = errorMessage(cause); setError(message); setProgress((current) => ({ ...current, state: "rejected", message })); }
  }
  return <Screen><Header title={`Send ${token.symbol}`} subtitle={`Transfer ${token.name} on ${app.octraNetwork.name}.`} onBack={app.back} /><div class="screen-content send-form"><Card className="selected-asset"><TokenIcon symbol={token.symbol} /><div><strong>{token.name}</strong><small>{shortAddress(token.contractAddress)}</small></div></Card><label class={`field recipient-field ${resolved ? "resolved" : ""}`}><span>Recipient</span><input value={recipient} onInput={(event) => { setRecipient(event.currentTarget.value); setResolved(null); }} placeholder="foo.oct or Octra address" /></label>{resolved ? <ResolvedRecipient name={resolved.name} address={resolved.address} onClear={() => { setRecipient(""); setResolved(null); }} /> : recipient.startsWith("oct") && recipient.length === 47 ? <ResolvedRecipient address={recipient} onClear={() => setRecipient("")} /> : null}<label class="field amount-field"><span>Amount</span><input value={amount} onInput={(event) => setAmount(event.currentTarget.value)} inputMode="decimal" placeholder="0.0" /><button onClick={() => setAmount(formatUnits(token.raw, token.decimals))}>Max</button><em>{token.symbol}</em></label><div class="balance-note"><span>Available balance</span><strong>{token.formatted} {token.symbol}</strong></div><NetworkFeeSelector values={{ auto: `${formatUnits(fee, 6)} OCT`, fast: `${formatUnits(fee * 125n / 100n, 6)} OCT` }} selected={feeMode} onChange={setFeeMode} custom={customFee} onCustom={setCustomFee} />{error && !review ? <StatusMessage kind="error" title={error} /> : null}</div><footer class="screen-footer"><Button icon={Send} disabled={!valid} onClick={() => { setProgress({ state: "review" }); setReview(true); }}>Review Send</Button></footer><TransactionReview open={review} title={`Review ${token.symbol} Send`} progress={progress} hash={progress.hash} explorerUrl={progress.hash ? `${app.octraNetwork.explorerUrl}/tx.html?hash=${progress.hash}` : ""} onClose={() => setReview(false)} onProceed={() => void proceed()} error={error} rows={[["Asset", token.symbol], ["To", resolved?.name || shortAddress(destination)], ["Amount", `${amount} ${token.symbol}`], ["Network", app.octraNetwork.name], ["Network fee", `${formatUnits(feeOu, 6)} OCT`]]} /></Screen>;
}

export function ReceiveOctScreen() {
  const app = useAppState();
  const [qr, setQr] = useState("");
  const wallet = app.activeWallet;
  useEffect(() => { if (wallet) void QRCode.toDataURL(wallet.address, { width: 280, margin: 1, color: { dark: "#090b09", light: "#ffffff" } }).then(setQr); }, [wallet?.address]);
  if (!wallet) return null;
  return <Screen><Header title="Receive OCT" subtitle="Share your Octra address to receive funds." onBack={app.back} /><div class="screen-content receive-content"><div class="receive-identity"><WalletAvatar label={app.snapshot?.identityName || wallet.name} size={48} /><div><strong>{app.snapshot?.identityName || wallet.name}</strong><small>{app.octraNetwork?.name}</small></div></div>{qr ? <img class="qr-code" src={qr} alt={`QR code for ${wallet.address}`} /> : null}<div class="address-box"><span>{wallet.address}</span><CopyButton value={wallet.address} label="Copy address" /></div><StatusMessage kind="info" title="Octra only">Only send OCT and Octra assets to this address.</StatusMessage></div></Screen>;
}

export function ReceiveEvmScreen() {
  const app = useAppState();
  const [qr, setQr] = useState("");
  const wallet = app.activeWallet;
  useEffect(() => { if (wallet) void QRCode.toDataURL(wallet.evmAddress, { width: 280, margin: 1, color: { dark: "#090b09", light: "#ffffff" } }).then(setQr); }, [wallet?.evmAddress]);
  if (!wallet) return null;
  return <Screen><Header title="Receive EVM" subtitle={`Receive assets on ${app.evmNetwork?.name || "the active EVM network"}.`} onBack={app.back} /><div class="screen-content receive-content"><div class="receive-identity"><WalletAvatar label={wallet.name} size={48} /><div><strong>{wallet.name}</strong><small>{app.evmNetwork?.name}</small></div></div>{qr ? <img class="qr-code" src={qr} alt={`QR code for ${wallet.evmAddress}`} /> : null}<div class="address-box"><span>{wallet.evmAddress}</span><CopyButton value={wallet.evmAddress} label="Copy address" /></div><StatusMessage kind="info" title="Check the active network">Only send assets supported by {app.evmNetwork?.name || "this EVM network"} to this address.</StatusMessage></div></Screen>;
}

export function SendEvmScreen({ tokenAddress }: { tokenAddress?: string }) {
  const app = useAppState();
  const { activeWallet, evmNetwork, snapshot, vault } = app;
  const asset = vault?.settings.evmAssets.find((item) => item.contractAddress.toLowerCase() === tokenAddress?.toLowerCase());
  const [recipient, setRecipient] = useState("");
  const [amount, setAmount] = useState("");
  const [gas, setGas] = useState<{ low: bigint; market: bigint; fast: bigint; gasLimit: bigint } | null>(null);
  const [gasMode, setGasMode] = useState<"auto" | "fast" | "custom">("auto");
  const [customGas, setCustomGas] = useState("");
  const [review, setReview] = useState(false);
  const [progress, setProgress] = useState<TransactionProgress>({ state: "review" });
  const [error, setError] = useState("");
  useEffect(() => { if (evmNetwork) void fetchEvmFeeOptions(evmNetwork, asset ? 80_000n : 21_000n).then(setGas).catch((cause) => setError(errorMessage(cause))); }, [evmNetwork?.id, asset?.contractAddress]);
  if (!activeWallet || !evmNetwork || !vault) return null;
  const symbol = asset?.symbol || evmNetwork.symbol;
  const available = asset ? Number(snapshot?.evmTokens.find((token) => token.contractAddress.toLowerCase() === asset.contractAddress.toLowerCase())?.formatted || 0) : Number(snapshot?.evmBalance || 0);
  const gasPrice = gas ? gasMode === "fast" ? gas.fast : gasMode === "custom" ? safeParseUnits(customGas, 9) : gas.market : 0n;
  const valid = (/^0x[0-9a-f]{40}$/i.test(recipient.trim()) || recipient.trim().toLowerCase().endsWith(".eth")) && Number(amount) > 0 && Number(amount) <= available && gasPrice > 0n;
  async function proceed() {
    setProgress({ state: "submitting" }); setError("");
    try {
      const tx = asset
        ? await sendErc20({ network: evmNetwork!, wallet: activeWallet!, asset, recipient, amount, gasPrice })
        : await sendEvmNative({ network: evmNetwork!, wallet: activeWallet!, recipient, amount, gasPrice });
      setProgress({ state: "accepted", hash: tx.hash });
      setProgress({ state: "confirming", hash: tx.hash });
      const confirmed = await tx.wait();
      if (!confirmed) throw new Error("EVM transaction reverted.");
      setProgress({ state: "confirmed", hash: tx.hash });
      await app.refresh(true);
    } catch (cause) { const message = errorMessage(cause); setError(message); setProgress((current) => ({ ...current, state: "rejected", message })); }
  }
  return <Screen><Header title={`Send ${symbol}`} subtitle={`Send ${symbol} on ${evmNetwork.name}.`} onBack={app.back} /><div class="screen-content send-form">{asset ? <Card className="selected-asset"><TokenIcon symbol={asset.symbol} /><div><strong>{asset.name}</strong><small>{shortAddress(asset.contractAddress)}</small></div></Card> : null}<label class="field"><span>Recipient</span><input value={recipient} onInput={(event) => setRecipient(event.currentTarget.value)} placeholder="foo.eth or 0x address" /></label><label class="field amount-field"><span>Amount</span><input value={amount} onInput={(event) => setAmount(event.currentTarget.value)} inputMode="decimal" placeholder="0.0" /><button onClick={() => setAmount(String(available))}>Max</button><em>{symbol}</em></label><div class="balance-note"><span>Available balance</span><strong>{available} {symbol}</strong></div><section class="fee-panel"><div class="section-label"><span>Network fee</span><small>{evmNetwork.name}</small></div><div class="fee-options"><button class={gasMode === "auto" ? "active" : ""} onClick={() => setGasMode("auto")}><strong>Market</strong><small>{gas ? `${Number(gas.market) / 1e9} Gwei` : "--"}</small></button><button class={gasMode === "fast" ? "active" : ""} onClick={() => setGasMode("fast")}><strong>Fast</strong><small>{gas ? `${Number(gas.fast) / 1e9} Gwei` : "--"}</small></button><button class={gasMode === "custom" ? "active" : ""} onClick={() => setGasMode("custom")}><strong>Custom</strong><small>{customGas ? `${customGas} Gwei` : "Set gas"}</small></button></div>{gasMode === "custom" ? <label class="field"><span>Custom gas (Gwei)</span><input value={customGas} onInput={(event) => setCustomGas(event.currentTarget.value)} inputMode="decimal" /></label> : null}</section>{error && !review ? <StatusMessage kind="error" title={error} action={<Button compact variant="ghost" icon={RefreshCw} onClick={() => void fetchEvmFeeOptions(evmNetwork, asset ? 80_000n : 21_000n).then(setGas)}>Retry</Button>} /> : null}</div><footer class="screen-footer"><Button icon={Send} disabled={!valid} onClick={() => { setProgress({ state: "review" }); setReview(true); }}>Review Send</Button></footer><TransactionReview open={review} title={`Review ${symbol} Send`} progress={progress} hash={progress.hash} explorerUrl={progress.hash ? `${evmNetwork.explorerUrl}/tx/${progress.hash}` : ""} onClose={() => setReview(false)} onProceed={() => void proceed()} error={error} rows={[['From', shortAddress(activeWallet.evmAddress)], ['To', recipient], ['Amount', `${amount} ${symbol}`], ['Network', evmNetwork.name], ['Network fee', gas ? `${formatUnits(gasPrice * gas.gasLimit, 18)} ETH` : '--']]} /></Screen>;
}

export function SendNftScreen({ contractAddress, tokenId }: { contractAddress: string; tokenId: string }) {
  const app = useAppState();
  const nft = app.vault?.settings.evmNfts?.find((item) => item.contractAddress.toLowerCase() === contractAddress.toLowerCase() && item.tokenId === tokenId && item.chainId === app.evmNetwork?.chainId);
  const [recipient, setRecipient] = useState("");
  const [gas, setGas] = useState<{ low: bigint; market: bigint; fast: bigint; gasLimit: bigint } | null>(null);
  const [gasMode, setGasMode] = useState<"auto" | "fast" | "custom">("auto");
  const [customGas, setCustomGas] = useState("");
  const [review, setReview] = useState(false);
  const [progress, setProgress] = useState<TransactionProgress>({ state: "review" });
  const [error, setError] = useState("");
  useEffect(() => { if (app.evmNetwork) void fetchEvmFeeOptions(app.evmNetwork, 120_000n).then(setGas).catch((cause) => setError(errorMessage(cause))); }, [app.evmNetwork?.id]);
  if (!nft || !app.activeWallet || !app.evmNetwork) return <Screen><Header title="Send NFT" onBack={app.back} /><div class="screen-content"><EmptyState>NFT data is unavailable on the active network.</EmptyState></div></Screen>;
  const selectedNft = nft;
  const gasPrice = gas ? gasMode === "fast" ? gas.fast : gasMode === "custom" ? safeParseUnits(customGas, 9) : gas.market : 0n;
  const valid = (/^0x[0-9a-f]{40}$/i.test(recipient.trim()) || recipient.trim().toLowerCase().endsWith(".eth")) && gasPrice > 0n;
  async function proceed() {
    setProgress({ state: "submitting" }); setError("");
    try {
      const transaction = await sendNft({ network: app.evmNetwork!, wallet: app.activeWallet!, nft: selectedNft, recipient, gasPrice });
      setProgress({ state: "accepted", hash: transaction.hash }); setProgress({ state: "confirming", hash: transaction.hash });
      if (!await transaction.wait()) throw new Error("NFT transfer reverted.");
      setProgress({ state: "confirmed", hash: transaction.hash }); await app.refresh(true);
    } catch (cause) { const message = errorMessage(cause); setError(message); setProgress((current) => ({ ...current, state: "rejected", message })); }
  }
  return <Screen><Header title={`Send ${nft.symbol} NFT`} subtitle={`Transfer NFT #${nft.tokenId} on ${app.evmNetwork.name}.`} onBack={app.back} /><div class="screen-content send-form"><Card className="selected-asset"><TokenIcon symbol="NFT" /><div><strong>{nft.name}</strong><small>{nft.symbol} #{nft.tokenId}</small></div></Card><label class="field"><span>Recipient</span><input value={recipient} onInput={(event) => setRecipient(event.currentTarget.value)} placeholder="foo.eth or 0x address" /></label><section class="fee-panel"><div class="section-label"><span>Network fee</span><small>{app.evmNetwork.name}</small></div><div class="fee-options"><button class={gasMode === "auto" ? "active" : ""} onClick={() => setGasMode("auto")}><strong>Market</strong><small>{gas ? `${Number(gas.market) / 1e9} Gwei` : "--"}</small></button><button class={gasMode === "fast" ? "active" : ""} onClick={() => setGasMode("fast")}><strong>Fast</strong><small>{gas ? `${Number(gas.fast) / 1e9} Gwei` : "--"}</small></button><button class={gasMode === "custom" ? "active" : ""} onClick={() => setGasMode("custom")}><strong>Custom</strong><small>{customGas ? `${customGas} Gwei` : "Set gas"}</small></button></div>{gasMode === "custom" ? <label class="field"><span>Gas price (Gwei)</span><input value={customGas} onInput={(event) => setCustomGas(event.currentTarget.value)} inputMode="decimal" /></label> : null}</section>{error && !review ? <StatusMessage kind="error" title={error} /> : null}</div><footer class="screen-footer"><Button icon={Send} disabled={!valid} onClick={() => { setProgress({ state: "review" }); setReview(true); }}>Review Send</Button></footer><TransactionReview open={review} title="Review NFT Send" progress={progress} hash={progress.hash} explorerUrl={progress.hash ? `${app.evmNetwork.explorerUrl}/tx/${progress.hash}` : ""} onClose={() => setReview(false)} onProceed={() => void proceed()} error={error} rows={[["NFT", `${nft.symbol} #${nft.tokenId}`], ["Contract", shortAddress(nft.contractAddress)], ["To", recipient], ["Network", app.evmNetwork.name], ["Network fee", gas ? `${formatUnits(gasPrice * gas.gasLimit, 18)} ETH` : "--"]]} /></Screen>;
}

export function BridgeScreen() {
  const app = useAppState();
  const { activeWallet, octraNetwork, evmNetwork, snapshot } = app;
  const [direction, setDirection] = useState<"oct-to-woct" | "woct-to-oct">("oct-to-woct");
  const [amount, setAmount] = useState("");
  const [status, setStatus] = useState<{ paused: boolean | null; lock: string; unlock: string }>({ paused: null, lock: "--", unlock: "--" });
  const [loading, setLoading] = useState(false);
  const [review, setReview] = useState(false);
  const [progress, setProgress] = useState<TransactionProgress>({ state: "review" });
  const [activeStep, setActiveStep] = useState(-1);
  const [explorerUrl, setExplorerUrl] = useState("");
  const [error, setError] = useState("");
  const bridgeContract = "oct5MrNfjiXFNRDLwsodn8Zm9hDKNGAYt3eQDCQ52bSpCHq";
  async function refreshStatus() {
    if (!activeWallet || !octraNetwork) return;
    setLoading(true);
    const [paused, lock, unlock] = await Promise.all([
      contractView(octraNetwork, bridgeContract, "is_paused", [], activeWallet.address).catch(() => null),
      contractView(octraNetwork, bridgeContract, "daily_lock_remaining", [], activeWallet.address).catch(() => null),
      contractView(octraNetwork, bridgeContract, "daily_unlock_remaining", [], activeWallet.address).catch(() => null)
    ]);
    setStatus({ paused: paused == null ? null : paused === "1" || paused === "true", lock: lock ? formatUnits(BigInt(lock), 6) : "--", unlock: unlock ? formatUnits(BigInt(unlock), 6) : "--" });
    setLoading(false);
  }
  useEffect(() => { void refreshStatus(); }, [activeWallet?.address, octraNetwork?.id]);
  if (!activeWallet || !octraNetwork || !evmNetwork) return null;
  const mainnets = octraNetwork.networkType === "mainnet" && evmNetwork.chainId === 1;
  const available = direction === "oct-to-woct" ? snapshot?.octraBalance.amount || 0 : Number(snapshot?.evmTokens.find((token) => token.symbol === "wOCT")?.formatted || 0);
  async function proceed() {
    setProgress({ state: "submitting" }); setActiveStep(0); setError(""); setExplorerUrl("");
    try {
      if (direction === "oct-to-woct") {
        const fee = await fetchRecommendedFee(octraNetwork!, "call");
        const result = await submitOctraContractCall({ network: octraNetwork!, wallet: activeWallet!, contractAddress: bridgeContract, method: "lock_to_eth", params: [activeWallet!.evmAddress], amountRaw: parseUnits(amount, 6), feeOu: fee });
        setProgress({ state: "accepted", hash: result.hash }); setExplorerUrl(`${octraNetwork!.explorerUrl}/tx.html?hash=${result.hash}`);
        await waitForOctra(octraNetwork!, result.hash, (state) => setProgress({ state, hash: result.hash }));
        const detail = await fetchOctraTransaction(octraNetwork!, result.hash);
        const locked = await readLockedEvent(octraNetwork!, result.hash, Number(detail?.epoch || 0));
        setActiveStep(1); setProgress({ state: "confirming", hash: result.hash });
        const calldata = await waitForBridgeClaim({ locked });
        setActiveStep(2);
        const mintHash = await mintWoct({ network: evmNetwork!, wallet: activeWallet!, calldata });
        setExplorerUrl(`${evmNetwork!.explorerUrl}/tx/${mintHash}`); setProgress({ state: "confirmed", hash: mintHash }); setActiveStep(3);
      } else {
        const balanceBefore = snapshot?.octraBalance.raw || 0n;
        const burned = await burnWoct({ network: evmNetwork!, wallet: activeWallet!, recipient: activeWallet!.address, amountRaw: parseUnits(amount, 6), onStage: (stage, hash) => { setActiveStep(stage === "approve" ? 0 : 1); setProgress({ state: stage === "approve" ? "accepted" : "confirming", hash }); if (hash) setExplorerUrl(`${evmNetwork!.explorerUrl}/tx/${hash}`); } });
        setProgress({ state: "confirming", hash: burned.burnHash }); setActiveStep(2);
        await waitForIncomingOct({ network: octraNetwork!, address: activeWallet!.address, balanceBefore });
        setProgress({ state: "confirmed", hash: burned.burnHash }); setActiveStep(3);
      }
      await app.refresh(true);
    } catch (cause) { const message = errorMessage(cause); setError(message); setProgress({ state: "rejected", message }); }
  }
  const bridgeSteps = direction === "oct-to-woct" ? ["Lock OCT", "Wait header", "Mint wOCT"] : ["Approve wOCT", "Burn wOCT", "Wait incoming OCT"];
  return <Screen><Header title="Bridge" subtitle="Move OCT between Octra and Ethereum wOCT." onBack={app.back} actions={<><IconButton icon={Info} label="Bridge info" /><IconButton icon={History} label="History" /><IconButton icon={RefreshCw} label="Refresh" className={loading ? "is-spinning" : ""} onClick={() => void refreshStatus()} /></>} /><div class="screen-content bridge-content"><div class="bridge-hero"><img src="/assets/bridge.svg" alt="" /><h2>Octra Bridge</h2><p>Move OCT between Octra and Ethereum wOCT.</p></div><Card className="network-summary"><div><TokenIcon symbol="OCT" size={34} /><span><small>Octra</small><strong>{octraNetwork.name}</strong></span><button onClick={() => app.navigate({ name: "rpc" })}>Change</button></div><div><TokenIcon symbol="ETH" size={34} /><span><small>EVM</small><strong>{evmNetwork.name}</strong></span><button onClick={() => app.navigate({ name: "evm-networks" })}>Change</button></div></Card><div class="bridge-status"><span class={status.paused === false ? "open" : "closed"}><i />{status.paused === false ? "Open" : status.paused ? "Closed" : "Unknown"}</span><span>Lock Left <strong>{status.lock}</strong></span><span>Unlock Left <strong>{status.unlock}</strong></span></div><div class="direction-tabs"><button class={direction === "oct-to-woct" ? "active" : ""} onClick={() => setDirection("oct-to-woct")}><Check size={14} />OCT to wOCT<small>Octra to Ethereum</small></button><button class={direction === "woct-to-oct" ? "active" : ""} onClick={() => setDirection("woct-to-oct")}><ArrowLeftRight size={14} />wOCT to OCT<small>Ethereum to Octra</small></button></div><label class="field amount-field"><span>Amount {direction === "oct-to-woct" ? "OCT" : "wOCT"}</span><input value={amount} onInput={(event) => setAmount(event.currentTarget.value)} inputMode="decimal" placeholder="0.0" /><button onClick={() => setAmount(String(available))}>Max</button></label><Card className="bridge-recipient"><TokenIcon symbol={direction === "oct-to-woct" ? "ETH" : "OCT"} size={34} /><div><small>Receive {direction === "oct-to-woct" ? "wOCT to" : "OCT to"}</small><strong>{shortAddress(direction === "oct-to-woct" ? activeWallet.evmAddress : activeWallet.address)}</strong></div></Card>{!mainnets ? <StatusMessage kind="warning" title="Mainnet required">Change both Octra and EVM networks to Mainnet before bridging.</StatusMessage> : null}{error && !review ? <StatusMessage kind="error" title={error} /> : null}</div><footer class="screen-footer"><Button icon={ArrowLeftRight} disabled={!mainnets || status.paused !== false || Number(amount) <= 0 || Number(amount) > available} onClick={() => { setProgress({ state: "review" }); setActiveStep(-1); setReview(true); }}>Bridge to {direction === "oct-to-woct" ? "wOCT" : "OCT"}</Button></footer><TransactionReview open={review} title="Review Bridge" progress={progress} hash={progress.hash} explorerUrl={explorerUrl} steps={bridgeSteps} activeStep={activeStep} onClose={() => setReview(false)} onProceed={() => void proceed()} error={error} rows={[["Route", direction === "oct-to-woct" ? "OCT to wOCT" : "wOCT to OCT"], ["Amount", `${amount} ${direction === "oct-to-woct" ? "OCT" : "wOCT"}`], ["Octra recipient", shortAddress(activeWallet.address)], ["EVM recipient", shortAddress(activeWallet.evmAddress)], ["Estimate", direction === "oct-to-woct" ? "About 3-8 minutes" : "About 2-6 minutes"]]} /></Screen>;
}

export function ActivityScreen() {
  const app = useAppState();
  const activity = useMemo(() => [...(app.snapshot?.octraActivity || []), ...(app.snapshot?.evmActivity || [])].sort((a, b) => b.timestamp - a.timestamp), [app.snapshot]);
  return <Screen><Header title="Activity Center" subtitle="Octra and EVM activity in one timeline." onBack={app.back} actions={<IconButton icon={RefreshCw} label="Refresh" className={app.refreshing ? "is-spinning" : ""} onClick={() => void app.refresh(true)} />} /><div class="screen-content activity-center">{activity.length ? activity.map((item) => <button class="center-activity" onClick={() => app.navigate({ name: "transaction", item })}><span class={`network-mark ${item.network}`}>{item.network === "octra" ? "O" : "E"}</span><div><strong>{item.from.toLowerCase() === (item.network === "octra" ? app.activeWallet?.address : app.activeWallet?.evmAddress)?.toLowerCase() ? "Sent" : "Received"}</strong><small>{item.network === "octra" ? app.octraNetwork?.name : app.evmNetwork?.name}</small><em>{shortAddress(item.hash)}</em></div><span><strong>{item.amount} {item.symbol}</strong><small>{item.status}</small></span></button>) : <EmptyState>No activity on the active networks</EmptyState>}</div></Screen>;
}

export function TransactionDetailsScreen({ item }: { item: ActivityItem }) {
  const app = useAppState();
  const outgoing = item.from.toLowerCase() === (item.network === "octra" ? app.activeWallet?.address : app.activeWallet?.evmAddress)?.toLowerCase();
  const explorer = item.network === "octra" ? `${app.octraNetwork?.explorerUrl}/tx.html?hash=${item.hash}` : `${app.evmNetwork?.explorerUrl}/tx/${item.hash}`;
  return <Screen><Header title="Transaction Details" subtitle={`${capitalize(item.network)} transaction`} onBack={app.back} /><div class="screen-content transaction-detail"><div class={`tx-status ${item.status}`}><Check size={24} /><h2>{capitalize(item.status)}</h2><p>{outgoing ? "Sent" : "Received"} {item.amount} {item.symbol}</p></div><Card className="detail-card">{[['Type', outgoing ? 'Sent' : 'Received'], ['Operation', item.opType], ['From', item.from], ['To', item.to], ['Amount', `${item.amount} ${item.symbol}`], ['Network', item.network === 'octra' ? app.octraNetwork?.name || 'Octra' : app.evmNetwork?.name || 'EVM']].map(([label, value]) => <div><span>{label}</span><strong>{value}</strong></div>)}</Card><Card className="hash-card"><div><span>Transaction hash</span><strong>{item.hash}</strong></div><CopyButton value={item.hash} label="Copy hash" /></Card><div class="inline-actions"><ExternalButton url={explorer}>View on Explorer</ExternalButton></div></div></Screen>;
}

function TransactionReview({ open, title, rows, progress, hash, explorerUrl, error, steps, activeStep, onClose, onProceed }: {
  open: boolean; title: string; rows: string[][]; progress: TransactionProgress; hash?: string; explorerUrl: string; error: string; steps?: string[]; activeStep?: number; onClose: () => void; onProceed: () => void;
}) {
  const running = !["review", "confirmed", "rejected"].includes(progress.state);
  const index = progress.state === "review" ? -1 : progress.state === "submitting" ? 0 : progress.state === "accepted" || progress.state === "confirming" ? 1 : progress.state === "confirmed" ? 3 : 1;
  return <Sheet open={open} title={title} onClose={onClose} locked={running} footer={progress.state === "review" ? <><Button variant="soft" onClick={onClose}>Cancel</Button><Button onClick={onProceed}>Proceed</Button></> : progress.state === "confirmed" || progress.state === "rejected" ? <Button onClick={onClose}>Done</Button> : null}>{progress.state === "review" ? <div class="review-content"><Card className="review-summary">{rows.map(([label, value]) => <div><span>{label}</span><strong>{value}</strong></div>)}</Card><StatusMessage kind="warning" title="Verify transaction">Review the recipient, amount, network, and fee before proceeding.</StatusMessage></div> : <div class="progress-content"><TransactionStepper steps={steps || ["Submit transaction", "Network accepted", "Confirmed"]} active={activeStep == null ? index : activeStep} failed={progress.state === "rejected"} />{error ? <StatusMessage kind="error" title={error} /> : null}{hash ? <Card className="hash-card"><div><span>Transaction hash</span><strong>{shortAddress(hash, 10)}</strong></div><CopyButton value={hash} /></Card> : null}{progress.state === "confirmed" && explorerUrl ? <ExternalButton url={explorerUrl}>View on Explorer</ExternalButton> : null}</div>}</Sheet>;
}

function ResolvedRecipient({ name, address, onClear }: { name?: string; address: string; onClear: () => void }) { return <div class="resolved-card"><WalletAvatar label={name || address} /><div><strong>{name || "Octra address"}</strong><small>{shortAddress(address)}</small></div><IconButton icon={X} label="Change recipient" onClick={onClear} /></div>; }

function Screen({ children }: { children: ComponentChildren }) { return <main class="route-screen">{children}</main>; }
function selectedFeeOu(base: bigint, mode: "auto" | "fast" | "custom", custom: string): bigint { if (mode === "fast") return base * 125n / 100n; if (mode === "custom") { try { return parseUnits(custom || "0", 6); } catch { return 0n; } } return base; }
function safeParseUnits(value: string, decimals: number): bigint { try { return parseUnits(value || "0", decimals); } catch { return 0n; } }
async function waitForOctra(network: NonNullable<ReturnType<typeof useAppState>["octraNetwork"]>, hash: string, update: (state: TransactionProgress["state"]) => void) { update("confirming"); for (let attempt = 0; attempt < 100; attempt += 1) { const tx = await fetchOctraTransaction(network, hash); const status = String(tx?.status || "").toLowerCase(); if (status === "rejected" || status === "dropped") throw new Error(String(tx?.reason || "Transaction rejected by the node.")); if (status === "confirmed" || tx?.epoch != null || tx?.stage_status === "confirmed") { update("confirmed"); return; } await new Promise((resolve) => window.setTimeout(resolve, 3000)); } throw new Error("Transaction remains pending. Check it again from Activity."); }
function errorMessage(cause: unknown): string { return cause instanceof Error ? cause.message : "Transaction failed."; }
function capitalize(value: string): string { return value.charAt(0).toUpperCase() + value.slice(1); }
