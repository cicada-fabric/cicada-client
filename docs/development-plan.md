# CICADA Client development and validation plan

**Last updated:** 2026-09-25

This repository develops the mobile Client only. Android is the current target. The fixed integration baseline is Hub commit `967dbd885fae9a150b3d9a77c8e4e30da1d0dd8a`, contract `client-hub-v1.2.1`, protocol archive SHA-256 `7bf1e3702eadf9fc3ffd50a0e8ab1213db0844b2bf418b577b88ba239216bf8a`, catalog SHA-256 `25c3d7f585b1811781cb46669a09e2e08ab8c58765a7b9318145cea5bbce4df9`, and full local image ID `sha256:adca1c62db5747625141be4506c4f3713368260076c50876776b4dabafa6c1b7`. The [backend contract](backend-contract.md) describes the wire behavior; the [v1.2.1 validation report](client-hub-v1.2.1-967dbd-validation.md) records runtime evidence. Results for the earlier `41beaf0` image are historical only.

## Current status

| Area | State |
|---|---|
| Protocol package, manifest, image labels | **PASS** for the fixed target. The imported bundle records the exact commit, `source_dirty=false`, and catalog digest. |
| Android encrypted session | **PASS**. An independent Android session verified encrypted `session.capabilities` against the fixed Hub. |
| Android app/test build | **PASS** for the corrected artifacts listed in the validation report. TypeScript, lint, contract, and whitespace checks also exited 0. |
| Lost enrollment and RPC responses | **PASS**. Android retried the exact saved Owner Grant and recovered the exact RPC response on the fixed image. |
| Three recovery faults | **PASS** on a disposable `/tmp` Hub: `STILL_PROCESSING`, Hub restart after `FAULT_READY` yielding `OUTCOME_UNCERTAIN`, and `RECOVERY_UNAVAILABLE`. Android retained the original packet and did not create a second operation. |
| Endpoint attestation, synthetic manifest, and owner proof | **PASS** on corrected APK `2762a2defd13a0d85bc9a7c96647cbe626f632314ac958d4d1fe689ebd346b76`; `EndpointAttestationVectorTest` returned `OK (4 tests)`, ADB exit 0. The earlier `70423555…` candidate failed the valid four-digit RFC3339Nano timestamp case; commit `af3ad451e29d0c43142b3fc792272bbd6df75c84` fixes it. |
| Native Group preview/grant/status implementation | Implemented with external signed-proof import, explicit Android confirmation and a dedicated encrypted status read. The APK does not import the owner private key; it verifies against the owner public approval key saved from authenticated enrollment. The fixed-image live acceptance passed. |
| Positive Group grant against the fixed Hub | **PASS** on a disposable fixed-image Hub with a real native Codex Endpoint, external Owner signing, explicit Android confirmation, and encrypted `group.key_status=CURRENT`. The same grant became `STALE` after the native lease expired, then `PROOF_EXPIRED` after proof expiry. Exact artifacts and limits are in the [Group key acceptance report](client-group-key-v1.2.1-disposable-validation.md). |
| Real Node/Codex approval | **PASS** on the fixed image with E2E candidate APK `70423555e96381722d1bdc46633d32c0fca6dc32edae4dfb45865abc1059d197`: one original Codex turn was approved on the same native Thread/Worker attempt 1, Worker and Goal completed, Intent resolved, and `goal.result` matched 30 bytes. This does not validate Group grant or production TLS. |
| Physical Android device and public HTTPS | **NOT_RUN**. |

The full command and evidence record is in [the v1.2.1 validation report](client-hub-v1.2.1-967dbd-validation.md). An initial recovery-fixture attempt failed with HTTP 502 because the fixture was not run from `/tmp`; the corrected disposable-Hub runs passed and both results are recorded separately. The four-digit RFC3339Nano compatibility issue found in one candidate was fixed, rebuilt, and retested successfully.

## Product and model work

The app retains the five-page Android UI, a clear voice entry point, editable transcripts, and a text composer that works without a speech model. The model centre offers only models with a real format/runtime/device validation record. Model weights are optional, verified before installation, and kept in app-private storage. See [model selection](model-selection.md) and [speech validation](stt-validation.md) for model sources, licenses, command results, timings, and limitations. Host inference does not count as physical Android testing; microphone, memory, battery, and background-resume measurements on a physical device remain **NOT_RUN**.

State views distinguish Node connectivity, native session binding, Worker execution, Goal lifecycle, Intent dispatch, and Artifact references. They show authority, observation time, and stale state. Large lists load on demand. When the app is disconnected or unauthorized, it must not show development fixtures or claim that a message was sent, an approval was granted, a Goal completed, or a Thread connected.

## Remaining gates

1. Preserve the completed fixed-image Group key trace and add a separate two-Owner fixture when cross-owner authorization needs acceptance. The one-Owner disposable run established the positive path and rejected wrong key selection and nonexistent Group/Endpoint status reads; it did not test a second real Owner.
2. Preserve the completed fixed-image Node/Codex trace in the validation record. Any new Hub image or Client protocol change needs its own run; do not transfer results from historical `41beaf0` or the separate Group verifier artifact.
3. Test on a physical Android device: enrollment and Keystore boundary, foreground/background recovery, microphone capture, supported model download and inference, latency, memory, and battery. Mark each item `NOT_RUN` until measured.
4. Validate production-style public HTTPS, certificate checks, device network behavior, and recovery after network changes. Do not infer this from emulator-to-host HTTP.
5. Keep full status push, cross-user Thread routing, equipment migration/recovery, iOS, and native HarmonyOS as separate future gates. Native HarmonyOS requires an ArkUI/ArkTS app; the React Native TSX UI is not a native Ark application.

## Development and safety rules

- Develop, build, and test in Docker. Verify Docker data root `/gpu1-share/data/docker-root`; keep project cache, model downloads, APKs, test identities, and temporary Hub state under `/gpu1-share/data/cicada-client`. Do not alter `/gpu1-share/data/cicada` data or global Docker configuration.
- Work only in this Client repository. Treat the adjacent CICADA core as read-only. Use one-time disposable Hub state for integration tests; never inspect or edit its database from Android.
- Fixed dependencies use lock files. Do not put real API keys, long-lived bearer credentials, private keys, complete sensitive messages, audio recordings, or model-test personal audio in Git, APKs, snapshots, persistent caches, logs, or analytics.
- All user content and management operations use independently pinned Client-Control PQ encryption and encrypted `session.capabilities`. No old `/v1` bearer fallback. Every write uses server guards, expected versions, idempotency, and authoritative rereads after conflict or uncertain network outcome.
- A protocol package, public capability flag, Hub image tag, HTTP 200, or test fixture is not runtime proof. Record exact package, complete image ID, app artifact, command, exit code, outcome, and limits for each validation.
- Do not push, merge, publish, deploy production, rotate keys, or change global CLI/Docker configuration automatically.
