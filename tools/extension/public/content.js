(() => {
  const script = document.createElement("script");
  script.src = chrome.runtime.getURL("inpage.js");
  script.async = false;
  (document.head || document.documentElement).appendChild(script);
  script.remove();

  let port;
  const origin = location.origin;
  const targetOrigin = origin === "null" ? "*" : origin;

  function ensurePort() {
    if (port) return port;
    port = chrome.runtime.connect({ name: "poctra-provider" });
    port.onMessage.addListener((message) => {
      if (message?.type === "poctra-provider-response") {
        window.postMessage({ source: "poctra-content", id: message.id, result: message.result, error: message.error }, targetOrigin);
      } else if (message?.type === "poctra-provider-event") {
        window.postMessage({ source: "poctra-content-event", event: message.event, payload: message.payload }, targetOrigin);
      }
    });
    port.onDisconnect.addListener(() => { port = undefined; });
    return port;
  }

  window.addEventListener("message", (event) => {
    if (event.source !== window || (origin !== "null" && event.origin !== origin) || event.data?.source !== "poctra-inpage" || !event.data?.id) return;
    try {
      ensurePort().postMessage({
        type: "poctra-provider-request",
        id: event.data.id,
        method: event.data.method,
        params: event.data.params,
        origin
      });
    } catch (error) {
      port = undefined;
      window.postMessage({ source: "poctra-content", id: event.data.id, error: { code: 4900, message: error instanceof Error ? error.message : "Wallet transport disconnected.", data: { reason: "wallet_disconnected" } } }, targetOrigin);
    }
  });

  chrome.runtime.onMessage.addListener((message) => {
    if (message?.type !== "poctra-provider-event" || message.origin !== origin) return;
    window.postMessage({ source: "poctra-content-event", event: message.event, payload: message.payload }, targetOrigin);
  });
})();
