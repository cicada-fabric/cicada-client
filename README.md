# CICADA Client

CICADA Client is the user interface to a CICADA fabric. It lets a user inspect authorized Nodes, Groups, Goals, Workers, and approvals; submit text or locally transcribed speech to Control; and manage the Hub's authoritative state. Control usually runs on a Hub, while native agent sessions run on Nodes. The Client does not inject input directly into a native Worker session.

The product is designed for multiple client platforms. **Android is the only implementation currently built and tested.** The shared application is written in TypeScript and React Native, with Kotlin adapters for Android device security and on-device speech recognition. Future iOS and native HarmonyOS clients will follow the same product behavior and reviewed protocol; their implementation and validation remain separate work. See the [stack decision](docs/stack-decision.md).

## Current capabilities

- **Status and work:** Read authenticated `status.snapshot` and partial `status.changes`; distinguish the source and freshness of Node, Group, Goal, and Worker state. Submit confirmed text or speech transcripts with `intent.submit`, inspect `intent.status`, and, when authorized, query `goal.result` by `intent_id`. Intent dispatch, Goal state, Worker state, and bounded Artifact references are shown separately.
- **Local speech:** Download, verify, install, select, and remove supported Vosk, Paraformer, and SenseVoice models. Model weights are optional and are not bundled in the application. A text composer remains available without a model. Speech is transcribed on the device; the user reviews the text before submission. See [model selection](docs/model-selection.md) and [inference validation](docs/stt-validation.md).
- **Hub management:** Use encrypted, authorized `topology.*`, `nodes.*`, `approvals.*`, and owner device operations. The Hub remains authoritative for every write, including version checks and rejection. Cross-user links are presented as proposals while `external_thread_links=false`; there is no implied peer-message permission.
- **Recovery:** Persist the exact enrollment Grant request and each signed encrypted RPC packet before sending. Replay the same enrollment after a lost 201 response. Recover a lost RPC response through `/v2/client/rpc/recover` without creating a new operation. Preserve unresolved ciphertext and counters, and require authoritative reconciliation after an authenticated uncertain outcome.

Feature availability comes from encrypted `session.capabilities`, including the contract revision, catalog hash, role, and operation allowlist. An unauthenticated public capability response cannot grant access. When `status_events=false`, the Client uses partial changes and periodic snapshot reconciliation rather than presenting a complete live event feed.

## Security boundary

Client ↔ Control business RPC uses application-layer ML-KEM-768, ML-DSA-65, and AES-256-GCM. The user must verify and pin the full Hub identity through an independent trusted channel. A device is enrolled only with an independently signed, one-time OwnerDeviceGrant; its private key is wrapped by Android Keystore in the current implementation. Release builds require HTTPS. Local debug HTTP is restricted to emulator-to-host development addresses. The Client does not use legacy `/v1` management bearer APIs, store Hub or Node bearer tokens, or read the Hub database.

The encrypted business channel terminates at Control, which must process the user's request. It does **not** prove that ordinary peer messages are unreadable to the Hub. Group key owner signing remains disabled: the fixed v1.2 wire contract and Hub implementation disagree on the Endpoint attestation's exact signed bytes. See [the interface request](docs/hub-interface-requests-v12.md).

## Development

The fixed integration target is CICADA Hub commit `01d51ece186a7ec53dc2a83b77e05085f939bd28`, contract `client-hub-v1.2`, catalog SHA-256 `613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a`, and local image ID `sha256:e31b4c5dc6fceb27932fbc4e5a43afac425b6ef0647a3c7f25788ff52b31585b`. The [imported contract](contracts/README.md) is verified against its source manifest. A mutable Docker tag or an older v1.1 test result does not establish this version.

Build the current Android implementation with Docker:

```bash
python3 scripts/check-client-contract.py
./scripts/docker-build.sh
./scripts/docker-build-android-test.sh
```

The debug APK is written to `/gpu1-share/data/cicada-client/build-output/cicada-client-debug.apk`. The scripts require Docker's data root under `/gpu1-share/data` and keep dependencies, models, test identities, logs, and build artifacts outside Git. The repository contains no production credentials. To run the clean-emulator UI and trust checks against the isolated v1.2 Hub:

```bash
CICADA_EMULATOR_VECTOR_CHECK=1 \
CICADA_EMULATOR_SECURITY_CHECK=1 \
CICADA_EMULATOR_HUB_BASE_URL=http://10.0.2.2:8789 \
./scripts/docker-emulator-check.sh
```

## Documentation and validation

- [Development constraints](AGENTS.md) and [product behavior](docs/product.md)
- [Verified backend contract](docs/backend-contract.md) and [development plan](docs/development-plan.md)
- [v1.2 validation matrix](docs/client-hub-v1.2-validation.md), including the exact image, commands, exits, evidence boundaries, and items not run
- [Requests for the CICADA core team](docs/hub-interface-requests-v12.md) and [handoff prompt](docs/cicada-core-handoff.md)

The v1.2 fixed-image Android emulator tests cover encrypted capabilities, enrollment and RPC response loss, replay and revocation, a queued Goal, and a synthetic Node protocol result. A real remote Codex Worker approval bridge, physical-device testing, and public HTTPS validation are still outstanding. The [v1.1 validation](docs/client-hub-v1.1-validation.md) is historical and is not counted for v1.2.
