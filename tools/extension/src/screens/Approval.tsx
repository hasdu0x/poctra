import { useEffect, useMemo, useState } from "preact/hooks";
import { AlertTriangle, Check, FileSignature, Globe2, Send, ShieldCheck, X } from "lucide-preact";
import { useAppState } from "../app/app-state";
import { Button, Card, Header, StatusMessage, TransactionStepper, WalletAvatar } from "../components/ui";
import { approvalKey, type ApprovalRequest } from "../core/approval";
import { bytesToHex, formatUnits, parseUnits, utf8 } from "../core/encoding";
import { buildSignedOctraTransaction, fetchOctraBalance, fetchRecommendedFee, submitOctraContractCall, submitOctraTransfer, submitSignedOctraTransaction } from "../core/octra-rpc";
import { signOctraBytes } from "../core/wallet";

export function ApprovalScreen() {
  const app = useAppState();
  const [request, setRequest] = useState<ApprovalRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const data = useMemo(() => normalizeRequest(request), [request]);

  useEffect(() => {
    const id = new URLSearchParams(location.search).get("request");
    if (!id || typeof chrome === "undefined") { setError("Approval request is missing."); setLoading(false); return; }
    void chrome.storage.session.get(approvalKey(id)).then((value) => {
      setRequest(value[approvalKey(id)] as ApprovalRequest || null);
      setLoading(false);
    });
  }, []);

  if (loading) return <div class="approval-loading"><ShieldCheck size={25} /><span>Loading approval</span></div>;
  if (!request || !app.activeWallet || !app.octraNetwork) return <main class="approval-screen"><StatusMessage kind="error" title={error || "Approval expired"}>Return to the dApp and create a new request.</StatusMessage></main>;

  async function respond(approved: boolean, result?: unknown, message?: string) {
    await chrome.runtime.sendMessage({ type: "poctra-approval-result", id: request!.id, approved, result, error: message });
  }

  async function approve() {
    if (!request || !app.activeWallet || !app.octraNetwork) return;
    setProcessing(true); setError("");
    try {
      let result: unknown;
      if (request.method === "octra_requestAccounts") {
        const siteName = safeHost(request.origin);
        const existing = app.vault?.settings.connectedSites.find((site) => site.origin === request.origin)?.permissions || [];
        const permissions = [...new Set([...existing, ...requestedPermissions(request)])];
        await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, connectedSites: [...vault.settings.connectedSites.filter((site) => site.origin !== request.origin), { origin: request.origin, name: siteName, permissions, connectedAt: Date.now(), lastUsedAt: Date.now() }] } }));
        result = [app.activeWallet.address];
      } else if (request.method === "octra_switchNetwork") {
        const networkId = String((request.params?.[0] as Record<string, unknown> | undefined)?.networkId || "");
        const target = app.vault?.settings.octraNetworks.find((network) => network.id === networkId || `octra:${network.networkType}` === networkId || network.networkType === networkId);
        if (!target) throw new Error("Requested Octra network is not configured.");
        await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, activeOctraNetworkId: target.id } }));
        result = octraNetworkInfo(target);
      } else if (request.method === "evm_switchChain") {
        const chainId = Number((request.params?.[0] as Record<string, unknown> | undefined)?.chainId);
        const target = app.vault?.settings.evmNetworks.find((network) => network.chainId === chainId);
        if (!target) throw new Error(`EVM chain ${chainId} is not configured.`);
        await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, activeEvmNetworkId: target.id } }));
        result = target;
      } else if (request.method === "octra_signMessage") {
        const message = String(data.message || "");
        if (!message) throw new Error("The dApp supplied an empty message.");
        result = { address: app.activeWallet.address, publicKey: app.activeWallet.publicKeyHex, signature: bytesToHex(signOctraBytes(app.activeWallet, utf8(message))) };
      } else if (request.method === "octra_sendTransaction") {
        const fee = data.feeRaw > 0n ? data.feeRaw : await fetchRecommendedFee(app.octraNetwork, "standard");
        const transaction = await submitOctraTransfer({ network: app.octraNetwork, wallet: app.activeWallet, recipient: data.to, amount: formatUnits(data.amountRaw, 6), feeOu: fee, message: data.message || undefined });
        result = transactionResult(transaction.hash, app.octraNetwork.explorerUrl, transaction.status); setSubmitted(true);
      } else if (request.method === "octra_signTransaction") {
        const balance = await fetchOctraBalance(app.octraNetwork, app.activeWallet.address);
        const nonce = data.nonce > 0 ? data.nonce : balance.nonce + 1;
        const fee = data.feeRaw > 0n ? data.feeRaw : await fetchRecommendedFee(app.octraNetwork, "standard");
        result = buildSignedOctraTransaction({ wallet: app.activeWallet, recipient: data.to, amountRaw: data.amountRaw, nonce, feeOu: fee, message: data.message || undefined, opType: "standard" });
      } else if (request.method === "octra_submitTransaction") {
        if (!data.signedTransaction) throw new Error("A signed transaction is required.");
        const transaction = await submitSignedOctraTransaction(app.octraNetwork, data.signedTransaction);
        result = transactionResult(transaction.hash, app.octraNetwork.explorerUrl, transaction.status); setSubmitted(true);
      } else if (request.method === "octra_sendContractTransaction") {
        const fee = data.feeRaw > 0n ? data.feeRaw : await fetchRecommendedFee(app.octraNetwork, "call");
        const transaction = await submitOctraContractCall({ network: app.octraNetwork, wallet: app.activeWallet, contractAddress: data.to, method: data.contractMethod, params: data.contractParams, amountRaw: data.amountRaw, feeOu: fee });
        result = transactionResult(transaction.hash, app.octraNetwork.explorerUrl, transaction.status); setSubmitted(true);
      } else if (request.method.startsWith("evm_")) {
        if (!app.evmNetwork) throw new Error("The active EVM network is unavailable.");
        const evm = await import("../core/evm-rpc");
        if (request.method === "evm_sendTransaction") {
          const hash = await evm.sendEvmProviderTransaction({ network: app.evmNetwork, wallet: app.activeWallet, to: data.evmTo, value: data.evmValue, data: data.evmData, gasLimit: data.evmGasLimit });
          result = { hash, chainId: app.evmNetwork.chainId }; setSubmitted(true);
        } else if (request.method === "evm_signMessage") {
          const signature = await evm.signEvmMessage(app.activeWallet, data.message);
          result = { signature, address: app.activeWallet.evmAddress };
        } else if (request.method === "evm_signTypedData") {
          if (!data.typedData) throw new Error("Typed data is missing.");
          const signature = await evm.signEvmTypedData(app.activeWallet, data.typedData);
          result = { signature, address: app.activeWallet.evmAddress };
        } else if (request.method === "evm_transferToken") {
          const hash = await evm.transferEvmTokenRaw({ network: app.evmNetwork, wallet: app.activeWallet, token: data.token, to: data.evmTo, amountRaw: data.evmAmountRaw });
          result = { hash, chainId: app.evmNetwork.chainId }; setSubmitted(true);
        } else if (request.method === "evm_approveToken") {
          const hash = await evm.approveEvmTokenRaw({ network: app.evmNetwork, wallet: app.activeWallet, token: data.token, spender: data.spender, amountRaw: data.evmAmountProvided ? data.evmAmountRaw : undefined });
          result = { hash, chainId: app.evmNetwork.chainId }; setSubmitted(true);
        }
      }
      await respond(true, result);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Approval failed."); setProcessing(false); }
  }

  const title = approvalTitle(request.method);
  const Icon = request.method === "octra_requestAccounts" ? Globe2 : request.method === "octra_signMessage" ? FileSignature : Send;
  const isEvm = request.method.startsWith("evm_");
  return <main class="approval-screen"><Header title={title} subtitle="Review this request before approving." /><div class="approval-content"><div class="approval-origin"><span><Globe2 size={17} /></span><div><strong>{safeHost(request.origin)}</strong><small>{request.origin}</small></div></div><div class="approval-account"><WalletAvatar label={app.snapshot?.identityName || app.activeWallet.name} /><div><strong>{app.snapshot?.identityName || app.activeWallet.name}</strong><small>{isEvm ? app.activeWallet.evmAddress : app.activeWallet.address}</small></div></div>{submitted ? <div class="progress-content"><TransactionStepper steps={["Approved", "Submitted", "Return to dApp"]} active={3} /><StatusMessage kind="success" title="Request submitted">The result has been returned to the requesting dApp.</StatusMessage></div> : <><Card className="approval-summary"><div><span>Request</span><strong><Icon size={14} />{methodLabel(request.method)}</strong></div><div><span>Network</span><strong>{isEvm ? app.evmNetwork?.name : app.octraNetwork.name}</strong></div>{request.method.includes("switch") || request.method.includes("Switch") ? <div><span>Requested network</span><strong>{data.requestedNetwork}</strong></div> : null}{request.method.includes("signMessage") ? <div class="approval-message"><span>Message</span><pre>{data.message}</pre></div> : null}{request.method === "evm_signTypedData" ? <div class="approval-message"><span>Typed data</span><pre>{safeJson(data.typedData)}</pre></div> : null}{request.method === "octra_requestAccounts" ? <div><span>Permissions</span><strong>{requestedPermissions(request).join(", ")}</strong></div> : null}{request.method.includes("Transaction") ? <><div><span>To</span><strong>{isEvm ? data.evmTo : data.to}</strong></div><div><span>Amount</span><strong>{isEvm ? `${data.evmValue || "0"} ${app.evmNetwork?.symbol}` : `${formatUnits(data.amountRaw, 6)} OCT`}</strong></div>{!isEvm ? <div><span>Network fee</span><strong>{data.feeRaw > 0n ? `${formatUnits(data.feeRaw, 6)} OCT` : "Auto"}</strong></div> : null}{isEvm && data.evmGasLimit ? <div><span>Gas limit</span><strong>{data.evmGasLimit}</strong></div> : null}{isEvm && data.evmData !== "0x" ? <div class="approval-message"><span>Calldata</span><pre>{data.evmData}</pre></div> : null}{data.contractMethod ? <><div><span>Method</span><strong>{data.contractMethod}</strong></div><div class="approval-message"><span>Parameters</span><pre>{safeJson(data.contractParams)}</pre></div></> : null}</> : null}{request.method === "evm_transferToken" || request.method === "evm_approveToken" ? <><div><span>Token</span><strong>{data.token}</strong></div><div><span>{request.method === "evm_transferToken" ? "Recipient" : "Spender"}</span><strong>{request.method === "evm_transferToken" ? data.evmTo : data.spender}</strong></div><div><span>Amount raw</span><strong>{data.evmAmountProvided ? data.evmAmountRaw.toString() : "Unlimited"}</strong></div></> : null}</Card><StatusMessage kind="warning" title="Only approve trusted requests"><AlertTriangle size={13} /> Verify the site and every value shown above.</StatusMessage>{error ? <StatusMessage kind="error" title={error} /> : null}</>}</div><footer class="approval-footer"><Button variant="soft" icon={X} disabled={processing} onClick={() => void respond(false, undefined, "User rejected the request.")}>Reject</Button><Button icon={Check} disabled={processing || submitted} onClick={() => void approve()}>{processing ? "Processing" : "Approve"}</Button></footer></main>;
}

function normalizeRequest(request: ApprovalRequest | null) {
  const first = request?.params?.[0];
  const value = first && typeof first === "object" ? first as Record<string, unknown> : {};
  const signed = value.tx && typeof value.tx === "object" ? value.tx as Record<string, unknown> : null;
  const rawAmount = signed ? safeBigInt(signed.amount) : value.amountOu != null ? safeBigInt(value.amountOu) : safeOctUnits(value.amount);
  const rawFee = signed ? safeBigInt(signed.ou) : value.ou != null ? safeBigInt(value.ou) : safeOctUnits(value.fee);
  const nested = value.call && typeof value.call === "object" ? value.call as Record<string, unknown> : value;
  return {
    to: String(signed?.to_ || value.to || value.to_ || value.address || nested.address || ""),
    amountRaw: rawAmount,
    feeRaw: rawFee,
    nonce: Number(signed?.nonce || value.nonce || 0),
    message: typeof first === "string" ? first : String(value.message || ""),
    contractMethod: String(value.method || nested.method || ""),
    contractParams: Array.isArray(value.params) ? value.params : Array.isArray(nested.params) ? nested.params : [],
    signedTransaction: signed,
    evmTo: String(value.to || ""),
    evmValue: String(value.value || "0"),
    evmData: String(value.data || "0x"),
    evmGasLimit: value.gasLimit == null ? undefined : String(value.gasLimit),
    token: String(value.token || ""),
    spender: String(value.spender || ""),
    evmAmountRaw: safeBigInt(value.amount),
    evmAmountProvided: value.amount != null,
    typedData: value.domain && value.types && value.value ? { domain: value.domain as Record<string, unknown>, types: value.types as Record<string, Array<{ name: string; type: string }>>, value: value.value as Record<string, unknown> } : null,
    requestedNetwork: String(value.networkId || value.chainId || "")
  };
}
function safeBigInt(value: unknown): bigint { try { return BigInt(String(value || 0)); } catch { return 0n; } }
function safeOctUnits(value: unknown): bigint { try { return parseUnits(String(value || "0"), 6); } catch { return 0n; } }
function safeHost(origin: string): string { try { return new URL(origin).hostname; } catch { return origin; } }
function methodLabel(method: string): string { return method === "octra_requestAccounts" ? "Account access" : method.includes("switch") || method.includes("Switch") ? "Network switch" : method.includes("sign") || method.includes("Sign") ? "Signature" : method.includes("Contract") ? "Contract transaction" : method.startsWith("evm_") ? "EVM request" : "OCT transfer"; }
function approvalTitle(method: string): string { return method === "octra_requestAccounts" ? "Connect dApp" : method.includes("switch") || method.includes("Switch") ? "Switch Network" : method.includes("sign") || method.includes("Sign") ? "Sign Request" : method.includes("Contract") ? "Contract Call" : method.startsWith("evm_") ? "EVM Request" : "Send Transaction"; }
function requestedPermissions(request: ApprovalRequest): string[] { const first = request.params?.[0] as Record<string, unknown> | undefined; const requested = Array.isArray(first?.permissions) ? first.permissions.map(String) : []; const allowed = new Set(["read_address", "read_balance", "read_public_key", "sign_messages", "send_transactions", "contract_calls"]); const valid = requested.filter((permission) => allowed.has(permission)); return valid.length ? valid : ["read_address", "read_balance", "send_transactions"]; }
function octraNetworkInfo(network: { id: string; name: string; networkType: string; rpcUrl: string; explorerUrl: string }) { return { id: `octra:${network.networkType}`, name: network.name, rpcUrl: network.rpcUrl, explorerUrl: network.explorerUrl, supportsPrivacy: false, isTestnet: network.networkType !== "mainnet" }; }
function transactionResult(hash: string, explorerUrl: string, status = "accepted") { return { hash, accepted: status !== "rejected", status: status === "confirmed" ? "confirmed" : "pending", explorerUrl: `${explorerUrl}/tx.html?hash=${hash}` }; }
function safeJson(value: unknown): string { try { return JSON.stringify(value, null, 2); } catch { return "Unable to display structured data."; } }
