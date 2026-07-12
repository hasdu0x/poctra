import {
  activeNetwork,
  activeProvider,
  applyTheme,
  defaultSettings,
  isOctraAddress,
  loadSettings,
  parseCircleInputSpec,
  resolveInputTarget,
  saveSettings,
  shortAddress
} from "./shared.js";

const input = document.getElementById("dappInput");
const form = document.getElementById("openForm");
const previewCard = document.getElementById("previewCard");
const previewIcon = document.getElementById("previewIcon");
const previewTitle = document.getElementById("previewTitle");
const previewSubtitle = document.getElementById("previewSubtitle");
const previewDetail = document.getElementById("previewDetail");
const protocolBadge = document.getElementById("protocolBadge");
const statusLine = document.getElementById("statusLine");
const topNetworkBadge = document.getElementById("topNetworkBadge");
const settingsButton = document.getElementById("settingsButton");
const resetButton = document.getElementById("resetButton");
const settingsStatus = document.getElementById("settingsStatus");
const networkSummary = document.getElementById("networkSummary");
const providerSummary = document.getElementById("providerSummary");
const themeSummary = document.getElementById("themeSummary");
const themeOptions = document.getElementById("themeOptions");

let settings;
let previewTarget = null;
let previewSerial = 0;
let previewTimer;
let statusTimer;

init();

async function init() {
  settings = await loadSettings();
  applyTheme(settings);
  renderAll();
  bindHome();
  bindNavigation();
  bindSettings();
  input.focus();
}

function bindHome() {
  input.addEventListener("input", updatePreviewSoon);
  form.addEventListener("submit", onSubmit);
  previewCard.addEventListener("click", openPreviewTarget);
  settingsButton.addEventListener("click", () => showScreen("settings"));
  topNetworkBadge.addEventListener("click", () => showScreen("network"));
  document.querySelectorAll("[data-input]").forEach((button) => {
    button.addEventListener("click", () => {
      input.value = button.dataset.input || "";
      updateProtocolBadge();
      updatePreviewSoon();
      input.focus();
    });
  });
}

function bindNavigation() {
  document.querySelectorAll("[data-back-screen]").forEach((button) => {
    button.addEventListener("click", () => showScreen(button.dataset.backScreen));
  });
}

function bindSettings() {
  document.getElementById("networkCard").addEventListener("click", () => showScreen("network"));
  document.getElementById("providerCard").addEventListener("click", () => showScreen("provider"));
  document.getElementById("appearanceCard").addEventListener("click", () => showScreen("appearance"));
  document.getElementById("networkForm").addEventListener("submit", addNetwork);
  document.getElementById("providerForm").addEventListener("submit", addProvider);
  resetButton.addEventListener("click", resetDefaults);
}

function showScreen(name) {
  document.querySelectorAll(".popup-screen").forEach((screen) => {
    screen.classList.toggle("is-active", screen.dataset.screen === name);
  });
  if (name === "home") {
    renderNetwork();
    setTimeout(() => input.focus(), 60);
  } else if (name === "settings") {
    renderSummaries();
  } else if (name === "network") {
    renderNetworks();
  } else if (name === "provider") {
    renderProviders();
  } else if (name === "appearance") {
    renderThemeOptions();
  }
}

function renderAll() {
  renderNetwork();
  renderSummaries();
  renderNetworks();
  renderProviders();
  renderThemeOptions();
}

function renderNetwork() {
  const network = activeNetwork(settings);
  topNetworkBadge.textContent = capitalize(network.networkType);
}

function renderSummaries() {
  const network = activeNetwork(settings);
  const provider = activeProvider(settings);
  networkSummary.textContent = `${network.name} - ${capitalize(network.networkType)}`;
  providerSummary.textContent = provider ? `${provider.name} - ${shortAddress(provider.contractAddress)}` : "No provider";
  themeSummary.textContent = settings.theme === "light" ? "Light theme" : "Dark theme";
}

function updatePreviewSoon() {
  updateProtocolBadge();
  clearTimeout(previewTimer);
  previewTimer = setTimeout(updatePreview, 280);
}

function updateProtocolBadge() {
  const spec = parseCircleInputSpec(input.value);
  protocolBadge.classList.toggle("hidden", !spec?.explicitScheme);
}

async function updatePreview() {
  const raw = input.value.trim();
  previewTarget = null;
  statusLine.textContent = "";
  statusLine.classList.remove("error");
  if (!raw) {
    previewCard.classList.add("hidden");
    return;
  }

  const serial = ++previewSerial;
  previewCard.classList.remove("hidden");
  previewIcon.textContent = "...";
  previewTitle.textContent = "Checking input";
  previewSubtitle.textContent = raw;
  previewDetail.textContent = "Resolving with active network";

  try {
    const target = await resolveInputTarget(raw, settings);
    if (serial !== previewSerial) return;
    previewTarget = target;
    previewIcon.textContent = target.kind === "circle" ? "o" : target.kind === "explorer" ? "x" : "w";
    previewTitle.textContent = target.title;
    previewSubtitle.textContent = target.subtitle || target.displayUrl || target.url;
    previewDetail.textContent = target.detail || "";
    protocolBadge.classList.toggle("hidden", !target.showOctProtocol);
    if (target.showOctProtocol) {
      protocolBadge.textContent = "oct://";
    }
  } catch (error) {
    if (serial !== previewSerial) return;
    previewIcon.textContent = "!";
    previewTitle.textContent = "Cannot resolve";
    previewSubtitle.textContent = raw;
    previewDetail.textContent = error.message || "Unknown error";
  }
}

async function onSubmit(event) {
  event.preventDefault();
  const raw = input.value.trim();
  if (!raw) return;
  statusLine.textContent = "Opening...";
  statusLine.classList.remove("error");
  try {
    const target = previewTarget && previewTarget.input === raw ? previewTarget : await resolveInputTarget(raw, settings);
    await chrome.tabs.create({ url: target.url });
    window.close();
  } catch (error) {
    statusLine.textContent = error.message || "Unable to open input.";
    statusLine.classList.add("error");
  }
}

async function openPreviewTarget() {
  if (!previewTarget) return;
  await chrome.tabs.create({ url: previewTarget.url });
  window.close();
}

function renderNetworks() {
  const list = document.getElementById("networkList");
  const active = activeNetwork(settings);
  list.innerHTML = "";
  for (const network of settings.networks) {
    const row = document.createElement("div");
    row.className = `network-row ${network.id === active.id ? "is-active" : ""}`;
    row.innerHTML = `
      <div class="row-copy">
        <strong>${escapeHtml(network.name)}</strong>
        <span>${escapeHtml(network.rpcUrl)}</span>
        <small>${escapeHtml(network.explorerUrl || "No explorer configured")}</small>
      </div>
      <div class="row-actions">
        <span class="pill ${network.id === active.id ? "active" : ""}">${network.id === active.id ? "Active" : capitalize(network.networkType)}</span>
        <button class="small-button neutral" type="button" data-action="activate" data-id="${escapeHtml(network.id)}">${network.id === active.id ? "Active" : "Use"}</button>
        ${network.isDefault ? "" : `<button class="icon-button danger ghost" type="button" data-action="delete" data-id="${escapeHtml(network.id)}" aria-label="Delete network">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 6h18"></path>
            <path d="M8 6V4h8v2"></path>
            <path d="M6 6l1 15h10l1-15"></path>
          </svg>
        </button>`}
      </div>
    `;
    list.append(row);
  }
  list.querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", async () => {
      const id = button.dataset.id;
      if (button.dataset.action === "activate") {
        settings.activeNetworkId = id;
      } else if (button.dataset.action === "delete") {
        settings.networks = settings.networks.filter((network) => network.id !== id || network.isDefault);
        if (!settings.networks.some((network) => network.id === settings.activeNetworkId)) {
          settings.activeNetworkId = settings.networks[0]?.id;
        }
      }
      settings = await saveSettings(settings);
      renderAll();
      showSaved();
    });
  });
}

function renderProviders() {
  const list = document.getElementById("providerList");
  const network = activeNetwork(settings);
  const activeProviderId = settings.activeProviderByNetwork?.[network.networkType];
  const providers = settings.providers.filter((provider) => provider.network === network.networkType);
  const providerNetwork = document.getElementById("providerNetwork");
  providerNetwork.value = network.networkType;
  list.innerHTML = "";
  if (!providers.length) {
    list.innerHTML = `<div class="empty-row"><strong>No provider</strong><span>Add a provider for ${escapeHtml(network.networkType)}.</span></div>`;
    return;
  }
  for (const provider of providers) {
    const row = document.createElement("div");
    row.className = `provider-row ${provider.id === activeProviderId ? "is-active" : ""}`;
    row.innerHTML = `
      <div class="row-copy">
        <strong>${escapeHtml(provider.name)}</strong>
        <span>${escapeHtml(provider.contractAddress)}</span>
        <small>${escapeHtml(capitalize(provider.network))}</small>
      </div>
      <div class="row-actions">
        <span class="pill ${provider.id === activeProviderId ? "active" : ""}">${provider.id === activeProviderId ? "Active" : "Ready"}</span>
        <button class="small-button neutral" type="button" data-action="activate" data-id="${escapeHtml(provider.id)}">${provider.id === activeProviderId ? "Active" : "Use"}</button>
        ${provider.isDefault ? "" : `<button class="icon-button danger ghost" type="button" data-action="delete" data-id="${escapeHtml(provider.id)}" aria-label="Delete provider">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 6h18"></path>
            <path d="M8 6V4h8v2"></path>
            <path d="M6 6l1 15h10l1-15"></path>
          </svg>
        </button>`}
      </div>
    `;
    list.append(row);
  }
  list.querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", async () => {
      const id = button.dataset.id;
      if (button.dataset.action === "activate") {
        settings.activeProviderByNetwork = {
          ...(settings.activeProviderByNetwork || {}),
          [network.networkType]: id
        };
      } else if (button.dataset.action === "delete") {
        settings.providers = settings.providers.filter((provider) => provider.id !== id || provider.isDefault);
      }
      settings = await saveSettings(settings);
      renderAll();
      showSaved();
    });
  });
}

function renderThemeOptions() {
  themeOptions.innerHTML = `
    <button class="theme-card" type="button" data-theme="dark">
      <strong>Dark</strong>
      <span>circle viewer default for low-glare browsing.</span>
    </button>
    <button class="theme-card" type="button" data-theme="light">
      <strong>Light</strong>
      <span>Cleaner contrast for bright screens.</span>
    </button>
  `;
  themeOptions.querySelectorAll("[data-theme]").forEach((button) => {
    button.classList.toggle("active", button.dataset.theme === settings.theme);
    button.addEventListener("click", async () => {
      settings.theme = button.dataset.theme;
      settings = await saveSettings(settings);
      applyTheme(settings);
      renderAll();
      showSaved();
    });
  });
}

async function addNetwork(event) {
  event.preventDefault();
  const name = document.getElementById("networkName").value.trim();
  const rpcUrl = document.getElementById("networkRpc").value.trim().replace(/\/+$/, "");
  const networkType = document.getElementById("networkType").value;
  const explorerUrl = document.getElementById("networkExplorer").value.trim().replace(/\/+$/, "");
  if (!name || !rpcUrl || !networkType || !explorerUrl) {
    showSaved("Fill all RPC network fields.", true);
    return;
  }
  settings.networks = settings.networks.filter((network) => network.rpcUrl !== rpcUrl);
  settings.networks.push({
    id: `octra-custom-${Date.now()}`,
    name,
    rpcUrl,
    networkType,
    explorerUrl,
    isDefault: false
  });
  settings.activeNetworkId = settings.networks.at(-1).id;
  settings = await saveSettings(settings);
  event.target.reset();
  renderAll();
  showSaved("Network added.");
}

async function addProvider(event) {
  event.preventDefault();
  const name = document.getElementById("providerName").value.trim();
  const contractAddress = document.getElementById("providerContract").value.trim();
  const network = document.getElementById("providerNetwork").value;
  if (!name || !isOctraAddress(contractAddress)) {
    showSaved("Enter provider name and valid Octra contract address.", true);
    return;
  }
  const id = `identity-custom-${Date.now()}`;
  settings.providers = settings.providers.filter((provider) => `${provider.network}:${provider.contractAddress}` !== `${network}:${contractAddress}`);
  settings.providers.push({
    id,
    name,
    contractAddress,
    network,
    isDefault: false
  });
  settings.activeProviderByNetwork = {
    ...(settings.activeProviderByNetwork || {}),
    [network]: id
  };
  settings = await saveSettings(settings);
  event.target.reset();
  renderAll();
  showSaved("Provider added.");
}

async function resetDefaults() {
  settings = await saveSettings(defaultSettings());
  applyTheme(settings);
  renderAll();
  showSaved("Defaults restored.");
}

function showSaved(message = "Saved.", isError = false) {
  clearTimeout(statusTimer);
  settingsStatus.textContent = message;
  settingsStatus.classList.toggle("error", isError);
  statusTimer = setTimeout(() => {
    settingsStatus.textContent = "";
    settingsStatus.classList.remove("error");
  }, 1800);
}

function capitalize(value) {
  const clean = String(value || "");
  return clean ? clean.charAt(0).toUpperCase() + clean.slice(1) : "";
}

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
