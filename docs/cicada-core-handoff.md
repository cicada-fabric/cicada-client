# Handoff: CICADA Client ↔ fixed Hub `client-hub-v1.2.1`

**Date:** 2026-09-26

**Client branch:** `dev/react-native`

**Node/Codex E2E candidate commit:** `09ecfe761811ceaf37e6deea2842b8a3949dfa46`

**Group verifier fix commit:** `af3ad451e29d0c43142b3fc792272bbd6df75c84`

**Final Group implementation commit:** `b676668e33c876c0d2c89bc3495fbd0d1e128c82`

**Validation record:** [v1.2.1 fixed-image validation](client-hub-v1.2.1-967dbd-validation.md)

**Two-Owner authorization implementation:** `680fac2bf12c431ac02804e5db168669cf12941f`

**Separate authorization record:** [Two-Owner fixed-image validation](client-two-owner-v1.2.1-967dbd-validation.md)

## Fixed integration baseline

| Item | Value |
|---|---|
| Hub source commit | `967dbd885fae9a150b3d9a77c8e4e30da1d0dd8a` (`source_dirty=false`) |
| Protocol | `client-hub-v1.2.1` |
| Protocol archive SHA-256 | `7bf1e3702eadf9fc3ffd50a0e8ab1213db0844b2bf418b577b88ba239216bf8a` |
| Catalog SHA-256 | `25c3d7f585b1811781cb46669a09e2e08ab8c58765a7b9318145cea5bbce4df9` |
| Full local image ID | `sha256:adca1c62db5747625141be4506c4f3713368260076c50876776b4dabafa6c1b7` |
| Recovery/lost-response candidate debug APK SHA-256 | `e75e994608e12b223fb3cb041cf1d3fba1d10411e519a317c4afc25e5c94fed3` |
| Recovery/lost-response candidate AndroidTest APK SHA-256 | `ad8bfa8c8b914677194f1cf493153eaaff67c817fc277d299b0a998a028456b8` |
| Pre-RFC3339Nano-fix Group/E2E candidate debug APK SHA-256 | `70423555e96381722d1bdc46633d32c0fca6dc32edae4dfb45865abc1059d197` |
| Pre-RFC3339Nano-fix Group/E2E candidate AndroidTest APK SHA-256 | `50756de57eb0ef2d9ca55feab6222d78bc4849ddf68b203560138918ba8f7c7f` |
| Corrected Group verifier debug APK SHA-256 | `2762a2defd13a0d85bc9a7c96647cbe626f632314ac958d4d1fe689ebd346b76` |
| Corrected Group verifier AndroidTest APK SHA-256 | `711e00f9fee11582c81af14e5a2c9d77799412166a8a3cd7008ec7cb16d27ab0` |
| Final live Group acceptance debug APK SHA-256 | `cfe345272f2399cbed7cd76f47e25e3e6fc09ed94daadb1d6e1a6ba9106caff1` |
| Final live Group acceptance AndroidTest APK SHA-256 | `52e9e678666654d57bd127b5f616a3094e9a9925b9fe5b960632c7cb81d6f9d5` |

The APKs are candidate debug/test artifacts, not signed release packages. Protocol package, manifest, and image-label checks passed. The imported files are under [`contracts/client-hub-v1.2.1-967dbd`](../contracts/client-hub-v1.2.1-967dbd/manifest.json).

## Two-Owner authorization acceptance — 2026-09-26

The separate two-Owner slice is **PASS** on implementation commit
`680fac2bf12c431ac02804e5db168669cf12941f`. It uses the same clean fixed Hub
commit, archive, catalog and complete image ID above. It adds only disposable
fixture and Android instrumentation code. The existing UI, local STT and
production cryptographic checks were preserved. No Monitor operations or
new contract version were introduced.

The final fresh-run evidence root is
`/gpu1-share/data/cicada-client/two-owner-20260926T024036Z-cto-9uslracc/evidence`
(`E2` below). Its debug APK SHA-256 is
`cfe345272f2399cbed7cd76f47e25e3e6fc09ed94daadb1d6e1a6ba9106caff1`;
its AndroidTest APK SHA-256 is
`342845155edf0333e3b81aa736d25945e8d83554a61c26fc1bf438c314d26ae0`.
The app artifact is unchanged from the final one-Owner Group run; this
authorization result comes from new execution with a different test APK.

For the commands below, `F=/tmp/cto.9uslracc` was the disposable fixture,
now removed, and `A=python3 scripts/interop/two-owner-android.py`.
The [complete authorization report](client-two-owner-v1.2.1-967dbd-validation.md)
contains exact commands, failures from the initial development attempt,
image/helper provenance and evidence limits.

| Check | Result | Command and exit | Evidence under `E2` |
|---|---|---|---|
| Clean fixed target and package verification | **PASS** | `python3 scripts/interop/two-owner-fixture.py prepare`: 0; nested contract verifier: 0 | `fixed-target.json`, `verify-contract.log`, `runtime-provenance.json`, `fixture-commands.jsonl` |
| Independent Android device keys A, A-admin and B | **PASS** | `$A "$F" test prepareTwoOwnerDeviceKeys`: runner 0, ADB 0, `OK (1 test)` | `prepareTwoOwnerDeviceKeys.json` |
| Owner-bound encrypted capabilities, Node confirmations and separate Groups | **PASS** | `$A "$F" test enrollConfirmAndCreateOwnerGroups`: runner 0, ADB 0, `OK (1 test)`; four reviewed taps: 0 | `enrollConfirmAndCreateOwnerGroups.json`, `reviewed-taps.json` |
| Live manifests, complete Android proof verification and external Owner signatures | **PASS** | `$A "$F" test exportOwnerGroupManifests`: runner 0, ADB 0, `OK (1 test)`; `sign-groups "$F"` through fixture driver: 0 | `exportOwnerGroupManifests.json`, `fixture-commands.jsonl` |
| Both own grants `CURRENT`; six cross-Owner requests denied; tampered proof rejected at Hub | **PASS** | `$A "$F" test twoOwnerOwnershipAndRevocationMatrix`: runner 0, ADB 0, `OK (1 test)`; two reviewed grant taps: 0 | `twoOwnerOwnershipAndRevocationMatrix.json`, `summary.redacted.json`, `reviewed-taps.json` |
| Local pending/identity isolation, authoritative grant records unchanged, revoke A while B remains usable | **PASS** | Same matrix command: 0; includes fresh A request and exact old packet `/rpc` and `/recover` HTTP 403 | `summary.redacted.json`, matrix private log |
| Known-secret checks and cleanup | **PASS** | Local evidence scan: 0; `python3 scripts/interop/two-owner-fixture.py stop "$F"`: 0 | `privacy-check.redacted.json`, `teardown.json` |
| New native Codex/Thread run; physical Android; public HTTPS | **NOT_RUN** | No commands for these gates in this slice | Earlier native evidence remains separately attributed |

No required item in this two-Owner slice remains **BLOCKED**. The Hub negative
cases used each other Owner's real existing Group, Endpoint and valid Owner
proof. All six requests returned HTTP 200 with a verified encrypted
`permission denied` business result. The separate corrupted-signature request
reached the Hub verifier and returned a signature rejection. These are distinct
from the product's local preflight errors and the HTTP 403 responses after
revocation. The authenticated negative requests legitimately advance Hub
request/response counters; unchanged state refers to target grants and isolated
Client journals, not an unchanged Hub request ledger.

These Endpoints are explicitly **synthetic authorization fixtures**, not native
sessions. The three Android contexts have independent PQ identities and journals
but share one emulator app UID and Keystore wrapping alias. The wrong-device
response-open assertion rejects a route/recipient mismatch; it is not a separate
OS key-isolation or KEM-decapsulation experiment. Owner/Endpoint private keys and
Node bearers stayed outside Android. Both runs' disposable Hub/Node/emulator
state and credentials were removed. Nothing was pushed, merged or deployed.

## Earlier evidence on this fixed target

- An independent Android session completed encrypted `session.capabilities` against the fixed image.
- Exact enrollment Grant replay after a lost HTTP 201 and exact encrypted RPC recovery after a lost HTTP 200 passed. The Android tests each returned `OK (1 test)` and ADB exit 0; the proxy evidence records the upstream response before dropping the downstream response.
- All three Android recovery fault tests passed with a new disposable Hub under `/tmp`. Each scenario script exited 0 and each final Android test returned `OK (1 test)` with ADB exit 0. For the uncertain outcome, the test waited for `FAULT_READY`, restarted only the disposable Hub, then recovered the signed encrypted notice. The details and Git-external evidence paths are in the validation report.
- Candidate APK `70423555e96381722d1bdc46633d32c0fca6dc32edae4dfb45865abc1059d197` passed `EndpointAttestationVectorTest` (`OK (4 tests)`, ADB exit 0), covering the full Endpoint proof, synthetic manifest digests, and owner proof. Subsequent review found that this candidate rejects valid RFC3339Nano timestamps with four fractional digits.
- The fix in commit `af3ad451e29d0c43142b3fc792272bbd6df75c84` passed `./scripts/docker-build-android-test.sh` (exit 0) and `EndpointAttestationVectorTest` on `emulator-5554` (`OK (4 tests)`, ADB exit 0), producing corrected APKs `2762a2…` and `711e00…` above.
- Candidate APK/test pair `7042…`/`5075…` passed `ClientWirePublicVectorTest` (`OK (5 tests)`) and `ClientHubRecoveryLockTest` (`OK (3 tests)`), each ADB exit 0 on `emulator-5554`.
- On the `7042…` APK, E2E setup `enrollAndRead` passed against the Hub on port 8794 (`OK (1 test)`, ADB exit 0), verifying encrypted capabilities and status. A fresh `cicada machine agent --once` reached the expected pending-device-code state before confirmation; Android `confirmDisposableNodeForQueuedGoal` verified the previewed Node ID and encrypted `nodes.confirm` (`OK (1 test)`, ADB exit 0). Its device code stayed in an external `0600` file.
- The completed native E2E accepted one real Codex approval on the original Thread and Worker attempt 1. The Worker and Goal completed, Intent resolved, and `goal.result` was present and byte-matched the 30-byte Node result. The exact runner commands, exits, redacted IDs and digests are in the validation report. This run used `7042…`, so it does not exercise the corrected Group timestamp verifier in `2762…`.
- E2E cleanup removed the proxy, temporary Node/Codex state, wrapper, device code, and test marker; the one-time Codex container used `--rm`. The provider environment was mounted read-only only into that isolated Codex container, and no bearer or secret entered the Node process, APK, repository, or logs. The disposable Hub was stopped and removed; other Hub containers were left untouched. Sanitized evidence remains outside Git with restricted permissions.
- The later disposable Group fixture supplied a real native Codex Endpoint. Android validated the full manifest, imported an externally signed `signed_proof`, required an explicit on-device confirmation, and read encrypted `group.key_status=CURRENT` on final implementation commit `b676668e33c876c0d2c89bc3495fbd0d1e128c82`. The Owner private key stayed outside the APK. Exact APK hashes, commands and limits are in the [Group key acceptance report](client-group-key-v1.2.1-disposable-validation.md).
- The Android builds passed; build logs and exact commands/exits are in the validation report. The independent loss/recovery tests used the earlier candidate artifacts listed above. The candidate with the timestamp issue is not called final for Group-key validation.

## Final Group acceptance: result and evidence index

All rows below use the fixed Hub image in the baseline table and the final Group APK pair above. Let `E=/gpu1-share/data/cicada-client/group-key-v121-967dbd/evidence`; this directory is `0700`, its evidence files are `0600`, and its raw IDs, signed public proofs and logs are outside Git. Arguments containing IDs, one-time codes or proofs are omitted from the command column. The [full Group report](client-group-key-v1.2.1-disposable-validation.md) records the test order, hashed Endpoint/Session/Group/Node/binding labels, command exits and limits. It does not turn earlier `7042…` or `2762…` results into final-APK evidence.

| Check | Result | Command and exit code | Evidence under `$E` |
|---|---|---|---|
| Archive, clean source, exact image and encrypted capabilities | **PASS** | `python3 ../CICADA/scripts/client-contract.py verify <fixed archive>`: 0; `docker image inspect <full image ID>`: 0; `python3 "$E/run-android-test.py" "$E/external-capabilities.spec.json"`: 0, JUnit `OK (1 test)` | `fixed-target-provenance.json`, `external-capabilities.log` |
| Android device preparation, owner-signed enrollment and Node/Group setup | **PASS**, with the failed combined harness attempt below | `python3 "$E/run-android-test.py" "$E/prepare-device.spec.json"`: 0; `python3 "$E/run-android-test.py" "$E/reconcile-group.spec.json"`: 0, JUnit `OK (1 test)`; reviewed on-screen taps: 0 | `prepare-device.log`, `device-signer.private.log`, `external-capabilities.log`, `reconcile-group.log`, `confirm-node-after.private.log` |
| Real native Codex Join, separate candidate publication, complete Android manifest verification and external Owner signing | **PASS** | `python3 "$E/rapid-prepare-grant.py" finalui`: 0; embedded manifest instrumentation: 0, JUnit `OK (1 test)` | `codex-finalui.private.jsonl`, `native-finalui.private.json`, `manifest-finalui.log`, `group-signer-finalui.private.log` |
| Phone confirmation, encrypted `group.key_grant`, separate encrypted `group.key_status=CURRENT` | **PASS** | `python3 "$E/run-android-test.py" "$E/grant-finalui.spec.json"`: 0, JUnit `OK (1 test)`; reviewed on-screen tap: 0 | `grant-finalui.log`, `grant-finalui-dialog.private.png`, `grant-finalui.private.json` |
| Mutated Owner signature and wrong Owner key selection; nonexistent Group/Endpoint scope | **PASS**, limited to Client-side rejection and encrypted Hub business rejections | `python3 "$E/run-android-test.py" "$E/reject-tampered-proof-finalui.spec.json"`: 0; `python3 "$E/run-android-test.py" "$E/reject-wrong-scope.spec.json"`: 0; each JUnit `OK (1 test)` | `reject-tampered-proof-finalui.log`, `reject-wrong-scope.log` |
| Accepted grant after native lease expiry, then proof expiry | **PASS**: `STALE`, then `PROOF_EXPIRED` | `python3 "$E/run-android-test.py" "$E/status-stale-finalui.spec.json"`: 0; `python3 "$E/run-android-test.py" "$E/status-proof-expired-finalui.spec.json"`: 0; each JUnit `OK (1 test)` | `status-stale-finalui.log`, `status-proof-expired-finalui.log` |
| Early manager-role assertion and combined Node preview/Group runner | **FAIL** as harness attempts, not final protocol results | `python3 "$E/run-android-test.py" "$E/enroll-and-capabilities.spec.json"`: runner 1, ADB 0, JUnit `FAILURES!!!`; `python3 "$E/run-android-test.py" "$E/confirm-node-create-group.spec.json"`: runner 1, ADB 0, JUnit `FAILURES!!!` | `enroll-and-capabilities.log`, `confirm-node-create-group.log`; the code-bearing second spec was deleted at teardown; corrected external-role and Group runs above |
| Independent Hub submission of a tampered grant; second real Owner cross-owner authorization | **NOT_RUN** | No qualifying command; the Client fenced the tampered proof and the fixture enrolled only one Owner | No claim of server-side tamper or two-Owner rejection |
| Physical Android hardware; public HTTPS | **NOT_RUN** | No command run | Emulator loopback evidence does not establish either environment |

No item remains **BLOCKED** for the one-Owner positive path. The initial long Node state path blocked the Unix Join socket, but a short real private `/tmp` state directory enabled the final PASS; the fixture improvement request is below. The disposable Hub/Node, short state directory, ADB reverse mapping and emulator app data were removed with exit 0. The synthetic Node code briefly appeared in a transient process-inspection output; it expired and its private spec was deleted. The full report records that privacy finding and the evidence metadata class-name correction.

## Contract correction and outstanding validation

The previous `41beaf0` v1.2 report recorded a mismatch between the prose and Go implementation for `EndpointKeyAttestation` v1: the implementation signs compact JSON with a final `"signature":null` field. The fixed v1.2.1 wire contract documents those exact bytes and the bundle contains a public synthetic vector. This closes the published contract ambiguity. Candidate `70423555…` failed a valid four-digit RFC3339Nano fraction check; commit `af3ad451e29d0c43142b3fc792272bbd6df75c84` fixes it. The later disposable Group run on the same fixed image independently established the live positive path with a different final APK pair; the earlier Node/Codex and vector APKs are not counted as its proof.

The real Node/Codex approval run is **PASS** for this fixed image, as described above. The E2E topology snapshot/Endpoint group-membership probe, physical Android hardware, and public HTTPS are **NOT_RUN**. The earlier `41beaf0` result remains historical and is not the evidence used for this pass.

The first attempt to run the recovery fixture from a `/gpu1` working directory failed with HTTP 502 because the fixture requires a real `/tmp` root. The corrected disposable `/tmp` run passed. Both results are recorded as such; the failed attempt is not counted as a protocol failure or a PASS.

## Security and environment boundary

All current Hub tests used an isolated disposable state. No Android Client stores Hub or Node bearer credentials or reads the Hub database. Sensitive test material and raw logs stay outside Git under `/gpu1-share/data/cicada-client/hub-v1.2.1-967dbd/`; the report names only the required evidence paths. The Client uses independently pinned Hub identity, an owner-signed device Grant, encrypted `session.capabilities`, and encrypted `/v2/client/rpc`. It does not call legacy `/v1` bearer APIs.

This integration validates Client-to-Control management encryption. It does not prove ordinary peer messages are unreadable to the Hub. The fixed contract keeps `status_events=false` and `external_thread_links=false`; cross-user links remain proposals, not routable Threads. No real-device key boundary or public HTTPS conclusion follows from emulator results.

## Next actions

1. Preserve the fixed-image Group acceptance and original Node/Codex approval traces as separate evidence sets. The Group result uses APK `cfe34527…` and native Codex candidate publishing; the earlier Goal approval result uses APK `70423555…` and exercises a different path.
2. Use the separate two-Owner report above for cross-Owner authorization and Hub-side signature rejection. That gap is now closed for the fixed image with synthetic Endpoints; it does not replace native Thread evidence.
3. Run physical-device key/Keystore and public HTTPS tests before making deployment claims. Both remain **NOT_RUN**.

## Core fixture feedback

The core-side `client-group-key-fixture.sh` and its recipe enabled the positive acceptance without new production routes. One operational issue arose: its long `/tmp/cicada-client-group-key.*` directory plus long synthetic Node ID exceeded the Unix-domain Join socket path limit. The Node Agent exited before Join. A short symlink allowed the socket but was correctly rejected by Node-local Endpoint key storage, which requires a real directory. The Client run copied only this disposable Node state into a private short real `/tmp` directory and then completed the test; both state locations were removed during teardown. Please make the fixture allocate a sufficiently short **real** Node state path and document the same-path MCP settings. This is fixture ergonomics, not evidence of a fixed-image protocol change.

The synthetic Owner's encrypted `session.capabilities` role was `external`, with Group and topology operations in its allowlist. The Client's legacy manager-role enrollment assertion failed after successful enrollment; a separate external-role encrypted capabilities test passed. The core fixture documentation should state that role explicitly so a future runner does not mistake the expected role for a Hub fault.

### Copyable prompt for the CICADA core maintainer

```text
You own only the CICADA core repository. Do not edit CICADA_CLIENT or modify
production Hub state.

Fixed acceptance target (already tested):
- source commit: 967dbd885fae9a150b3d9a77c8e4e30da1d0dd8a
- contract: client-hub-v1.2.1
- protocol archive SHA-256: 7bf1e3702eadf9fc3ffd50a0e8ab1213db0844b2bf418b577b88ba239216bf8a
- catalog SHA-256: 25c3d7f585b1811781cb46669a09e2e08ab8c58765a7b9318145cea5bbce4df9
- image ID: sha256:adca1c62db5747625141be4506c4f3713368260076c50876776b4dabafa6c1b7
- Native Group implementation commit: b676668e33c876c0d2c89bc3495fbd0d1e128c82
- Two-Owner instrumentation/fixture commit: 680fac2bf12c431ac02804e5db168669cf12941f
- Native Group report: CICADA_CLIENT/docs/client-group-key-v1.2.1-disposable-validation.md
- Authorization report: CICADA_CLIENT/docs/client-two-owner-v1.2.1-967dbd-validation.md

The one-Owner disposable fixture succeeded: Android encrypted manifest,
independent full Endpoint verification, external Owner signing, explicit phone
confirmation, encrypted grant and status CURRENT. The same grant became STALE
after the native binding lease expired and PROOF_EXPIRED after the proof expired;
the Android Client rejected a mutated Owner signature before submission.
The later two-Owner authorization run also passed with synthetic Endpoints:
both own grants CURRENT; A-to-B and B-to-A manifest/status/grant denied inside
authenticated encrypted responses; a corrupted public signature rejected by
the Hub; revoked A new requests and original-packet RPC/recover rejected with
HTTP 403, while B remained usable. It does not repeat native Thread acceptance.

Read the reports and working tree read-only. Review the fixed identities,
exact APK attribution, command exits, redacted summaries and teardown records.
The first development attempt's compile and canonical-input failures are
retained separately from the successful final fresh run. No core interface
gap remains for this authorization slice; do not request another replacement
fixture or expand Client to unshipped Monitor operations. Preserve the fixed
image and contract; a new Hub build needs a clean commit and complete
archive/catalog/image identity before new acceptance.

Do not put Owner private keys or Node bearers in Android, Git or logs, and do
not inspect or mutate a live Hub database. Physical Android and public HTTPS
remain NOT_RUN until independently tested.
```
