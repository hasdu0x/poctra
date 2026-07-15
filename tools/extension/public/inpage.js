(() => {
  const PROVIDER_VERSION = "1.0.0";
  const QUICK_METHODS = new Set([
    "octra_accounts", "octra_networkId", "octra_networkInfo", "octra_permissions", "octra_disconnect",
    "evm_getDerivedAddress", "evm_getChainId", "evm_getNetworkInfo"
  ]);
  const APPROVAL_METHODS = new Set([
    "octra_requestAccounts", "octra_switchNetwork", "octra_signMessage", "octra_sendTransaction",
    "octra_signTransaction", "octra_submitTransaction", "octra_sendContractTransaction",
    "evm_switchChain", "evm_sendTransaction", "evm_signMessage", "evm_signTypedData",
    "evm_transferToken", "evm_approveToken"
  ]);

  class PoctraProvider {
    constructor() {
      Object.defineProperties(this, {
        isOctra: { value: true, enumerable: true },
        isPoctra: { value: true, enumerable: true },
        isOctraWallet: { value: true, enumerable: true },
        providerId: { value: "poctra", enumerable: true },
        version: { value: PROVIDER_VERSION, enumerable: true }
      });
      this.selectedAddress = null;
      this._connected = false;
      this._networkId = null;
      this._sequence = 0;
      this._pending = new Map();
      this._listeners = new Map();
      window.addEventListener("message", (event) => this._handleMessage(event));
    }

    request(args) {
      if (!args || typeof args.method !== "string" || !args.method.trim()) {
        return Promise.reject(providerError(4200, "Invalid request: method is required."));
      }
      const method = args.method.trim();
      const params = args.params == null ? [] : Array.isArray(args.params) ? args.params : [args.params];
      const timeoutMs = APPROVAL_METHODS.has(method) ? 300_000 : QUICK_METHODS.has(method) ? 15_000 : 45_000;
      return new Promise((resolve, reject) => {
        const id = `poctra-${Date.now()}-${++this._sequence}`;
        const timeout = window.setTimeout(() => {
          this._pending.delete(id);
          reject(providerError(4900, `Request timeout: ${method}`, "network_unavailable"));
        }, timeoutMs);
        this._pending.set(id, { resolve, reject, timeout, method });
        window.postMessage({ source: "poctra-inpage", id, method, params }, targetOrigin());
      });
    }

    on(event, handler) {
      if (typeof handler !== "function") return this;
      const values = this._listeners.get(event) || new Set();
      values.add(handler);
      this._listeners.set(event, values);
      return this;
    }

    removeListener(event, handler) {
      this._listeners.get(event)?.delete(handler);
      return this;
    }

    _emit(event, payload) {
      this._listeners.get(event)?.forEach((handler) => {
        try { handler(payload); } catch { /* Consumer listener failures are isolated. */ }
      });
    }

    _handleMessage(event) {
      if (event.source !== window || !sameOrigin(event)) return;
      if (event.data?.source === "poctra-content-event") {
        this._handleEvent(event.data.event, event.data.payload);
        return;
      }
      if (event.data?.source !== "poctra-content") return;
      const request = this._pending.get(event.data.id);
      if (!request) return;
      window.clearTimeout(request.timeout);
      this._pending.delete(event.data.id);
      if (event.data.error) {
        request.reject(providerError(event.data.error.code || 4100, event.data.error.message || "Wallet request failed.", event.data.error.data?.reason, event.data.error.data));
        return;
      }
      if ((request.method === "octra_requestAccounts" || request.method === "octra_accounts") && Array.isArray(event.data.result)) {
        this._setAccounts(event.data.result, false);
      } else if (request.method === "octra_disconnect") {
        this._setAccounts([], false);
        this._connected = false;
      } else if (request.method === "octra_networkId") {
        this._networkId = event.data.result;
      }
      request.resolve(event.data.result);
    }

    _handleEvent(event, payload) {
      if (event === "accountsChanged") {
        this._setAccounts(Array.isArray(payload) ? payload : [], true);
        return;
      }
      if (event === "connect") {
        this._connected = true;
        this._networkId = payload?.networkId || null;
      } else if (event === "disconnect") {
        this._connected = false;
        this._networkId = null;
        this.selectedAddress = null;
        payload = providerError(4900, payload?.message || "Disconnected.");
      } else if (event === "networkChanged") {
        this._networkId = payload?.id || null;
      }
      this._emit(event, payload);
    }

    _setAccounts(accounts, emit) {
      const previous = this.selectedAddress;
      this.selectedAddress = accounts[0] || null;
      this._connected = accounts.length > 0;
      if (emit && previous !== this.selectedAddress) this._emit("accountsChanged", accounts);
    }
  }

  function providerError(code, message, reason, data) {
    const error = new Error(message);
    error.code = code;
    if (data || reason) error.data = data || { reason };
    return error;
  }

  function targetOrigin() {
    return window.location.origin === "null" ? "*" : window.location.origin;
  }

  function sameOrigin(event) {
    return window.location.origin === "null" || event.origin === window.location.origin;
  }

  const provider = new PoctraProvider();
  let providers = window.octraProviders;
  if (!Array.isArray(providers)) {
    providers = [];
    try {
      Object.defineProperty(window, "octraProviders", { value: providers, writable: false, configurable: false });
    } catch {
      window.octraProviders = providers;
    }
  }
  if (!providers.includes(provider)) providers.push(provider);
  if (!window.octra || window.octra.isOctra !== true) {
    try { Object.defineProperty(window, "octra", { value: provider, configurable: false, writable: false }); }
    catch { window.octra = provider; }
  }

  const info = Object.freeze({
    uuid: crypto.randomUUID(),
    name: "poctra",
    rdns: "app.poctra.wallet",
    version: PROVIDER_VERSION
  });
  const announce = () => window.dispatchEvent(new CustomEvent("octra:announceProvider", {
    detail: Object.freeze({ info, provider })
  }));
  window.addEventListener("octra:requestProvider", announce);
  announce();
  window.dispatchEvent(new Event("octraLoaded"));
  window.dispatchEvent(new CustomEvent("octra#initialized", { detail: { provider: "poctra" } }));
  window.postMessage({ type: "OCTRA_EXTENSION_AVAILABLE", version: PROVIDER_VERSION, providerId: "poctra" }, targetOrigin());
})();
