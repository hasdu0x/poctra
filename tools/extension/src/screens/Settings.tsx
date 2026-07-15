import { useState } from "preact/hooks";
import type { ComponentChildren } from "preact";
import {
  BookUser,
  Check,
  ChevronRight,
  CircleHelp,
  Download,
  ExternalLink,
  FileText,
  Globe2,
  KeyRound,
  Link2,
  LockKeyhole,
  Moon,
  Network,
  PieChart,
  Plus,
  Radio,
  ShieldCheck,
  Sun,
  Trash2,
  WalletCards,
  Wifi
} from "lucide-preact";
import { useAppState } from "../app/app-state";
import { Button, Card, EmptyState, Header, IconButton, SecretField, StatusMessage, TokenIcon } from "../components/ui";
import { inspectErc20, inspectNft } from "../core/evm-rpc";
import { shortAddress } from "../core/encoding";
import { normalizeExplorerUrl, normalizeRpcUrl, verifyEvmRpc } from "../core/rpc-security";
import { loadVaultEnvelope } from "../core/storage";
import type { AddressBookEntry, EvmNetwork, IdentityProvider, OctraNetwork, WalletRecord } from "../core/types";
import { unlockVault } from "../core/vault";
import { generateWalletRecord, importBase64Key, importEvmHexKey, importMnemonic } from "../core/wallet";

export function SettingsScreen() {
  const app = useAppState();
  const theme = app.vault?.settings.theme || "dark";
  const items = [
    { title: "Wallets", subtitle: "Manage and import accounts", icon: WalletCards, route: { name: "wallets" } as const },
    { title: "Backup Keys", subtitle: "Reveal recovery keys securely", icon: LockKeyhole, route: { name: "backup" } as const },
    { title: "Portfolio", subtitle: "Unified asset value and history", icon: PieChart, route: { name: "portfolio" } as const },
    { title: "Activity Center", subtitle: "Octra and EVM transaction timeline", icon: Radio, route: { name: "activity" } as const },
    { title: "Security Center", subtitle: "Review wallet security checks", icon: ShieldCheck, route: { name: "security-center" } as const },
    { title: "Recovery Plan", subtitle: "Prepare and verify account recovery", icon: KeyRound, route: { name: "recovery-plan" } as const },
    { title: "Approvals", subtitle: "Review known ERC20 allowances", icon: Check, route: { name: "approvals" } as const },
    { title: "RPC Management", subtitle: "Octra networks and explorers", icon: Wifi, route: { name: "rpc" } as const },
    { title: "EVM Networks", subtitle: "Ethereum and custom networks", icon: Network, route: { name: "evm-networks" } as const },
    { title: "Address Book", subtitle: "Saved and recent recipients", icon: BookUser, route: { name: "address-book" } as const },
    { title: "Octra Identity Provider", subtitle: "Resolve .oct names", icon: Globe2, route: { name: "identity" } as const },
    { title: "Connected dApps", subtitle: "Review granted permissions", icon: Link2, route: { name: "connected-sites" } as const },
    { title: "About", subtitle: "poctra alpha v1.0.0", icon: CircleHelp, route: { name: "about" } as const }
  ];
  return <Route><Header title="Settings" subtitle="Wallet, networks, and security." onBack={app.back} /><div class="screen-content settings-list">{items.map(({ title, subtitle, icon: Icon, route }) => <button class="settings-row" onClick={() => app.navigate(route)}><span><Icon size={17} /></span><div><strong>{title}</strong><small>{subtitle}</small></div><ChevronRight size={15} /></button>)}<button class="settings-row theme-row" onClick={() => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, theme: theme === "dark" ? "light" : "dark" } }))}><span>{theme === "dark" ? <Moon size={17} /> : <Sun size={17} />}</span><div><strong>{theme === "dark" ? "Dark Mode" : "Light Mode"}</strong><small>Tap the card to switch appearance</small></div><i class={`toggle ${theme === "dark" ? "on" : ""}`} /></button><Button variant="soft" icon={LockKeyhole} onClick={() => void app.lock()}>Lock Wallet</Button></div></Route>;
}

export function WalletManagementScreen() {
  const app = useAppState();
  const [method, setMethod] = useState<"mnemonic" | "base64" | "evm" | null>(null);
  const [value, setValue] = useState("");
  const [error, setError] = useState("");
  if (!app.vault || !app.activeWallet) return null;
  async function importNow() {
    try {
      const wallet = method === "mnemonic" ? importMnemonic(value, `Wallet ${app.vault!.wallets.length + 1}`) : method === "base64" ? importBase64Key(value, `Wallet ${app.vault!.wallets.length + 1}`) : importEvmHexKey(value, `Wallet ${app.vault!.wallets.length + 1}`);
      await app.addWallet(wallet); setValue(""); setMethod(null);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Import failed."); }
  }
  return <Route><Header title="Wallets" subtitle="Manage accounts secured by this password." onBack={app.back} /><div class="screen-content manage-wallets">{app.vault.wallets.map((wallet, index) => <Card className={wallet.address === app.activeWallet?.address ? "managed-wallet active" : "managed-wallet"}><span class="wallet-number">{index + 1}</span><div><strong>{wallet.name}</strong><small>{shortAddress(wallet.address)}</small><em>{walletType(wallet)}</em></div>{wallet.address === app.activeWallet?.address ? <Check size={16} /> : <button onClick={() => void app.updateVault((vault) => ({ ...vault, activeAddress: wallet.address }))}>Switch</button>}</Card>)}<div class="wallet-management-actions"><Button icon={Plus} onClick={() => void generateWalletRecord(app.vault!.wallets.length).then(app.addWallet)}>Create Wallet</Button><Button icon={Download} variant="soft" onClick={() => setMethod(method ? null : "mnemonic")}>Import Wallet</Button></div>{method ? <Card className="import-wallet-card"><div class="mini-methods"><button class={method === "mnemonic" ? "active" : ""} onClick={() => setMethod("mnemonic")}><FileText size={15} />Mnemonic</button><button class={method === "base64" ? "active" : ""} onClick={() => setMethod("base64")}><KeyRound size={15} />Base64</button><button class={method === "evm" ? "active" : ""} onClick={() => setMethod("evm")}><TokenIcon symbol="ETH" size={20} />EVM key</button></div><label class="field"><span>{method === "mnemonic" ? "Mnemonic phrase" : method === "base64" ? "Base64 private key" : "Ethereum private key"}</span><textarea rows={method === "mnemonic" ? 4 : 2} value={value} onInput={(event) => setValue(event.currentTarget.value)} class="masked-text" /></label>{error ? <StatusMessage kind="error" title={error} /> : null}<Button icon={Download} disabled={!value.trim()} onClick={() => void importNow()}>Import</Button></Card> : null}</div></Route>;
}

export function RpcManagementScreen() {
  const app = useAppState();
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ name: "", rpcUrl: "", networkType: "mainnet" as "mainnet" | "devnet", explorerUrl: "" });
  if (!app.vault) return null;
  async function add() {
    try {
      if (!form.name.trim()) throw new Error("Enter an RPC name.");
      const network: OctraNetwork = { ...form, name: form.name.trim(), id: `octra-${Date.now()}`, rpcUrl: normalizeRpcUrl(form.rpcUrl), explorerUrl: normalizeExplorerUrl(form.explorerUrl) };
      await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, octraNetworks: [...vault.settings.octraNetworks, network], activeOctraNetworkId: network.id } }));
      setAdding(false);
    } catch (cause) { app.notify(cause instanceof Error ? cause.message : "Unable to add RPC network.", "error"); }
  }
  return <Route><Header title="RPC Management" subtitle="Select the active Octra network." onBack={app.back} /><div class="screen-content network-list">{app.vault.settings.octraNetworks.map((network) => <NetworkRow active={network.id === app.vault?.settings.activeOctraNetworkId} title={network.name} subtitle={`${capitalize(network.networkType)} · ${network.rpcUrl}`} onClick={() => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, activeOctraNetworkId: network.id } }))} onDelete={network.id.startsWith("octra-") && !["octra-mainnet", "octra-devnet"].includes(network.id) ? () => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, octraNetworks: vault.settings.octraNetworks.filter((item) => item.id !== network.id) } })) : undefined} />)}{adding ? <NetworkForm form={form} setForm={setForm} onSave={() => void add()} /> : <Button icon={Plus} variant="soft" onClick={() => setAdding(true)}>Add RPC Network</Button>}</div></Route>;
}

export function EvmNetworksScreen() {
  const app = useAppState();
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ name: "", chainId: "", symbol: "ETH", rpcUrl: "", explorerUrl: "" });
  if (!app.vault) return null;
  async function add() {
    try {
      const chainId = Number(form.chainId);
      if (!form.name.trim() || !Number.isSafeInteger(chainId) || chainId <= 0) throw new Error("Enter a network name and valid chain ID.");
      const rpcUrl = normalizeRpcUrl(form.rpcUrl);
      await verifyEvmRpc(rpcUrl, chainId);
      const network: EvmNetwork = { id: `evm-${Date.now()}`, name: form.name.trim(), chainId, symbol: form.symbol.trim().toUpperCase() || "ETH", rpcUrl, explorerUrl: normalizeExplorerUrl(form.explorerUrl), testnet: chainId !== 1 };
      await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, evmNetworks: [...vault.settings.evmNetworks, network], activeEvmNetworkId: network.id } }));
      setAdding(false);
    } catch (cause) { app.notify(cause instanceof Error ? cause.message : "Unable to add EVM network.", "error"); }
  }
  return <Route><Header title="EVM Networks" subtitle="Selecting a network activates it immediately." onBack={app.back} /><div class="screen-content network-list">{app.vault.settings.evmNetworks.map((network) => <NetworkRow active={network.id === app.vault?.settings.activeEvmNetworkId} title={network.name} subtitle={`Chain ${network.chainId} · ${network.symbol}`} detail={network.rpcUrl} onClick={() => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, activeEvmNetworkId: network.id } }))} onDelete={network.id.startsWith("evm-") && !network.id.startsWith("eth-") ? () => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, evmNetworks: vault.settings.evmNetworks.filter((item) => item.id !== network.id) } })) : undefined} />)}{adding ? <EvmNetworkForm form={form} setForm={setForm} onSave={() => void add()} /> : <Button icon={Plus} variant="soft" onClick={() => setAdding(true)}>Add Custom Network</Button>}</div></Route>;
}

export function IdentityProviderScreen() {
  const app = useAppState();
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  if (!app.vault || !app.octraNetwork) return null;
  const providers = app.vault.settings.identityProviders.filter((item) => item.networkType === app.octraNetwork?.networkType);
  async function add() {
    if (!address.startsWith("oct") || address.length !== 47) { app.notify("Enter a valid provider contract address.", "error"); return; }
    const provider: IdentityProvider = { id: `identity-${Date.now()}`, name: name.trim() || "Octra Identity", contractAddress: address, networkType: app.octraNetwork!.networkType };
    await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, identityProviders: [...vault.settings.identityProviders, provider], activeIdentityProviderByNetwork: { ...vault.settings.activeIdentityProviderByNetwork, [provider.networkType]: provider.id } } })); setAdding(false);
  }
  return <Route><Header title="Octra Identity Provider" subtitle={`Providers for ${app.octraNetwork.name}.`} onBack={app.back} /><div class="screen-content network-list">{providers.map((provider) => <NetworkRow active={provider.id === app.vault?.settings.activeIdentityProviderByNetwork[app.octraNetwork!.networkType]} title={provider.name} subtitle={shortAddress(provider.contractAddress, 10)} onClick={() => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, activeIdentityProviderByNetwork: { ...vault.settings.activeIdentityProviderByNetwork, [provider.networkType]: provider.id } } }))} onDelete={provider.id.startsWith("identity-") && !["identity-mainnet", "identity-devnet"].includes(provider.id) ? () => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, identityProviders: vault.settings.identityProviders.filter((item) => item.id !== provider.id) } })) : undefined} />)}{adding ? <Card className="form-card"><label class="field"><span>Provider name</span><input value={name} onInput={(event) => setName(event.currentTarget.value)} /></label><label class="field"><span>Contract address</span><input value={address} onInput={(event) => setAddress(event.currentTarget.value)} /></label><Button onClick={() => void add()}>Add Provider</Button></Card> : <Button icon={Plus} variant="soft" onClick={() => setAdding(true)}>Add Identity Provider</Button>}</div></Route>;
}

export function AddressBookScreen() {
  const app = useAppState();
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [kind, setKind] = useState<"octra" | "evm">("octra");
  if (!app.vault) return null;
  async function add() {
    const valid = kind === "octra" ? address.startsWith("oct") && address.length === 47 : /^0x[0-9a-f]{40}$/i.test(address);
    if (!name.trim() || !valid) { app.notify("Enter a name and valid address.", "error"); return; }
    const entry: AddressBookEntry = { id: `contact-${Date.now()}`, name: name.trim(), address, kind, createdAt: Date.now() };
    await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, addressBook: [...vault.settings.addressBook, entry] } })); setAdding(false); setName(""); setAddress("");
  }
  return <Route><Header title="Address Book" subtitle="Saved recipients for faster transfers." onBack={app.back} actions={<Button compact icon={Plus} variant="soft" onClick={() => setAdding(true)}>Add</Button>} /><div class="screen-content contact-list">{adding ? <Card className="form-card"><div class="segmented"><button class={kind === "octra" ? "active" : ""} onClick={() => setKind("octra")}>Octra</button><button class={kind === "evm" ? "active" : ""} onClick={() => setKind("evm")}>EVM</button></div><label class="field"><span>Contact name</span><input value={name} onInput={(event) => setName(event.currentTarget.value)} /></label><label class="field"><span>Address</span><input value={address} onInput={(event) => setAddress(event.currentTarget.value)} /></label><Button onClick={() => void add()}>Save Contact</Button></Card> : null}{app.vault.settings.addressBook.length ? app.vault.settings.addressBook.map((entry) => <Card className="contact-row"><span class={`network-mark ${entry.kind}`}>{entry.kind === "octra" ? "O" : "E"}</span><div><strong>{entry.name}</strong><small>{shortAddress(entry.address)}</small></div><IconButton icon={Trash2} label="Delete" onClick={() => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, addressBook: vault.settings.addressBook.filter((item) => item.id !== entry.id) } }))} /></Card>) : <EmptyState>No saved recipients</EmptyState>}</div></Route>;
}

export function BackupKeysScreen() {
  const app = useAppState();
  const [password, setPassword] = useState("");
  const [revealed, setRevealed] = useState<WalletRecord | null>(null);
  const [error, setError] = useState("");
  if (!app.activeWallet) return null;
  async function reveal() {
    try { const envelope = await loadVaultEnvelope(); if (!envelope) throw new Error("Wallet vault was not found."); const unlocked = await unlockVault(envelope, password); setRevealed(unlocked.vault.wallets.find((wallet) => wallet.address === app.activeWallet!.address) || null); setPassword(""); } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to reveal keys."); }
  }
  return <Route><Header title="Backup Keys" subtitle="Password verification is required to reveal keys." onBack={app.back} /><div class="screen-content backup-content"><StatusMessage kind="warning" title="Sensitive information">Never share these keys. Anyone with them can control both Octra and derived EVM funds.</StatusMessage>{!revealed ? <Card className="password-card"><label class="field"><span>Wallet password</span><input type="password" value={password} onInput={(event) => setPassword(event.currentTarget.value)} /></label>{error ? <StatusMessage kind="error" title={error} /> : null}<Button icon={ShieldCheck} disabled={!password} onClick={() => void reveal()}>Reveal Keys</Button></Card> : <><SecretField label="Mnemonic phrase" value={revealed.mnemonic || "Not available for this wallet type"} multiline /><SecretField label="Octra Private Key (Base64)" value={revealed.privateKeyBase64} /><SecretField label="EVM Private Key (Hex)" value={`0x${Array.from(atob(revealed.privateKeyBase64), (character) => character.charCodeAt(0).toString(16).padStart(2, '0')).join('')}`} /><Card className="wallet-info"><div><span>Wallet name</span><strong>{revealed.name}</strong></div><div><span>Octra address</span><strong>{shortAddress(revealed.address, 10)}</strong></div><div><span>EVM address</span><strong>{shortAddress(revealed.evmAddress, 10)}</strong></div><div><span>Wallet type</span><strong>{walletType(revealed)}</strong></div></Card></>}</div></Route>;
}

export function ConnectedSitesScreen() { const app = useAppState(); const sites = app.vault?.settings.connectedSites || []; return <Route><Header title="Connected dApps" subtitle="Sites currently allowed to see this account." onBack={app.back} /><div class="screen-content connected-list">{sites.length ? sites.map((site) => <Card className="connected-site"><span><Globe2 size={17} /></span><div><strong>{site.name}</strong><small>{site.origin}</small><em>{site.permissions.length} permissions</em></div><Button compact variant="danger" onClick={() => void app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, connectedSites: vault.settings.connectedSites.filter((item) => item.origin !== site.origin) } }))}>Revoke</Button></Card>) : <EmptyState>No connected dApps</EmptyState>}</div></Route>; }

export function AddAssetScreen({ kind }: { kind: "octra" | "evm" }) {
  const app = useAppState(); const [address, setAddress] = useState(""); const [metadata, setMetadata] = useState<{ name: string; symbol: string; decimals: number } | null>(null); const [error, setError] = useState("");
  async function inspect() { try { if (kind === "evm") { if (!app.evmNetwork || !app.activeWallet) return; const value = await inspectErc20(app.evmNetwork, address, app.activeWallet.evmAddress); setMetadata(value); } else { if (!address.startsWith("oct") || address.length !== 47) throw new Error("Enter a valid Octra contract address."); setMetadata({ name: "Octra asset", symbol: "TOKEN", decimals: 6 }); } } catch (cause) { setError(cause instanceof Error ? cause.message : "Asset lookup failed."); } }
  async function add() { if (!metadata) return; await app.updateVault((vault) => kind === "evm" ? ({ ...vault, settings: { ...vault.settings, evmAssets: [...vault.settings.evmAssets.filter((item) => item.contractAddress.toLowerCase() !== address.toLowerCase()), { contractAddress: address, ...metadata, chainId: app.evmNetwork!.chainId }] } }) : ({ ...vault, settings: { ...vault.settings, octraAssets: [...vault.settings.octraAssets.filter((item) => item.contractAddress !== address), { contractAddress: address, ...metadata }] } })); app.back(); }
  return <Route><Header title={kind === "evm" ? "Add ERC20" : "Add Octra Asset"} subtitle="Verify the contract before adding it." onBack={app.back} /><div class="screen-content form-page"><label class="field"><span>Contract address</span><input value={address} onInput={(event) => { setAddress(event.currentTarget.value); setMetadata(null); }} /></label><Button variant="soft" onClick={() => void inspect()}>Inspect Contract</Button>{metadata ? <Card className="asset-preview"><TokenIcon symbol={metadata.symbol} /><div><strong>{metadata.name}</strong><small>{metadata.symbol} · {metadata.decimals} decimals</small></div></Card> : null}{error ? <StatusMessage kind="error" title={error} /> : null}<Button disabled={!metadata} onClick={() => void add()}>Add Asset</Button></div></Route>;
}

export function AddNftScreen() {
  const app = useAppState();
  const [contractAddress, setContractAddress] = useState("");
  const [tokenId, setTokenId] = useState("");
  const [preview, setPreview] = useState<Awaited<ReturnType<typeof inspectNft>> | null>(null);
  const [error, setError] = useState("");
  async function inspect() {
    try {
      if (!app.evmNetwork || !app.activeWallet) return;
      setError("");
      setPreview(await inspectNft(app.evmNetwork, contractAddress.trim(), tokenId.trim(), app.activeWallet.evmAddress));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "NFT lookup failed."); setPreview(null); }
  }
  async function add() {
    if (!preview) return;
    await app.updateVault((vault) => ({ ...vault, settings: { ...vault.settings, evmNfts: [...(vault.settings.evmNfts || []).filter((item) => !(item.contractAddress.toLowerCase() === preview.contractAddress.toLowerCase() && item.tokenId === preview.tokenId && item.chainId === preview.chainId)), preview] } }));
    app.back();
  }
  return <Route><Header title="Add NFT" subtitle="Add an NFT owned by the active EVM account." onBack={app.back} /><div class="screen-content form-page"><label class="field"><span>Contract address</span><input value={contractAddress} onInput={(event) => { setContractAddress(event.currentTarget.value); setPreview(null); }} placeholder="0x..." /></label><label class="field"><span>Token ID</span><input value={tokenId} onInput={(event) => { setTokenId(event.currentTarget.value); setPreview(null); }} inputMode="numeric" placeholder="0" /></label><Button variant="soft" onClick={() => void inspect()} disabled={!/^0x[0-9a-f]{40}$/i.test(contractAddress) || !/^\d+$/.test(tokenId)}>Inspect NFT</Button>{preview ? <Card className="asset-preview"><TokenIcon symbol="NFT" /><div><strong>{preview.name}</strong><small>{preview.symbol} #{preview.tokenId}</small></div></Card> : null}{error ? <StatusMessage kind="error" title={error} /> : null}<Button disabled={!preview} onClick={() => void add()}>Add NFT</Button></div></Route>;
}

export function AboutScreen() { const app = useAppState(); return <Route><Header title="About" subtitle="poctra extension information." onBack={app.back} /><div class="screen-content about-screen"><img src="/assets/poctra.svg" alt="" /><h2>poctra</h2><p>pocket octra</p><Card className="about-data"><div><span>Version</span><strong>Alpha v1.0.0</strong></div><div><span>Octra network</span><strong>{app.octraNetwork?.name}</strong></div><div><span>EVM network</span><strong>{app.evmNetwork?.name}</strong></div><div><span>Octra account</span><strong>{shortAddress(app.activeWallet?.address || "")}</strong></div><div><span>EVM account</span><strong>{shortAddress(app.activeWallet?.evmAddress || "")}</strong></div></Card><Button variant="soft" icon={ExternalLink} onClick={() => window.open("https://m-tq.github.io/poctra/", "_blank", "noopener")}>Poctra Website</Button></div></Route>; }

function NetworkRow({ active, title, subtitle, detail, onClick, onDelete }: { active: boolean; title: string; subtitle: string; detail?: string; onClick: () => void; onDelete?: () => void }) { return <Card className={`network-row ${active ? "active" : ""}`}><button onClick={onClick}><span class="radio">{active ? <Check size={13} /> : null}</span><div><strong>{title}</strong><small>{subtitle}</small>{detail ? <em>{detail}</em> : null}</div></button>{onDelete ? <IconButton icon={Trash2} label="Delete" onClick={onDelete} /> : null}</Card>; }
type OctraNetworkForm = { name: string; rpcUrl: string; networkType: "mainnet" | "devnet"; explorerUrl: string };
type CustomEvmNetworkForm = { name: string; chainId: string; symbol: string; rpcUrl: string; explorerUrl: string };
function NetworkForm({ form, setForm, onSave }: { form: OctraNetworkForm; setForm: (form: OctraNetworkForm) => void; onSave: () => void }) { return <Card className="form-card"><label class="field"><span>RPC name</span><input value={form.name} onInput={(event) => setForm({ ...form, name: event.currentTarget.value })} /></label><label class="field"><span>RPC endpoint URL</span><input value={form.rpcUrl} onInput={(event) => setForm({ ...form, rpcUrl: event.currentTarget.value })} /></label><label class="field"><span>Network type</span><select value={form.networkType} onChange={(event) => setForm({ ...form, networkType: event.currentTarget.value as "mainnet" | "devnet" })}><option value="mainnet">Mainnet</option><option value="devnet">Devnet</option></select></label><label class="field"><span>Explorer URL</span><input value={form.explorerUrl} onInput={(event) => setForm({ ...form, explorerUrl: event.currentTarget.value })} /></label><Button onClick={onSave}>Add Network</Button></Card>; }
function EvmNetworkForm({ form, setForm, onSave }: { form: CustomEvmNetworkForm; setForm: (form: CustomEvmNetworkForm) => void; onSave: () => void }) { return <Card className="form-card"><label class="field"><span>Network name</span><input value={form.name} onInput={(event) => setForm({ ...form, name: event.currentTarget.value })} /></label><label class="field"><span>Chain ID</span><input value={form.chainId} onInput={(event) => setForm({ ...form, chainId: event.currentTarget.value })} inputMode="numeric" /></label><label class="field"><span>Symbol</span><input value={form.symbol} onInput={(event) => setForm({ ...form, symbol: event.currentTarget.value })} /></label><label class="field"><span>RPC URL</span><input value={form.rpcUrl} onInput={(event) => setForm({ ...form, rpcUrl: event.currentTarget.value })} /></label><label class="field"><span>Explorer URL</span><input value={form.explorerUrl} onInput={(event) => setForm({ ...form, explorerUrl: event.currentTarget.value })} /></label><Button onClick={onSave}>Add Network</Button></Card>; }
function Route({ children }: { children: ComponentChildren }) { return <main class="route-screen">{children}</main>; }
function walletType(wallet: WalletRecord) { return wallet.type === "generated" ? "Generated" : wallet.type === "imported-mnemonic" ? "Imported (Mnemonic)" : wallet.type === "imported-evm-private-key" ? "Imported (EVM Key)" : "Imported (Key)"; }
function capitalize(value: string) { return value.charAt(0).toUpperCase() + value.slice(1); }
