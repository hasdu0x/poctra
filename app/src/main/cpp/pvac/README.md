# Vendored PVAC Runtime

This directory contains the native PVAC implementation copied from the local
OctWa webcli reference at commit `d1e4589717de07507c82574b133217127a230687`.

Android-specific changes are intentionally limited to:

- using `/dev/urandom` on Android API 23, where `getrandom` is not declared;
- compiling hardware AES paths for arm64 and x86_64;
- capping proof workers to a four-core process budget on Android;
- using a small unsupported stub on 32-bit ABIs so the rest of poctra remains
  compatible with older Android devices.

Do not replace these files independently. Update the complete directory from a
known OctWa revision and re-run native builds for every supported ABI.
