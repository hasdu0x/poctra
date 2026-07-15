import { expect, test } from "vitest";
import { fetchEvmActivity, fetchEvmBalance, fetchEvmTokens } from "./evm-rpc";
import { fetchOctraActivity, fetchOctraBalance, fetchOctraTokens } from "./octra-rpc";
import { fetchUsdPrices } from "./portfolio";
import { fetchTradeCandles, quoteTrade } from "./trade";
import type { EvmNetwork, OctraNetwork } from "./types";

const live = Boolean((globalThis as typeof globalThis & { process?: { env?: Record<string, string | undefined> } }).process?.env?.POCTRA_LIVE_TEST);
const liveTest = live ? test : test.skip;

const octraMainnet: OctraNetwork = {
  id: "live-octra-mainnet",
  name: "Octra Mainnet",
  networkType: "mainnet",
  rpcUrl: "https://octra.network",
  explorerUrl: "https://octrascan.io"
};
const ethereumMainnet: EvmNetwork = {
  id: "live-eth-mainnet",
  name: "Ethereum Mainnet",
  chainId: 1,
  symbol: "ETH",
  rpcUrl: "https://rpc.xme.my.id/eth-mainnet",
  explorerUrl: "https://etherscan.io",
  testnet: false
};

liveTest("loads complete Octra balance and activity", async () => {
  const address = "oct5ZHUXnGVJwwZowQsQuVrEWmE6qqvDNgak9PorPYoctra";
  const [balance, activity] = await Promise.all([
    fetchOctraBalance(octraMainnet, address),
    fetchOctraActivity(octraMainnet, address, 7)
  ]);
  expect(balance.raw).toBeGreaterThan(0n);
  expect(activity.length).toBeGreaterThan(0);
  expect(activity[0].from || activity[0].to).toBeTruthy();
  expect(activity[0].timestamp).toBeGreaterThan(0);
});

liveTest("falls back from the Poctra EVM proxy and loads EVM activity", async () => {
  const address = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
  const [balance, activity] = await Promise.all([
    fetchEvmBalance(ethereumMainnet, address),
    fetchEvmActivity(ethereumMainnet, address, 7)
  ]);
  expect(balance.wei).toBeGreaterThanOrEqual(0n);
  expect(activity.length).toBeGreaterThan(0);
});

liveTest("loads real wOCT candles and a Uniswap v4 quote", async () => {
  const [chart, quote] = await Promise.all([
    fetchTradeCandles("1H", true),
    quoteTrade({ network: ethereumMainnet, from: "ETH", to: "wOCT", amount: "0.0001", slippageBps: 50 })
  ]);
  expect(chart.candles.length).toBeGreaterThan(10);
  expect(quote.amountOutRaw).toBeGreaterThan(0n);
  expect(quote.gasEstimate).toBeGreaterThan(0n);
});

liveTest("loads portfolio prices and token balances", async () => {
  const [prices, evmTokens, octraTokens] = await Promise.all([
    fetchUsdPrices(),
    fetchEvmTokens(ethereumMainnet, "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045", [
      { contractAddress: "0x4647e1fE715c9e23959022C2416C71867F5a6E80", name: "Wrapped OCT", symbol: "wOCT", decimals: 6, chainId: 1 },
      { contractAddress: "0xdAC17F958D2ee523a2206206994597C13D831ec7", name: "Tether USD", symbol: "USDT", decimals: 6, chainId: 1 }
    ]),
    fetchOctraTokens(octraMainnet, "oct5ZHUXnGVJwwZowQsQuVrEWmE6qqvDNgak9PorPYoctra", [])
  ]);
  expect(prices.ETH).toBeGreaterThan(0);
  expect(evmTokens).toHaveLength(2);
  expect(Array.isArray(octraTokens)).toBe(true);
});
