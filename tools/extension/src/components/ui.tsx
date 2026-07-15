import type { ComponentChildren, JSX } from "preact";
import { useEffect, useState } from "preact/hooks";
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  ChevronDown,
  Copy,
  ExternalLink,
  Eye,
  EyeOff,
  Info,
  LoaderCircle,
  X
} from "lucide-preact";
import type { LucideIcon } from "lucide-preact";
import { useAppState } from "../app/app-state";

export function Button({
  children,
  icon: Icon,
  variant = "primary",
  compact = false,
  className = "",
  ...props
}: JSX.ButtonHTMLAttributes<HTMLButtonElement> & {
  icon?: LucideIcon;
  variant?: "primary" | "soft" | "ghost" | "danger";
  compact?: boolean;
}) {
  return (
    <button type="button" class={`button button-${variant} ${compact ? "button-compact" : ""} ${className}`} {...props}>
      {Icon ? <Icon size={compact ? 14 : 17} aria-hidden="true" /> : null}
      {children}
    </button>
  );
}

export function IconButton({ icon: Icon, label, className = "", ...props }: JSX.ButtonHTMLAttributes<HTMLButtonElement> & {
  icon: LucideIcon;
  label: string;
}) {
  return (
    <button type="button" class={`icon-button ${className}`} aria-label={label} title={label} {...props}>
      <Icon size={18} aria-hidden="true" />
    </button>
  );
}

export function Header({ title, subtitle, actions, onBack }: {
  title: string;
  subtitle?: string;
  actions?: ComponentChildren;
  onBack?: () => void;
}) {
  return (
    <header class="screen-header">
      {onBack ? <IconButton icon={ArrowLeft} label="Back" onClick={onBack} /> : null}
      <div class="screen-title">
        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
      {actions ? <div class="header-actions">{actions}</div> : null}
    </header>
  );
}

export function Card({ children, className = "", onClick }: {
  children: ComponentChildren;
  className?: string;
  onClick?: () => void;
}) {
  const Tag = onClick ? "button" : "section";
  return <Tag type={onClick ? "button" : undefined} class={`card ${onClick ? "card-clickable" : ""} ${className}`} onClick={onClick}>{children}</Tag>;
}

export function TokenIcon({ symbol, size = 40 }: { symbol: string; size?: number }) {
  const clean = symbol.toLowerCase();
  const asset = clean === "eth" ? "eth.svg" : clean === "usdt" ? "usdt.svg" : "octra.svg";
  return <span class="token-icon" style={{ width: size, height: size }}><img src={`/assets/${asset}`} alt="" /></span>;
}

export function WalletAvatar({ label, size = 34 }: { label: string; size?: number }) {
  return <span class="wallet-avatar" style={{ width: size, height: size, fontSize: Math.max(11, size * .34) }}>{label.trim().charAt(0).toUpperCase() || "W"}</span>;
}

export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const { notify } = useAppState();
  return <IconButton icon={Copy} label={label} onClick={() => void navigator.clipboard.writeText(value).then(() => notify("Copied.", "success"))} />;
}

export function SecretField({ label, value, multiline = false }: { label: string; value: string; multiline?: boolean }) {
  const [visible, setVisible] = useToggle(false);
  return (
    <div class="secret-field">
      <div class="field-heading"><span>{label}</span><div><IconButton icon={visible ? EyeOff : Eye} label={visible ? "Hide" : "Show"} onClick={() => setVisible(!visible)} /><CopyButton value={value} /></div></div>
      <div class={`secret-value ${multiline ? "multiline" : ""} ${visible ? "" : "is-hidden"}`}>{visible ? value : "Click the eye to reveal"}</div>
    </div>
  );
}

export function StatusMessage({ kind = "info", title, children, action }: {
  kind?: "info" | "success" | "warning" | "error";
  title: string;
  children?: ComponentChildren;
  action?: ComponentChildren;
}) {
  const Icon = kind === "error" || kind === "warning" ? AlertTriangle : kind === "success" ? Check : Info;
  return (
    <div class={`status-message status-${kind}`}>
      <Icon size={15} />
      <div><strong>{title}</strong>{children ? <span>{children}</span> : null}</div>
      {action ? <div class="status-action">{action}</div> : null}
    </div>
  );
}

export function EmptyState({ children }: { children: ComponentChildren }) {
  return <p class="empty-state">{children}</p>;
}

export function LoadingState({ label = "Loading" }: { label?: string }) {
  return <div class="loading-state"><LoaderCircle class="spin" size={20} /><span>{label}</span></div>;
}

export function Sheet({ open, title, children, footer, onClose, locked = false }: {
  open: boolean;
  title: string;
  children: ComponentChildren;
  footer?: ComponentChildren;
  onClose: () => void;
  locked?: boolean;
}) {
  useEffect(() => {
    document.body.classList.toggle("sheet-open", open);
    return () => document.body.classList.remove("sheet-open");
  }, [open]);
  if (!open) return null;
  return (
    <div class="sheet-layer" role="presentation" onMouseDown={(event) => {
      if (!locked && event.target === event.currentTarget) onClose();
    }}>
      <section class="sheet" role="dialog" aria-modal="true" aria-label={title}>
        <div class="sheet-handle" />
        <header><h2>{title}</h2>{!locked ? <IconButton icon={X} label="Close" onClick={onClose} /> : null}</header>
        <div class="sheet-body">{children}</div>
        {footer ? <footer class="sheet-footer">{footer}</footer> : null}
      </section>
    </div>
  );
}

export function TransactionStepper({ steps, active, failed = false }: { steps: string[]; active: number; failed?: boolean }) {
  return (
    <div class="stepper">
      {steps.map((step, index) => {
        const complete = index < active || (!failed && index === steps.length - 1 && active >= steps.length);
        const current = index === active && !complete;
        return (
          <div class={`step ${complete ? "complete" : ""} ${current ? "current" : ""} ${failed && current ? "failed" : ""}`} key={step}>
            <span>{complete ? <Check size={13} /> : current ? <LoaderCircle class={failed ? "" : "spin"} size={13} /> : index + 1}</span>
            <div><strong>{step}</strong><small>{complete ? "Completed" : current ? failed ? "Failed" : "In progress" : "Waiting"}</small></div>
          </div>
        );
      })}
    </div>
  );
}

export function NetworkFeeSelector({ values, selected, onChange, custom, onCustom }: {
  values: { auto: string; fast: string };
  selected: "auto" | "fast" | "custom";
  onChange: (value: "auto" | "fast" | "custom") => void;
  custom: string;
  onCustom: (value: string) => void;
}) {
  return (
    <section class="fee-panel">
      <div class="section-label"><span>Network fee</span><small>Choose transaction priority</small></div>
      <div class="fee-options">
        <button type="button" aria-pressed={selected === "auto"} class={selected === "auto" ? "active" : ""} onClick={() => onChange("auto")}><strong>Auto</strong><small>{values.auto}</small></button>
        <button type="button" aria-pressed={selected === "fast"} class={selected === "fast" ? "active" : ""} onClick={() => onChange("fast")}><strong>Fast</strong><small>{values.fast}</small></button>
        <button type="button" aria-pressed={selected === "custom"} class={selected === "custom" ? "active" : ""} onClick={() => onChange("custom")}><strong>Custom</strong><small>{custom ? `${custom} OCT` : "Set fee"}</small></button>
      </div>
      {selected === "custom" ? <label class="field"><span>Custom fee (OCT)</span><input value={custom} onInput={(event) => onCustom(event.currentTarget.value)} inputMode="decimal" /></label> : null}
    </section>
  );
}

export function SelectField({ label, value, children, onChange }: {
  label: string;
  value: string;
  children: ComponentChildren;
  onChange: JSX.GenericEventHandler<HTMLSelectElement>;
}) {
  return <label class="field select-field"><span>{label}</span><select value={value} onChange={onChange}>{children}</select><ChevronDown size={15} /></label>;
}

export function ExternalButton({ url, children }: { url: string; children: ComponentChildren }) {
  return <Button variant="soft" compact icon={ExternalLink} onClick={() => window.open(url, "_blank", "noopener")}>{children}</Button>;
}

function useToggle(initial: boolean): [boolean, (value: boolean) => void] {
  const [value, setValue] = useState(initial);
  return [value, setValue];
}
