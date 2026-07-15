import { lazy, Suspense } from "preact/compat";
import { AlertTriangle, Check, Info, LoaderCircle } from "lucide-preact";
import { useAppState } from "./app/app-state";
import { DashboardScreen, EvmAccountScreen } from "./screens/Dashboard";
import { OnboardingScreen, UnlockScreen } from "./screens/Onboarding";

const SendOctScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.SendOctScreen })));
const SendOctraAssetScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.SendOctraAssetScreen })));
const ReceiveOctScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.ReceiveOctScreen })));
const ReceiveEvmScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.ReceiveEvmScreen })));
const SendEvmScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.SendEvmScreen })));
const SendNftScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.SendNftScreen })));
const BridgeScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.BridgeScreen })));
const TransactionDetailsScreen = lazy(() => import("./screens/Transactions").then((module) => ({ default: module.TransactionDetailsScreen })));
const SettingsScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.SettingsScreen })));
const WalletManagementScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.WalletManagementScreen })));
const RpcManagementScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.RpcManagementScreen })));
const EvmNetworksScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.EvmNetworksScreen })));
const IdentityProviderScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.IdentityProviderScreen })));
const AddressBookScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.AddressBookScreen })));
const BackupKeysScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.BackupKeysScreen })));
const ConnectedSitesScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.ConnectedSitesScreen })));
const AddAssetScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.AddAssetScreen })));
const AddNftScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.AddNftScreen })));
const AboutScreen = lazy(() => import("./screens/Settings").then((module) => ({ default: module.AboutScreen })));
const PortfolioScreen = lazy(() => import("./screens/Consumer").then((module) => ({ default: module.PortfolioScreen })));
const PortfolioAssetScreen = lazy(() => import("./screens/Consumer").then((module) => ({ default: module.PortfolioAssetScreen })));
const ActivityCenterScreen = lazy(() => import("./screens/Consumer").then((module) => ({ default: module.ActivityCenterScreen })));
const EvmActivityScreen = lazy(() => import("./screens/Consumer").then((module) => ({ default: module.EvmActivityScreen })));
const SecurityCenterScreen = lazy(() => import("./screens/Consumer").then((module) => ({ default: module.SecurityCenterScreen })));
const RecoveryPlanScreen = lazy(() => import("./screens/Consumer").then((module) => ({ default: module.RecoveryPlanScreen })));
const ApprovalsScreen = lazy(() => import("./screens/Consumer").then((module) => ({ default: module.ApprovalsScreen })));
const ApprovalScreen = lazy(() => import("./screens/Approval").then((module) => ({ default: module.ApprovalScreen })));

export function App() {
  const app = useAppState();
  if (app.phase === "loading") return <div class="boot-screen"><img src="/assets/poctra.svg" alt="" /><LoaderCircle class="spin" size={18} /></div>;
  if (app.phase === "welcome") return <OnboardingScreen />;
  if (app.phase === "locked") return <UnlockScreen />;
  if (app.surface === "approval") return <Suspense fallback={<div class="route-loading"><LoaderCircle class="spin" size={19} /></div>}><ApprovalScreen /></Suspense>;
  return (
    <div class="app-root">
      <div class="route-motion" key={routeKey(app.route)}><Suspense fallback={<div class="route-loading"><LoaderCircle class="spin" size={19} /><span>Loading</span></div>}><RouteContent /></Suspense></div>
      {app.toast ? <Toast kind={app.toast.kind} message={app.toast.message} id={app.toast.id} /> : null}
    </div>
  );
}

function Toast({ kind, message, id }: { kind: "success" | "error" | "warning" | "info"; message: string; id: number }) {
  const Icon = kind === "success" ? Check : kind === "error" || kind === "warning" ? AlertTriangle : Info;
  return <div class={`toast toast-${kind}`} key={id} role="status"><Icon size={15} aria-hidden="true" /><span>{message}</span></div>;
}

function RouteContent() {
  const app = useAppState();
  switch (app.route.name) {
    case "dashboard": return <DashboardScreen />;
    case "send-oct": return <SendOctScreen />;
    case "send-octra-asset": return <SendOctraAssetScreen contractAddress={app.route.contractAddress} />;
    case "receive-oct": return <ReceiveOctScreen />;
    case "send-evm": return <SendEvmScreen tokenAddress={app.route.token} />;
    case "send-evm-nft": return <SendNftScreen contractAddress={app.route.contractAddress} tokenId={app.route.tokenId} />;
    case "receive-evm": return <ReceiveEvmScreen />;
    case "evm-account": return <EvmAccountScreen />;
    case "bridge": return <BridgeScreen />;
    case "settings": return <SettingsScreen />;
    case "wallets": return <WalletManagementScreen />;
    case "rpc": return <RpcManagementScreen />;
    case "evm-networks": return <EvmNetworksScreen />;
    case "identity": return <IdentityProviderScreen />;
    case "address-book": return <AddressBookScreen />;
    case "backup": return <BackupKeysScreen />;
    case "connected-sites": return <ConnectedSitesScreen />;
    case "about": return <AboutScreen />;
    case "portfolio": return <PortfolioScreen />;
    case "portfolio-asset": return <PortfolioAssetScreen symbol={app.route.symbol} network={app.route.network} />;
    case "security-center": return <SecurityCenterScreen />;
    case "recovery-plan": return <RecoveryPlanScreen />;
    case "approvals": return <ApprovalsScreen />;
    case "activity": return <ActivityCenterScreen />;
    case "evm-activity": return <EvmActivityScreen />;
    case "transaction": return <TransactionDetailsScreen item={app.route.item} />;
    case "add-octra-asset": return <AddAssetScreen kind="octra" />;
    case "add-evm-asset": return <AddAssetScreen kind="evm" />;
    case "add-evm-nft": return <AddNftScreen />;
    default: return <DashboardScreen />;
  }
}

function routeKey(route: ReturnType<typeof useAppState>["route"]): string {
  return JSON.stringify(route);
}
