const screens = {
  overview: {
    image: "./assets/screenshots/overview.png",
    kicker: "Overview",
    title: "Portfolio, balance, actions, and recent activity.",
    description:
      "A compact dashboard for wallet identity, network state, OCT balance, primary actions, and recent Octra activity.",
    points: [
      "Compact wallet and network header.",
      "Native send, receive, bridge, and activity flows.",
      "Pull-to-refresh for current wallet state.",
    ],
  },
  assets: {
    image: "./assets/screenshots/assets.png",
    kicker: "Assets",
    title: "Octra assets and EVM account in one place.",
    description:
      "Assets are grouped so users can understand what belongs to Octra and what belongs to the derived EVM account.",
    points: [
      "Native OCT and Octra-chain asset list.",
      "Dedicated EVM Account entry point.",
      "Clickable asset rows for send and history flows.",
    ],
  },
  bridge: {
    image: "./assets/screenshots/bridge.png",
    kicker: "Bridge",
    title: "OCT to wOCT bridge with review and recovery.",
    description:
      "Bridge status, network checks, review sheets, progress tracking, and recovery actions are built around the mobile flow.",
    points: [
      "OCT to wOCT and wOCT to OCT flows.",
      "Bridge history with recover and claim support.",
      "Gas review and progress stepper before execution.",
    ],
  },
  trade: {
    image: "./assets/screenshots/trade.png",
    kicker: "Trade",
    title: "wOCT swaps with chart, quote, and review.",
    description:
      "The trade screen keeps chart context and swap review close together while avoiding repeated network calls when data is cached.",
    points: [
      "wOCT, ETH, and USDT swap routes.",
      "Gas option review before swap.",
      "Cached chart behavior for a smoother mobile experience.",
    ],
  },
  explore: {
    image: "./assets/screenshots/explore.png",
    kicker: "Explore",
    title: "dApp browser and Octra circle access.",
    description:
      "Users can open dApps, Octra Identity names, circle addresses, or explorer fallbacks from a compact input field.",
    points: [
      "poctra dApp provider injection.",
      "Connection and signing approval sheets.",
      "oct:// circle routing and explorer fallback.",
    ],
  },
  import: {
    image: "./assets/screenshots/import.png",
    kicker: "Import",
    title: "Guided import with secure input states.",
    description:
      "Seed phrase, Octra key, and EVM hex key imports are separated into a cleaner selection flow.",
    points: [
      "Mnemonic, Base64 key, and Ethereum hex key imports.",
      "Hidden sensitive input by default.",
      "Octra address validation before setup.",
    ],
  },
  welcome: {
    image: "./assets/screenshots/welcome.png",
    kicker: "Welcome",
    title: "Create or recover an Octra wallet.",
    description:
      "The first screen keeps wallet creation and recovery obvious, with a quiet production-grade entry point.",
    points: [
      "Create Wallet and Import Wallet actions.",
      "Dark mode default visual system.",
      "Local security setup before dashboard access.",
    ],
  },
};

const image = document.querySelector("#screen-image");
const kicker = document.querySelector("#screen-kicker");
const title = document.querySelector("#screen-title");
const description = document.querySelector("#screen-description");
const points = document.querySelector("#screen-points");
const tabs = [...document.querySelectorAll(".screen-tab")];

function renderScreen(key) {
  const screen = screens[key] || screens.overview;
  tabs.forEach((tab) => tab.classList.toggle("is-active", tab.dataset.screen === key));
  image.src = screen.image;
  image.alt = `poctra ${screen.kicker.toLowerCase()} screen`;
  kicker.textContent = screen.kicker;
  title.textContent = screen.title;
  description.textContent = screen.description;
  points.replaceChildren(
    ...screen.points.map((point) => {
      const item = document.createElement("li");
      item.textContent = point;
      return item;
    }),
  );
}

tabs.forEach((tab) => {
  tab.addEventListener("click", () => renderScreen(tab.dataset.screen));
});
