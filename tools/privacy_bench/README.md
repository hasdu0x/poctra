# Poctra Privacy Benchmark

This internal benchmark runs the same PVAC proof primitives used by Poctra's
Decrypt, Stealth, and Privacy key-migration flows without involving UI, RPC,
wallet files, or transaction signing.

The executable uses deterministic benchmark-only seeds. It must never embed or
accept a production wallet private key.

Supported scenarios:

```text
zero
range
decrypt
decrypt-parallel
stealth
migration
```

Each run reports setup time, individual proof stages, local verification, and
the existing R1CS prover phase timings. Results are intended for verifier-
compatible performance work and must be compared on the same device under
similar thermal conditions.
