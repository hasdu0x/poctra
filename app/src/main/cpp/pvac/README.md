# Vendored PVAC Runtime

This directory contains the native PVAC implementation copied from the local
OctWa webcli reference at commit `10f2977` (`pvac security update and circle
policies`). The runtime emits the PVAC V7 MiMC-x5 profile while retaining the
V6 secret material required to prove an authorized key switch.

Poctra keeps the upstream proof format and verification rules unchanged.
Android-specific performance and platform changes are intentionally limited
to:

- using `/dev/urandom` on Android API 23, where `getrandom` is not declared;
- compiling hardware AES paths for arm64 and x86_64;
- sharing a bounded process-wide worker pool across proof jobs;
- preallocating R1CS structures and caching immutable field constants;
- sharing immutable extended-point generator snapshots across prover and
  verifier without duplicating compressed tables;
- using indexed MSM inputs, in-place inner-product folding, and prepared wNAF
  scalar recoding (width 4 on arm64);
- building with `-O3`, ThinLTO, hidden symbols, and dead-section removal;
- using a small unsupported stub on 32-bit ABIs so the rest of poctra remains
  compatible with older Android devices.

V6 registered keys are accepted only when the native runtime proves that the
active wallet owns the matching secret profile. Migration uses the dedicated
key-switch proof limits; matching public proof shape alone is not sufficient.

Do not replace these files independently. Update the complete protocol surface
from a known OctWa revision, reapply only the platform changes above, and run
native builds for every supported ABI plus the deterministic benchmark harness.
