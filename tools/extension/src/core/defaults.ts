import type { VaultSettings } from "./types";

export const MAINNET_IDENTITY_CONTRACT = "octH1GEiwunjEZyiBMjp1ETy1VUJJKEEnsMRZ8P2AvTC3et";
export const DEVNET_IDENTITY_CONTRACT = "oct2Tdnmgh8qtvhC82W9VhoRJcLkAhNRCbnEEPhCUPX5b7m";
export const WOCT_ADDRESS = "0x4647e1fE715c9e23959022C2416C71867F5a6E80";
export const USDT_ADDRESS = "0xdAC17F958D2ee523a2206206994597C13D831ec7";

export function defaultSettings(): VaultSettings {
  return {
    theme: "dark",
    autoLockMinutes: 10,
    activeOctraNetworkId: "octra-mainnet",
    activeEvmNetworkId: "eth-mainnet",
    activeIdentityProviderByNetwork: {
      mainnet: "identity-mainnet",
      devnet: "identity-devnet"
    },
    octraNetworks: [
      {
        id: "octra-mainnet",
        name: "Octra Mainnet",
        networkType: "mainnet",
        rpcUrl: "https://octra.network",
        explorerUrl: "https://octrascan.io"
      },
      {
        id: "octra-devnet",
        name: "Octra Devnet",
        networkType: "devnet",
        rpcUrl: "http://165.227.225.79:8080",
        explorerUrl: "https://devnet.octrascan.io"
      }
    ],
    evmNetworks: [
      {
        id: "eth-mainnet",
        name: "Ethereum Mainnet",
        chainId: 1,
        symbol: "ETH",
        rpcUrl: "https://rpc.xme.my.id/eth-mainnet",
        explorerUrl: "https://etherscan.io",
        testnet: false
      },
      {
        id: "eth-sepolia",
        name: "Ethereum Sepolia",
        chainId: 11155111,
        symbol: "ETH",
        rpcUrl: "https://rpc.xme.my.id/eth-sepolia",
        explorerUrl: "https://sepolia.etherscan.io",
        testnet: true
      },
      {
        id: "eth-mainnet-publicnode",
        name: "Ethereum Mainnet PublicNode",
        chainId: 1,
        symbol: "ETH",
        rpcUrl: "https://ethereum-rpc.publicnode.com",
        explorerUrl: "https://etherscan.io",
        testnet: false
      },
      {
        id: "eth-sepolia-publicnode",
        name: "Ethereum Sepolia PublicNode",
        chainId: 11155111,
        symbol: "ETH",
        rpcUrl: "https://ethereum-sepolia-rpc.publicnode.com",
        explorerUrl: "https://sepolia.etherscan.io",
        testnet: true
      }
    ],
    identityProviders: [
      {
        id: "identity-mainnet",
        name: "Octra Identity",
        networkType: "mainnet",
        contractAddress: MAINNET_IDENTITY_CONTRACT
      },
      {
        id: "identity-devnet",
        name: "Octra Identity",
        networkType: "devnet",
        contractAddress: DEVNET_IDENTITY_CONTRACT
      }
    ],
    addressBook: [],
    octraAssets: [],
    evmAssets: [
      { contractAddress: WOCT_ADDRESS, name: "Wrapped OCT", symbol: "wOCT", decimals: 6, chainId: 1 },
      { contractAddress: USDT_ADDRESS, name: "Tether USD", symbol: "USDT", decimals: 6, chainId: 1 }
    ],
    evmNfts: [],
    connectedSites: []
  };
}
