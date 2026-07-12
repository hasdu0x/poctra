import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// Dev-server only: proxy `/rpc` to the upstream Octra node so the browser
// stays same-origin and CORS never enters the picture. Production builds
// hit the RPC directly per VITE_ONS_RPC.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const network = (env.VITE_ONS_NETWORK ?? 'devnet').toLowerCase()
  const configuredRpc = env.VITE_ONS_RPC?.trim()
  const upstream = configuredRpc ||
    (network === 'mainnet' ? 'https://octra.network' : 'http://165.227.225.79:8080')
  const allowedHosts = [
    '31e6-114-10-113-94.ngrok-free.app',
    ...(env.VITE_ALLOWED_HOSTS ?? '')
      .split(',')
      .map((host) => host.trim())
      .filter(Boolean),
  ]

  return {
    base: './',
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: 4000,
      allowedHosts,
      proxy: {
        // Browser POSTs to http://localhost:4000/rpc → Vite rewrites it
        // to <upstream>/rpc. The Origin header gets stripped by the
        // proxy, so the upstream sees a server-to-server request.
        '/rpc': {
          target:        upstream,
          changeOrigin:  true,
          secure:        upstream.startsWith('https://'),
          // Keep the path verbatim — the upstream node also serves /rpc.
          rewrite:       (p) => p,
        },
      },
    },
  }
})
