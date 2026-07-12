import {
  activeNetwork,
  activeProvider,
  applyTheme,
  defaultSettings,
  isOctraAddress,
  loadSettings,
  saveSettings,
  shortAddress
} from "./shared.js";

const resetButton = document.getElementById("resetButton");
const saveStatus = document.getElementById("saveStatus");
const modal = document.getElementById("settingsModal");
const modalTitle = document.getElementById("modalTitle");
const modalSubtitle = document.getElementById("modalSubtitle");
const modalBody = document.getElementById("modalBody");
const networkSummary = document.getElementById("networkSummary");
const providerSummary = document.getElementById("providerSummary");
const themeSummary = document.getElementById("themeSummary");

let settings;

init();

async function init() {
  settings = await loadSettings();
  applyTheme(settings);
  renderSummaries();
  document.getElementById("networkCard").addEventListener("click", openNetworkModal);
  document.getElementById("providerCard").addEventListener("click", openProviderModal);
  document.getElementById("appearanceCard").addEventListener("click", openAppearanceModal);
  document.getElementById("modalCloseButton").addEventListener("click", () => modal.close());
  resetButton.addEventListener("click", resetDefaults);
}

function renderSummaries() {
  const network = activeNetwork(settings);
  const provider = activeProvider(settings);
  networkSummary.textContent = `${network.name} - ${network.networkType}`;
  providerSummary.textContent = provider ? `${provider.name} - ${shortAddress(provider.contractAddress)}` : "No provider";
  themeSummary.textContent = settings.theme === "light" ? "Light theme" : "Dark theme";
}

function openNetworkModal() {
  modalTitle.textContent = "RPC Network";
  modalSubtitle.textContent = "Select active Octra RPC or add a custom endpoint.";
  modalBody.innerHTML = `
    <div id="networkList" class="list-stack"></div>
    <div class="modal-separator"></div>
    <form id="networkForm" class="form-stack">
      <label>
        <span>RPC name</span>
        <input id="networkName" type="text" placeholder="Octra Mainnet">
      </label>
      <label>
        <span>RPC endpoint URL</span>
        <input id="networkRpc" type="url" placeholder="https://octra.network">
      </label>
      <label>
        <span>Network type</span>
        <select id="networkType">
          <option value="mainnet">Mainnet</option>
          <option value="devnet">Devnet</option>
        </select>
      </label>
      <label>
        <span>Explorer URL</span>
        <input id="networkExplorer" type="url" placeholder="https://octrascan.io">
      </label>
      <button class="primary-button" type="submit">Add network</button>
    </form>
  `;
  renderNetworks();
  document.getElementById("networkForm").addEventListener("submit", addNetwork);
  modal.showModal();
}

function renderNetworks() {
  const list = document.getElementById("networkList");
  const active = activeNetwork(settings);
  list.innerHTML = "";
  for (const network of settings.networks) {
    const row = document.createElement("div");
    row.className = "network-row";
    row.innerHTML = `
      <div class="row-copy">
        <strong>${escapeHtml(network.name)}</strong>
        <span>${escapeHtml(network.rpcUrl)}</span>
        <small>${escapeHtml(network.explorerUrl || "No explorer configured")}</small>
      </div>
      <div class="row-actions">
        <span class="pill ${network.id === active.id ? "active" : ""}">${escapeHtml(network.networkType)}</span>
        <button class="small-button neutral" type="button" data-action="activate" data-id="${escapeHtml(network.id)}">${network.id === active.id ? "Active" : "Use"}</button>
        ${network.isDefault ? "" : `<button class="small-button danger" type="button" data-action="delete" data-id="${escapeHtml(network.id)}">Delete</button>`}
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
      renderSummaries();
      renderNetworks();
      showSaved();
    });
  });
}

function openProviderModal() {
  const network = activeNetwork(settings);
  modalTitle.textContent = "Identity Provider";
  modalSubtitle.textContent = `Manage providers for ${network.networkType}.`;
  modalBody.innerHTML = `
    <div id="providerList" class="list-stack"></div>
    <div class="modal-separator"></div>
    <form id="providerForm" class="form-stack">
      <label>
        <span>Provider name</span>
        <input id="providerName" type="text" placeholder="Octra Identity">
      </label>
      <label>
        <span>Contract address</span>
        <input id="providerContract" type="text" placeholder="oct...">
      </label>
      <label>
        <span>Network type</span>
        <select id="providerNetwork">
          <option value="mainnet">Mainnet</option>
          <option value="devnet">Devnet</option>
        </select>
      </label>
      <button class="primary-button" type="submit">Add provider</button>
    </form>
  `;
  document.getElementById("providerNetwork").value = network.networkType;
  renderProviders();
  document.getElementById("providerForm").addEventListener("submit", addProvider);
  modal.showModal();
}

function renderProviders() {
  const list = document.getElementById("providerList");
  const network = activeNetwork(settings);
  const activeProviderId = settings.activeProviderByNetwork?.[network.networkType];
  const providers = settings.providers.filter((provider) => provider.network === network.networkType);
  list.innerHTML = "";
  if (!providers.length) {
    list.innerHTML = `<div class="empty-row"><strong>No provider</strong><span>Add a provider for ${escapeHtml(network.networkType)}.</span></div>`;
    return;
  }
  for (const provider of providers) {
    const row = document.createElement("div");
    row.className = "provider-row";
    row.innerHTML = `
      <div class="row-copy">
        <strong>${escapeHtml(provider.name)}</strong>
        <span>${escapeHtml(provider.contractAddress)}</span>
        <small>${escapeHtml(provider.network)}</small>
      </div>
      <div class="row-actions">
        <span class="pill ${provider.id === activeProviderId ? "active" : ""}">${provider.id === activeProviderId ? "Active" : "Ready"}</span>
        <button class="small-button neutral" type="button" data-action="activate" data-id="${escapeHtml(provider.id)}">${provider.id === activeProviderId ? "Active" : "Use"}</button>
        ${provider.isDefault ? "" : `<button class="small-button danger" type="button" data-action="delete" data-id="${escapeHtml(provider.id)}">Delete</button>`}
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
      renderSummaries();
      renderProviders();
      showSaved();
    });
  });
}

function openAppearanceModal() {
  modalTitle.textContent = "Appearance";
  modalSubtitle.textContent = "Choose the extension theme.";
  modalBody.innerHTML = `
    <div class="theme-options">
      <button class="theme-card" type="button" data-theme="dark">
        <strong>Dark</strong>
        <span>circle viewer default for low-glare browsing.</span>
      </button>
      <button class="theme-card" type="button" data-theme="light">
        <strong>Light</strong>
        <span>Cleaner contrast for bright screens.</span>
      </button>
    </div>
  `;
  renderThemeOptions();
  modal.showModal();
}

function renderThemeOptions() {
  modalBody.querySelectorAll("[data-theme]").forEach((button) => {
    button.classList.toggle("active", button.dataset.theme === settings.theme);
    button.onclick = async () => {
      settings.theme = button.dataset.theme;
      settings = await saveSettings(settings);
      applyTheme(settings);
      renderSummaries();
      renderThemeOptions();
      showSaved();
    };
  });
}

async function addNetwork(event) {
  event.preventDefault();
  const name = document.getElementById("networkName").value.trim();
  const rpcUrl = document.getElementById("networkRpc").value.trim().replace(/\/+$/, "");
  const networkType = document.getElementById("networkType").value;
  const explorerUrl = document.getElementById("networkExplorer").value.trim().replace(/\/+$/, "");
  if (!name || !rpcUrl || !networkType || !explorerUrl) {
    showError("Fill all RPC network fields.");
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
  renderSummaries();
  renderNetworks();
  showSaved();
}

async function addProvider(event) {
  event.preventDefault();
  const name = document.getElementById("providerName").value.trim();
  const contractAddress = document.getElementById("providerContract").value.trim();
  const network = document.getElementById("providerNetwork").value;
  if (!name || !isOctraAddress(contractAddress)) {
    showError("Enter provider name and a valid Octra contract address.");
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
  renderSummaries();
  renderProviders();
  showSaved();
}

async function resetDefaults() {
  settings = await saveSettings(defaultSettings());
  applyTheme(settings);
  renderSummaries();
  if (modal.open) modal.close();
  showSaved("Defaults restored.");
}

function showSaved(message = "Saved.") {
  saveStatus.textContent = message;
  saveStatus.classList.remove("error");
  setTimeout(() => {
    saveStatus.textContent = "";
  }, 1800);
}

function showError(message) {
  saveStatus.textContent = message;
  saveStatus.classList.add("error");
}

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
