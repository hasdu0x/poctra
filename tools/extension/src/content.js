(function () {
  document.addEventListener(
    "click",
    (event) => {
      const link = event.target?.closest?.("a[href]");
      if (!link) return;
      const href = link.getAttribute("href") || "";
      if (!href.toLowerCase().startsWith("oct://")) return;
      event.preventDefault();
      event.stopPropagation();
      chrome.runtime.sendMessage({ type: "open-octra-input", input: href });
    },
    true
  );
})();
