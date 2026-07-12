# poctra public circles

This folder contains public circle projects that can be deployed independently.

## Projects

- `landing/` - deployed poctra landing page public circle.
- `dapp/` - copied `octra.id` frontend source prepared for a devnet public circle dApp.

## Current Landing Deployment

```text
oct://oct3A5bhpktxb9y2gepQeGxSbkJspq3Pv178Zyi9tkM1oJx/index.html
```

The landing is `public_resources` and does not require a passphrase.

## Current dApp Deployment

```text
oct://octDKJ5vct7qDnbbAQdeq1SkCpt15V5uEb8XuEpyLWq851h/index.html
```

The dApp circle is `public_resources` and does not require a passphrase.

## dApp Notes

`dapp/` is a copy of:

```text
C:\Users\Administrator\Documents\Devs\octra.id\frontend
```

The original `octra.id` workspace is intentionally untouched. The copied dApp env uses the devnet Octra Identity contract and leaves `VITE_ONS_RPC` blank for circle runtime integration.
