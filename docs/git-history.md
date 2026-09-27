# Git history and validation provenance

## Commit policy

Commit complete, validated milestones. Keep implementation, related fixes, tests and evidence together. Consolidate unpublished incremental commits before synchronization; preserve published history. Push the explicitly authorized branch and keep local recovery references private.

## 2026-09-27 consolidation

The 34 unpublished commits after `2fed94249cd03f799f15f1abd828064d13f26df2` were consolidated into three milestones. The published base was preserved, so synchronization uses a normal fast-forward push to `origin/dev/react-native`.

| Milestone | Original checkpoint | Consolidated commit |
|---|---|---|
| Hub v1.2 real Android/Node approval integration (2 commits) | `8a180eace9ee77ea964c97b69979433e5d02535a` | `27bb060c1983c186959a675303ba6dbb33a66345` |
| Hub v1.2.1 recovery, Endpoint and Group authorization (8 commits) | `a4007a51b53a36b15ecc02cbf50bdac4f744ec56` | `4fa9fe99c71d663344f7c7aadb12ce745c4a058f` |
| Hub v1.3 Monitor consent, recovery and native acceptance (24 commits) | `fa6e73a9feebaf4f06335deef005a68d169eae28` | The `feat: complete verified v1.3 Monitor consent and native acceptance` milestone containing this document |

The first two milestone trees are byte-identical to their original checkpoints:

- v1.2 tree: `37d5de69580ae6c69650458107544b857c53ec84`.
- v1.2.1 tree: `15ffe982d33d21423e487fc5fb7c8b0d511f7b3f`.

The v1.3 source tree before consolidation was `5befffce3c41245b6da30fe67c68f4b40853b96a`. Application code, tests, dependency locks, scripts, contract snapshots and existing acceptance records are unchanged by consolidation. Only `AGENTS.md`, the README link, and this history note were added or updated for the commit policy and provenance explanation. No APK was rebuilt and no runtime test result was reassigned.

Historical reports deliberately retain the original source commit IDs and APK SHA-256 values that were actually tested. Those old unpublished IDs need not resolve in a fresh remote clone. The original local history remains reachable through `refs/cicada/archive/pre-milestone-sync-20260927`; this recovery reference is not pushed. Product source `9568b2f` and instrumentation source `02cefa9` retain their original meaning in the acceptance reports; the final milestone contains the same corresponding application and instrumentation sources.

The private runtime evidence, APKs, credentials, signing keys and container state remain outside the repository. This history change does not alter any PASS, FAIL, BLOCKED or NOT_RUN result.
