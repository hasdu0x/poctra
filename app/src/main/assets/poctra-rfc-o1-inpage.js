(() => {
  const PROVIDER_VERSION = "1.0.0";
  const DEBUG = window.__poctraRfcO1Debug === true;
  const debugLog = (message) => {
    if (!DEBUG) return;
    try { console.log(`[PoctraRFC] ${message}`); } catch { /* Debug logging must not affect the provider. */ }
  };

  function installWalletSheetCompatibility() {
    if (window.__poctraWalletSheetCompatibilityInstalled) return;
    window.__poctraWalletSheetCompatibilityInstalled = true;

    const setImportant = (element, property, value) => {
      element.style.setProperty(property, value, "important");
    };

    const normalizeContainingBlocks = (element) => {
      let ancestor = element.parentElement;
      while (ancestor && ancestor !== document.body && ancestor !== document.documentElement) {
        const style = window.getComputedStyle(ancestor);
        const createsContainingBlock = style.transform !== "none" ||
          style.perspective !== "none" || style.filter !== "none" ||
          /paint|layout|strict|content/.test(style.contain || "") ||
          /transform|perspective|filter/.test(style.willChange || "");
        if (createsContainingBlock) {
          setImportant(ancestor, "transform", "none");
          setImportant(ancestor, "perspective", "none");
          setImportant(ancestor, "filter", "none");
          setImportant(ancestor, "contain", "none");
          setImportant(ancestor, "will-change", "auto");
        }
        ancestor = ancestor.parentElement;
      }
    };

    const repairWalletSheet = () => {
      const sheet = document.querySelector(".wallet-sheet");
      if (!(sheet instanceof HTMLElement)) return;

      const style = window.getComputedStyle(sheet);
      if (style.display === "none" || style.visibility === "hidden" || Number(style.opacity || "1") <= 0) return;

      const viewport = window.visualViewport;
      const viewportTop = viewport ? viewport.offsetTop : 0;
      const viewportWidth = viewport ? viewport.width : window.innerWidth;
      const viewportHeight = viewport ? viewport.height : window.innerHeight;
      const viewportBottom = viewportTop + viewportHeight;
      const rect = sheet.getBoundingClientRect();
      const contentHeight = Math.max(sheet.scrollHeight || 0, sheet.clientHeight || 0);
      const collapsed = rect.height <= 56 && contentHeight >= rect.height + 24;
      const outsideViewport = rect.bottom <= viewportTop + 2 || rect.top >= viewportBottom - 2;
      if (!collapsed && !outsideViewport) return;

      const desiredHeight = Math.min(Math.max(contentHeight, 180), Math.floor(viewportHeight * 0.88));
      const backdrop = sheet.closest(".wallet-backdrop");

      normalizeContainingBlocks(backdrop instanceof HTMLElement ? backdrop : sheet);
      if (backdrop instanceof HTMLElement) {
        setImportant(backdrop, "position", "fixed");
        setImportant(backdrop, "inset", "0");
        setImportant(backdrop, "width", "auto");
        setImportant(backdrop, "height", "auto");
        setImportant(backdrop, "display", "flex");
        setImportant(backdrop, "align-items", "flex-end");
        setImportant(backdrop, "justify-content", "center");
        setImportant(backdrop, "overflow", "hidden");
        setImportant(backdrop, "z-index", "2147482999");
      }

      setImportant(sheet, "position", "relative");
      setImportant(sheet, "flex", "0 1 auto");
      setImportant(sheet, "left", "auto");
      setImportant(sheet, "right", "auto");
      setImportant(sheet, "top", "auto");
      setImportant(sheet, "bottom", "auto");
      setImportant(sheet, "width", `${Math.round(viewportWidth)}px`);
      setImportant(sheet, "height", `${desiredHeight}px`);
      setImportant(sheet, "min-height", `${desiredHeight}px`);
      setImportant(sheet, "max-height", `${Math.floor(viewportHeight * 0.88)}px`);
      setImportant(sheet, "transform", "none");
      setImportant(sheet, "animation", "none");
      setImportant(sheet, "overflow-y", "auto");
      setImportant(sheet, "box-sizing", "border-box");
      setImportant(sheet, "z-index", "2147483000");
      sheet.setAttribute("data-poctra-wallet-sheet-compat", "true");

      const repaired = sheet.getBoundingClientRect();
      debugLog(
        `wallet sheet repaired from=${Math.round(rect.height)} content=${contentHeight} ` +
        `to=${Math.round(repaired.width)}x${Math.round(repaired.height)} top=${Math.round(repaired.top)}`
      );
    };

    const repairTransactionProgressSheet = () => {
      const sheet = document.querySelector(".tx-progress-sheet, .tx-progress-dialog");
      if (!(sheet instanceof HTMLElement)) return;

      const style = window.getComputedStyle(sheet);
      if (style.display === "none" || style.visibility === "hidden" || Number(style.opacity || "1") <= 0) return;

      const viewport = window.visualViewport;
      const viewportLeft = viewport ? viewport.offsetLeft : 0;
      const viewportTop = viewport ? viewport.offsetTop : 0;
      const viewportWidth = viewport ? viewport.width : window.innerWidth;
      const viewportHeight = viewport ? viewport.height : window.innerHeight;
      const viewportBottom = viewportTop + viewportHeight;
      const rect = sheet.getBoundingClientRect();
      const contentHeight = Math.max(sheet.scrollHeight || 0, sheet.clientHeight || 0);
      const minimumHeight = Math.min(360, Math.max(260, Math.floor(viewportHeight * 0.42)));
      const desiredHeight = Math.min(
        Math.max(contentHeight, minimumHeight),
        Math.floor(viewportHeight * 0.88),
      );
      const reportedBottomInset = Math.max(0, window.innerHeight - viewportBottom);
      const bottomReserve = Math.max(16, reportedBottomInset);
      const clipped = rect.height < desiredHeight - 8 ||
        rect.top >= viewportTop + viewportHeight * 0.72 ||
        rect.bottom > viewportBottom - bottomReserve + 2;
      if (!clipped) return;

      const desiredTop = Math.max(viewportTop, viewportBottom - desiredHeight - bottomReserve);
      const backdrop = sheet.closest(".tx-progress-backdrop");

      normalizeContainingBlocks(backdrop instanceof HTMLElement ? backdrop : sheet);
      if (backdrop instanceof HTMLElement) {
        setImportant(backdrop, "position", "fixed");
        setImportant(backdrop, "left", `${Math.round(viewportLeft)}px`);
        setImportant(backdrop, "right", "auto");
        setImportant(backdrop, "top", `${Math.round(viewportTop)}px`);
        setImportant(backdrop, "bottom", "auto");
        setImportant(backdrop, "width", `${Math.round(viewportWidth)}px`);
        setImportant(backdrop, "height", `${Math.round(viewportHeight)}px`);
        setImportant(backdrop, "min-height", `${Math.round(viewportHeight)}px`);
        setImportant(backdrop, "display", "block");
        setImportant(backdrop, "overflow", "hidden");
        setImportant(backdrop, "z-index", "2147482999");
      }

      setImportant(sheet, "position", "fixed");
      setImportant(sheet, "flex", "none");
      setImportant(sheet, "left", `${Math.round(viewportLeft)}px`);
      setImportant(sheet, "right", "auto");
      setImportant(sheet, "top", `${Math.round(desiredTop)}px`);
      setImportant(sheet, "bottom", "auto");
      setImportant(sheet, "width", `${Math.round(viewportWidth)}px`);
      setImportant(sheet, "height", `${desiredHeight}px`);
      setImportant(sheet, "min-height", `${desiredHeight}px`);
      setImportant(sheet, "max-height", `${Math.floor(viewportHeight * 0.88)}px`);
      setImportant(sheet, "transform", "none");
      setImportant(sheet, "animation", "none");
      setImportant(sheet, "overflow-y", "auto");
      setImportant(sheet, "box-sizing", "border-box");
      setImportant(sheet, "z-index", "2147483000");
      sheet.setAttribute("data-poctra-tx-sheet-compat", "true");

      const repaired = sheet.getBoundingClientRect();
      debugLog(
        `transaction sheet repaired from=${Math.round(rect.top)},${Math.round(rect.height)} ` +
        `content=${contentHeight} to=${Math.round(repaired.top)},${Math.round(repaired.height)}`
      );
    };

    let repairScheduled = false;
    const scheduleRepair = () => {
      if (repairScheduled) return;
      repairScheduled = true;
      const repair = () => {
        repairWalletSheet();
        repairTransactionProgressSheet();
      };
      window.setTimeout(repair, 50);
      window.setTimeout(repair, 180);
      window.setTimeout(() => {
        repair();
        repairScheduled = false;
      }, 420);
    };

    document.addEventListener("click", scheduleRepair, true);
    window.addEventListener("resize", scheduleRepair);
    window.visualViewport?.addEventListener("resize", scheduleRepair);
    const root = document.documentElement;
    if (root) {
      new MutationObserver(scheduleRepair).observe(root, { childList: true, subtree: true });
    }
  }

  installWalletSheetCompatibility();
  if (DEBUG && !window.__poctraRfcO1ListenerDebugInstalled) {
    window.__poctraRfcO1ListenerDebugInstalled = true;
    const addEventListener = window.addEventListener;
    window.addEventListener = function (type, listener, options) {
      if (type === "octra:announceProvider") debugLog("dApp registered announce listener");
      return addEventListener.call(this, type, listener, options);
    };
    window.addEventListener("error", (event) => {
      debugLog(`page error: ${event?.message || "unknown"}`);
    });
    document.addEventListener("click", () => {
      window.setTimeout(() => {
        const sheet = document.querySelector(".wallet-sheet");
        if (!sheet) return;
        const options = sheet.querySelectorAll(".wallet-option:not(.disabled)").length;
        const empty = /no wallet detected/i.test(sheet.textContent || "");
        debugLog(`wallet picker options=${options} empty=${empty} height=${Math.round(sheet.getBoundingClientRect().height)}`);
      }, 150);
    }, true);
  }
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
      debugLog(`provider request method=${method}`);
      const params = args.params == null ? [] : Array.isArray(args.params) ? args.params : [args.params];
      const timeoutMs = APPROVAL_METHODS.has(method) ? 300_000 : QUICK_METHODS.has(method) ? 15_000 : 45_000;
      return new Promise((resolve, reject) => {
        const id = `poctra-${Date.now()}-${++this._sequence}`;
        const timeout = window.setTimeout(() => {
          this._pending.delete(id);
          reject(providerError(4900, `Request timeout: ${method}`, "network_unavailable"));
        }, timeoutMs);
        this._pending.set(id, { resolve, reject, timeout, method });
        debugLog(`provider postMessage id=${id} method=${method}`);
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
      debugLog(`provider response id=${event.data.id} error=${Boolean(event.data.error)}`);
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
  const info = Object.freeze({
    uuid: crypto.randomUUID(),
    name: "Poctra Wallet",
    rdns: "app.poctra.wallet",
    icon: "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCIgd2lkdGg9IjY0IiBoZWlnaHQ9IjY0IiByb2xlPSJpbWciIGFyaWEtbGFiZWw9InBvY3RyYSI+CiAgPHJlY3QgeD0iMCIgeT0iMCIgd2lkdGg9IjY0IiBoZWlnaHQ9IjY0IiByeD0iMTQiIHJ5PSIxNCIgZmlsbD0iIzAwMCIvPgogIDxwYXRoIGQ9Ik0gMzQuNjI2IDEyIEMgMzIgMTIgMjkuMTYzIDEzLjEyOSAyNi45MDUgMTUuOTkyIEwgMjYuNTM3IDEyLjkxOSBMIDE5LjE4MyAxMi45MTkgTCAxOS4xODMgNTIgTCAyNy40ODMgNTEuMTYgTCAyNy40ODMgMzguNSBDIDI5LjE2MyA0MC4zOTEgMzEuMzQzIDQxLjM4OSA0NC44MTcgMzUuMTM5IDQ0LjgxNyAyNi42MjkgQyA0NC44MTcgMTcuNjczIDQxLjkwMiAxMiAzNC42MjYgMTIgWiBNIDMyLjA1MyAxOC4xMiBDIDM0LjY3OSAxOC4xMiAzNi4yNTUgMTkuOTA1IDM2LjI1NSAyNi43MzQgQyAzNi4yNTUgMzIuOTA2IDM0LjUyMSAzNS4yOTYgMzEuNTggMzUuMjk2IEMgMjkuODIgMzUuMjk2IDI4LjYzOCAzNC41MzQgMjcuNDgzIDMyLjg1NCBMIDI3LjQ4MyAyMS4yNzEgQyAyOC42NjQgMTkuMzU0IDMwLjEwOSAxOC4xMiAzMi4wNTMgMTguMTIgWiIgZmlsbD0iIzAwRjI5QiIgZmlsbC1ydWxlPSJub256ZXJvIi8+Cjwvc3ZnPgo=",
    version: PROVIDER_VERSION
  });
  const announce = () => {
    debugLog(`announce uuid=${info.uuid} rdns=${info.rdns}`);
    window.dispatchEvent(new CustomEvent("octra:announceProvider", {
      detail: Object.freeze({ info, provider })
    }));
  };
  window.addEventListener("octra:requestProvider", () => {
    debugLog("requestProvider observed");
    announce();
  });
  announce();
})();
