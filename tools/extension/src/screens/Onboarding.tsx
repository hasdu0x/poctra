import { useState } from "preact/hooks";
import {
  ArrowLeft,
  Check,
  ChevronRight,
  Download,
  Eye,
  EyeOff,
  FileText,
  KeyRound,
  LockKeyhole,
  Plus,
  ShieldCheck,
  WalletCards
} from "lucide-preact";
import { useAppState } from "../app/app-state";
import { Button, IconButton, SecretField, StatusMessage } from "../components/ui";
import type { WalletRecord } from "../core/types";
import { generateWalletRecord, importBase64Key, importEvmHexKey, importMnemonic } from "../core/wallet";

type Step = "welcome" | "create" | "import" | "password";
type ImportMethod = "mnemonic" | "base64" | "evm";

const importOptions: Array<{
  id: ImportMethod;
  title: string;
  shortTitle: string;
  description: string;
  icon: typeof FileText;
}> = [
  { id: "mnemonic", title: "Mnemonic phrase", shortTitle: "Mnemonic", description: "Use a 12 or 24 word seed phrase.", icon: FileText },
  { id: "base64", title: "Private Key (Base64)", shortTitle: "Octra key", description: "Use an Octra-compatible Base64 encoded 32-byte key.", icon: KeyRound },
  { id: "evm", title: "Ethereum Private Key", shortTitle: "EVM key", description: "Use a 0x hex key and derive an Octra wallet from it.", icon: WalletCards }
];

export function OnboardingScreen() {
  const { createFirstWallet, notify } = useAppState();
  const [step, setStep] = useState<Step>("welcome");
  const [wallet, setWallet] = useState<WalletRecord | null>(null);
  const [method, setMethod] = useState<ImportMethod | null>(null);
  const [input, setInput] = useState("");
  const [visible, setVisible] = useState(false);
  const [backedUp, setBackedUp] = useState(false);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function createWallet() {
    setBusy(true);
    setError("");
    try {
      setWallet(await generateWalletRecord());
      setStep("create");
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  function importWallet() {
    if (!method) return;
    setError("");
    try {
      const imported = method === "mnemonic"
        ? importMnemonic(input)
        : method === "base64"
          ? importBase64Key(input)
          : importEvmHexKey(input);
      setWallet(imported);
      setStep("password");
      notify("Valid Octra address derived.", "success");
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }

  async function secureWallet() {
    if (!wallet) return;
    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await createFirstWallet(wallet, password);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  if (step === "welcome") {
    return (
      <main class="onboarding welcome-screen onboarding-transition" key={step}>
        <div class="welcome-brand">
          <img src="/assets/poctra.svg" alt="" />
          <h1>poctra</h1>
          <p>pocket octra</p>
        </div>
        {error ? <StatusMessage kind="error" title={error} /> : null}
        <div class="welcome-actions">
          <Button icon={Plus} onClick={() => void createWallet()} disabled={busy}>{busy ? "Creating wallet" : "Create Wallet"}</Button>
          <Button icon={Download} variant="soft" onClick={() => setStep("import")}>Import Wallet</Button>
        </div>
      </main>
    );
  }

  if (step === "create" && wallet) {
    return (
      <main class="onboarding flow-screen onboarding-transition" key={step}>
        <FlowHeader title="Secure your wallet" subtitle="Back up these keys before continuing." onBack={() => setStep("welcome")} />
        <div class="flow-content">
          <div class="security-hero"><ShieldCheck size={34} /><h2>Your keys, your wallet</h2><p>Store this recovery information offline. Anyone with it can control your funds.</p></div>
          <SecretField label="Mnemonic phrase" value={wallet.mnemonic || ""} multiline />
          <SecretField label="Private Key (Base64)" value={wallet.privateKeyBase64} />
          <label class="check-row"><input type="checkbox" checked={backedUp} onChange={(event) => setBackedUp(event.currentTarget.checked)} /><span><Check size={13} />I have backed up my wallet</span></label>
        </div>
        <footer class="sticky-actions"><Button onClick={() => setStep("password")} disabled={!backedUp}>Continue</Button></footer>
      </main>
    );
  }

  if (step === "import") {
    return (
      <main class="onboarding flow-screen onboarding-transition" key={`${step}:${method || "select"}`}>
        <FlowHeader title="Import Wallet" subtitle="Recover an existing Octra account." onBack={() => method ? (setMethod(null), setInput(""), setError("")) : setStep("welcome")} />
        <div class="flow-content import-content">
          <div class={`import-hero ${method ? "compact" : ""}`}><Download size={method ? 20 : 34} /><h2>Recover your wallet</h2>{!method ? <p>Poctra validates the derived Octra address before security setup.</p> : null}</div>
          <div class={`import-options ${method ? "has-selection" : ""}`}>
            {importOptions.filter((option) => !method || option.id !== method).map((option) => {
              const Icon = option.icon;
              return (
                <button type="button" class="import-option" onClick={() => { setMethod(option.id); setInput(""); setError(""); }} key={option.id}>
                  <span><Icon size={19} /></span><div><strong>{method ? option.shortTitle : option.title}</strong>{!method ? <small>{option.description}</small> : null}</div><ChevronRight size={15} />
                </button>
              );
            })}
          </div>
          {method ? (
            <div class="import-entry motion-rise">
              {(() => {
                const selected = importOptions.find((option) => option.id === method)!;
                const SelectedIcon = selected.icon;
                return <div class="selected-import-method"><span><SelectedIcon size={18} /></span><div><strong>{selected.title}</strong><small>{selected.description}</small></div></div>;
              })()}
              <label class="field secret-input">
                <span>{importOptions.find((option) => option.id === method)?.title}</span>
                {method === "mnemonic"
                  ? <textarea rows={5} value={input} onInput={(event) => setInput(event.currentTarget.value)} class={visible ? "" : "masked-text"} autoComplete="off" spellcheck={false} />
                  : <input value={input} onInput={(event) => setInput(event.currentTarget.value)} type={visible ? "text" : "password"} autoComplete="off" spellcheck={false} />}
                <IconButton icon={visible ? EyeOff : Eye} label={visible ? "Hide" : "Show"} onClick={() => setVisible(!visible)} />
              </label>
              {error ? <StatusMessage kind="error" title={error} /> : null}
              <Button icon={Download} onClick={importWallet} disabled={!input.trim()}>Import Wallet</Button>
            </div>
          ) : null}
        </div>
      </main>
    );
  }

  return (
    <main class="onboarding flow-screen onboarding-transition" key={step}>
      <FlowHeader title="Create password" subtitle="Encrypt this wallet on your device." onBack={() => setStep(wallet?.type === "generated" ? "create" : "import")} />
      <div class="flow-content password-content">
        <div class="security-hero"><LockKeyhole size={34} /><h2>Protect poctra</h2><p>This password unlocks your wallet in this Chrome profile. Poctra cannot recover it.</p></div>
        <label class="field"><span>Password</span><input type="password" value={password} onInput={(event) => setPassword(event.currentTarget.value)} autoComplete="new-password" /></label>
        <label class="field"><span>Confirm password</span><input type="password" value={confirm} onInput={(event) => setConfirm(event.currentTarget.value)} autoComplete="new-password" /></label>
        <div class="password-meter"><span class={password.length >= 10 ? "active" : ""} /><span class={password.length >= 14 ? "active" : ""} /><span class={/[A-Z]|[^a-z0-9]/i.test(password) && password.length >= 14 ? "active" : ""} /></div>
        <small class="field-note">Use at least 10 characters. A longer unique password is safer.</small>
        {error ? <StatusMessage kind="error" title={error} /> : null}
      </div>
      <footer class="sticky-actions"><Button icon={LockKeyhole} onClick={() => void secureWallet()} disabled={busy || password.length < 10 || !confirm}>{busy ? "Encrypting vault" : "Secure Wallet"}</Button></footer>
    </main>
  );
}

export function UnlockScreen() {
  const { unlock, openExpanded } = useAppState();
  const [password, setPassword] = useState("");
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: Event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await unlock(password);
      setPassword("");
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main class="unlock-screen">
      <div class="unlock-top"><img src="/assets/poctra.svg" alt="" /><span>poctra</span>{document.documentElement.dataset.surface === "popup" ? <button onClick={openExpanded}>Expand</button> : null}</div>
      <form class="unlock-card" onSubmit={(event) => void submit(event)}>
        <div class="unlock-icon"><LockKeyhole size={28} /></div>
        <h1>Welcome back</h1>
        <p>Enter your password to unlock poctra.</p>
        <label class="field secret-input"><span>Password</span><input type={visible ? "text" : "password"} value={password} onInput={(event) => setPassword(event.currentTarget.value)} autoFocus autoComplete="current-password" /><IconButton icon={visible ? EyeOff : Eye} label={visible ? "Hide" : "Show"} type="button" onClick={() => setVisible(!visible)} /></label>
        {error ? <StatusMessage kind="error" title={error} /> : null}
        <Button type="submit" disabled={busy || !password}>{busy ? "Unlocking" : "Unlock Wallet"}</Button>
      </form>
      <p class="unlock-footnote">Your keys remain encrypted in this browser profile.</p>
    </main>
  );
}

function FlowHeader({ title, subtitle, onBack }: { title: string; subtitle: string; onBack: () => void }) {
  return <header class="flow-header"><IconButton icon={ArrowLeft} label="Back" onClick={onBack} /><div><h1>{title}</h1><p>{subtitle}</p></div></header>;
}

function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : "Something went wrong.";
}
