const header = document.querySelector("[data-header]");

const updateHeader = () => {
  if (!header) return;
  header.classList.toggle("is-scrolled", window.scrollY > 16);
};

updateHeader();
window.addEventListener("scroll", updateHeader, { passive: true });

document.querySelectorAll('a[href^="#"]').forEach((link) => {
  link.addEventListener("click", (event) => {
    const target = document.querySelector(link.getAttribute("href"));
    if (!target) return;
    event.preventDefault();
    target.scrollIntoView({ behavior: "smooth", block: "start" });
  });
});

const screenshots = [
  { src: "./assets/poctra.jpg", title: "Welcome", description: "Create a new wallet or begin a validated import flow." },
  { src: "./assets/import.jpg", title: "Import Wallet", description: "Mnemonic, Octra Base64 key, and Ethereum hex key recovery methods." },
  { src: "./assets/assets.jpg", title: "Assets", description: "A clear split between Octra assets and the derived EVM account." },
  { src: "./assets/bridge.jpg", title: "Bridge", description: "Bridge state, daily capacity, fee review, history, and recovery." },
  { src: "./assets/explore.jpg", title: "Explore", description: "Open dApps, Octra Identity names, addresses, and Circle resources." },
  { src: "./assets/overview.jpg", title: "Overview", description: "Public balance, primary actions, identity-aware activity, and network state." },
  { src: "./assets/multiwallet.jpg", title: "Wallets", description: "Name, reorder, import, and switch between multiple wallets." },
  { src: "./assets/trade.jpg", title: "Trade", description: "Candlestick context and a review-first Uniswap v4 swap flow." },
  { src: "./assets/privacy.jpg", title: "Privacy", description: "Encrypted balance and dedicated private operation entry points." },
];

const dialog = document.querySelector("[data-viewer-dialog]");
const viewerImage = dialog?.querySelector("[data-viewer-image]");
const viewerTitle = dialog?.querySelector("[data-viewer-title]");
const viewerDescription = dialog?.querySelector("[data-viewer-description]");
const viewerCounter = dialog?.querySelector("[data-viewer-counter]");
const viewerStage = dialog?.querySelector(".viewer-stage");
let activeIndex = 0;
let lastTrigger = null;
let touchStartX = null;
let viewerRenderId = 0;
const viewerImageCache = new Map();
const usesPoctraCircleBridge = Boolean(window.PoctraAndroid);

const blobToDataUrl = (blob) => new Promise((resolve, reject) => {
  const reader = new FileReader();
  reader.onload = () => resolve(String(reader.result || ""));
  reader.onerror = () => reject(reader.error || new Error("Unable to read screenshot"));
  reader.readAsDataURL(blob);
});

const viewerSource = (index) => {
  const item = screenshots[index];
  if (usesPoctraCircleBridge) return Promise.resolve(item.src);

  if (!viewerImageCache.has(index)) {
    viewerImageCache.set(index, fetch(item.src)
      .then((response) => {
        if (!response.ok) throw new Error(`Screenshot unavailable (${response.status})`);
        return response.blob();
      })
      .then(blobToDataUrl)
      .catch((error) => {
        viewerImageCache.delete(index);
        throw error;
      }));
  }
  return viewerImageCache.get(index);
};

const renderViewer = () => {
  if (!dialog || !viewerImage || !viewerTitle || !viewerDescription || !viewerCounter) return;
  const item = screenshots[activeIndex];
  const renderId = ++viewerRenderId;
  const renderedIndex = activeIndex;
  viewerImage.setAttribute("aria-busy", "true");
  viewerImage.onerror = null;
  viewerSource(renderedIndex)
    .then((source) => {
      if (renderId !== viewerRenderId || renderedIndex !== activeIndex) return;
      if (source !== item.src) {
        viewerImage.onerror = () => {
          viewerImage.onerror = null;
          viewerImage.src = item.src;
        };
      }
      viewerImage.src = source;
    })
    .catch(() => {
      if (renderId !== viewerRenderId || renderedIndex !== activeIndex) return;
      viewerImage.src = item.src;
    })
    .finally(() => {
      if (renderId === viewerRenderId) viewerImage.removeAttribute("aria-busy");
    });
  viewerImage.alt = `${item.title} screen in poctra`;
  viewerTitle.textContent = item.title;
  viewerDescription.textContent = item.description;
  viewerCounter.textContent = `${activeIndex + 1} / ${screenshots.length}`;
};

const openViewer = (index, trigger) => {
  if (!dialog) return;
  activeIndex = Number(index) || 0;
  lastTrigger = trigger;
  renderViewer();
  dialog.showModal();
};

const moveViewer = (direction) => {
  activeIndex = (activeIndex + direction + screenshots.length) % screenshots.length;
  renderViewer();
};

document.querySelectorAll("[data-viewer]").forEach((trigger) => {
  trigger.addEventListener("click", () => openViewer(trigger.dataset.viewer, trigger));
});

dialog?.querySelector("[data-viewer-close]")?.addEventListener("click", () => dialog.close());
dialog?.querySelector("[data-viewer-prev]")?.addEventListener("click", () => moveViewer(-1));
dialog?.querySelector("[data-viewer-next]")?.addEventListener("click", () => moveViewer(1));

dialog?.addEventListener("click", (event) => {
  if (event.target === dialog) dialog.close();
});

dialog?.addEventListener("close", () => lastTrigger?.focus());

viewerStage?.addEventListener("touchstart", (event) => {
  touchStartX = event.changedTouches[0]?.clientX ?? null;
}, { passive: true });

viewerStage?.addEventListener("touchend", (event) => {
  if (touchStartX === null) return;
  const endX = event.changedTouches[0]?.clientX ?? touchStartX;
  const distance = endX - touchStartX;
  touchStartX = null;
  if (Math.abs(distance) < 48) return;
  moveViewer(distance > 0 ? -1 : 1);
}, { passive: true });

document.addEventListener("keydown", (event) => {
  if (!dialog?.open) return;
  if (event.key === "ArrowLeft") moveViewer(-1);
  if (event.key === "ArrowRight") moveViewer(1);
});

// Circle resources keep the APK in small parts to avoid large RPC responses.
const apkDownload = document.querySelector("[data-apk-download]");
const apkParts = [
  "./assets/apk/poctra-alpha-v1.0.0-debug.apk.part01",
  "./assets/apk/poctra-alpha-v1.0.0-debug.apk.part02",
  "./assets/apk/poctra-alpha-v1.0.0-debug.apk.part03",
  "./assets/apk/poctra-alpha-v1.0.0-debug.apk.part04",
  "./assets/apk/poctra-alpha-v1.0.0-debug.apk.part05",
];

apkDownload?.addEventListener("click", async (event) => {
  event.preventDefault();
  if (apkDownload.dataset.loading === "true") return;

  const originalLabel = apkDownload.textContent;
  apkDownload.dataset.loading = "true";
  apkDownload.setAttribute("aria-disabled", "true");

  try {
    const chunks = [];
    for (let index = 0; index < apkParts.length; index += 1) {
      apkDownload.textContent = `Preparing ${index + 1}/${apkParts.length}`;
      const response = await fetch(apkParts[index]);
      if (!response.ok) throw new Error(`APK part ${index + 1} is unavailable`);
      chunks.push(await response.arrayBuffer());
    }

    const url = URL.createObjectURL(new Blob(chunks, {
      type: "application/vnd.android.package-archive",
    }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "poctra-alpha-v1.0.0-debug.apk";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) {
    console.error(error);
    apkDownload.textContent = "Retry download";
    return;
  } finally {
    apkDownload.dataset.loading = "false";
    apkDownload.removeAttribute("aria-disabled");
  }

  apkDownload.textContent = originalLabel;
});
