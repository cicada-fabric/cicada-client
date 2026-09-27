# Android Client ↔ Hub contract: `client-hub-v1.2.1`

**Last updated:** 2026-09-25

This document follows the imported protocol bundle at [`contracts/client-hub-v1.2.1-967dbd`](../contracts/client-hub-v1.2.1-967dbd/manifest.json). The current fixed target is:

| Item | Value |
|---|---|
| Hub source revision | `967dbd885fae9a150b3d9a77c8e4e30da1d0dd8a` |
| Contract revision | `client-hub-v1.2.1` |
| Protocol archive SHA-256 | `7bf1e3702eadf9fc3ffd50a0e8ab1213db0844b2bf418b577b88ba239216bf8a` |
| Catalog SHA-256 | `25c3d7f585b1811781cb46669a09e2e08ab8c58765a7b9318145cea5bbce4df9` |
| Full Docker image ID | `sha256:adca1c62db5747625141be4506c4f3713368260076c50876776b4dabafa6c1b7` |

The archive and manifest checks and image labels passed. A separate Android session verified encrypted `session.capabilities`. Results are scoped to this fixed target in the [validation report](client-hub-v1.2.1-967dbd-validation.md); the earlier `41beaf0` report is historical and does not establish behavior of this image.

## Trust and authorization

The current wire protocol provides `GET /v2/client/identity`, `GET /v2/client/capabilities`, `POST /v2/client/devices/enroll`, `POST /v2/client/rpc`, and `POST /v2/client/rpc/recover`. The public capabilities response describes Hub implementation support. It does not authenticate a device or grant access.

Before enrollment, the user independently verifies and pins the full Hub ID and Control public identity. The device generates its own ML-KEM-768 and ML-DSA-65 key pair. An independently trusted owner signs a one-time `OwnerDeviceGrant` bound to the Hub, owner, device public identity, purpose, nonce, and expiry. The Client persists the exact enrollment bytes before the first POST. If the 201 response is lost, only the same bytes may be replayed. The Client does not self-approve enrollment or use a legacy bearer shortcut.

After enrollment, the first encrypted operation is `session.capabilities`. It supplies the authenticated owner, role, contract revision, catalog digest, recovery support, and operation allowlist. The Client requires both this encrypted authorization and local support for an operation. Server guards remain authoritative; no role, owner, or approval field supplied by the Client grants permission.

## Encrypted RPC and recovery

Every operation uses the Client-Control v1 wire framing and its exact route/AAD rules with ML-KEM-768, ML-DSA-65, HKDF, and AES-256-GCM. The device persists the request sequence, expected response sequence, operation ID, and exact signed ciphertext before sending. The server derives owner/device/epoch from the registered device and fences replayed or altered packets.

The encrypted RPC catalog includes session capabilities; status snapshots and partial changes; Intent submission, lookup, and status; Goal results and lifecycle; topology; device, Node, and Approval management; Link proposals and key evidence; and Group Endpoint key operations. Actual access depends on the authenticated role and encrypted allowlist. Catalog presence alone does not make a Client UI action available.

For a missing response, the Client submits the original request packet to `/v2/client/rpc/recover`. Recovery never dispatches the business operation again:

| Recovery result | Client behavior |
|---|---|
| Completed request | Accept the original cached encrypted response. |
| `STILL_PROCESSING` (HTTP 409) | Preserve the exact pending packet, operation ID, and sequence. Do not create another operation. |
| Signed encrypted `OUTCOME_UNCERTAIN` | Verify the signature, original operation ID, and reserved response sequence. Retire only the transport pending slot; retain the uncertain business outcome and reconcile authoritative state. |
| `RECOVERY_UNAVAILABLE` (HTTP 409) | Preserve the unresolved business evidence. Do not guess a response sequence or replay the operation. |
| `RECOVERY_REJECTED` | Permit retry only as an explicit user choice and only with the original packet. |

HTTP success does not imply business success. A business rejection is an authenticated encrypted result. Transport failure, Intent dispatch, Goal state, Worker execution, and model consumption remain separate states in the UI.

The current v1.2.1 fixed-image Android validation passed all three deterministic recovery cases on a disposable `/tmp` Hub with an independent Android session. The exact commands and limits are in the [validation report](client-hub-v1.2.1-967dbd-validation.md#recovery-faults-on-the-disposable-fixed-image). This evidence does not authorize direct Hub database access or a production fault switch.

## Status, Intent, and management operations

| Operation family | Contract behavior and Client boundary |
|---|---|
| `status.snapshot`, `status.changes` | Authenticated authoritative snapshot plus partial, cursor-based changes. Reconcile periodically with snapshots; this is not a complete push event feed. Display source, age, and stale state. |
| `intent.submit`, `intent.list/get/status` | Submit user-confirmed text or a reviewed local transcript. A completed Intent dispatch is not proof a Worker consumed input or a Goal completed. Read history and detail only on demand; responses may contain sensitive text and do not belong in durable Client caches or logs. |
| `goal.result`, `goal.lifecycle` | Results are owner-scoped and bounded. Lifecycle changes require an authorized role, current expected version, and fresh authoritative state. A queued Goal state is not a claim about a running native Worker. |
| `topology.snapshot/apply` | Versioned same-owner Group, Endpoint membership, role, and Link-proposal operations. A failed write or 409 requires a fresh authoritative read; parent Group permissions do not propagate to child Groups. |
| `nodes.preview/confirm/list/revoke` | Node generates and keeps its Node credential. Client displays a short code, previews it, then asks the user to confirm through encrypted RPC. Confirmation does not give the Client the Node credential. |
| `approvals.list/decide` | The authorized user decides a real pending approval. A Monitor cannot approve for a User. The approval body is Hub-visible management data; do not describe this path as peer-message blind encryption. |
| `devices.list/revoke` | Versioned owner-device management. The current Client must not offer self-revocation when the accepted response could no longer be delivered. |
| `link.*` | Owner-scoped invitations and key-consent records are proposals. The fixed contract advertises `external_thread_links=false`; these operations do not enable cross-user message routing. |
| `group.key_manifest/grant/status` | Fixed v1.2.1 returns a complete Endpoint attestation and owner grant fields. Android independently verifies the proof and binding, accepts an externally signed `signed_proof`, requires explicit confirmation, and reads the current Hub status through a dedicated encrypted method. A disposable fixed-image run with a real native Codex Endpoint reached `CURRENT`, then `STALE` after lease expiry and `PROOF_EXPIRED` after proof expiry. The Owner private key stayed outside the APK. See the [Group key acceptance report](client-group-key-v1.2.1-disposable-validation.md). |

The fixed wire contract keeps `status_events=false` and routable `external_thread_links=false`. Client status pages use partial changes and periodic snapshot reconciliation. Cross-user invitations must be described as proposals, never as a connected Thread or permission to send messages.

## Endpoint attestation v1 correction

The v1.2.1 bundle corrects the published Endpoint attestation signature input. `EndpointKeyAttestation` v1 is compact JSON with ordered fields `version, endpoint_id, principal_id, node_id, binding_id, binding_epoch, public_identity, signature`. To produce unsigned claims, the `signature` field remains present with JSON value `null`. The signature input is:

```text
UTF-8("cicada/fabric/endpoint-key-attestation/v1\x00") || compact_JSON(unsigned_claims)
```

The new public synthetic vector contains the complete proof, public identity, signed-input bytes, and proof digest. Android must verify the raw proof digest, ML-DSA-65 signature, public-key identity and fingerprint, all endpoint/principal/node/session-binding values and epochs, and the binding and manifest digests before accepting an owner grant. Candidate APK `70423555e96381722d1bdc46633d32c0fca6dc32edae4dfb45865abc1059d197` passed `EndpointAttestationVectorTest` (`OK (4 tests)`), covering the full proof, synthetic manifest digests, and owner proof. Subsequent review found that its timestamp check rejected valid RFC3339Nano timestamps with four fractional digits. That candidate failed the timestamp-compatibility case; the fix is in commit `af3ad451e29d0c43142b3fc792272bbd6df75c84`. The corrected APK `2762a2defd13a0d85bc9a7c96647cbe626f632314ac958d4d1fe689ebd346b76` and AndroidTest APK `711e00f9fee11582c81af14e5a2c9d77799412166a8a3cd7008ec7cb16d27ab0` passed a rebuild and `EndpointAttestationVectorTest` (`OK (4 tests)`, ADB exit 0). A self-attestation proves possession of an Endpoint key, not owner consent or current Hub authority.

## Runtime validation limits

- Package, manifest, and image-label checks: **PASS**.
- Encrypted Android `session.capabilities`: **PASS**.
- Recovery on a disposable fixed-image Hub: **PASS** for `STILL_PROCESSING`, restart-after-`FAULT_READY` `OUTCOME_UNCERTAIN`, and `RECOVERY_UNAVAILABLE`.
- Corrected Endpoint vector, synthetic-manifest, and owner-proof tests: **PASS** on APK `2762a2defd13a0d85bc9a7c96647cbe626f632314ac958d4d1fe689ebd346b76` (`EndpointAttestationVectorTest`, `OK (4 tests)`, ADB exit 0).
- RFC3339Nano four-digit fractional timestamp compatibility: **FAIL** on the earlier `70423555…` candidate; **PASS** after the fix in `af3ad451e29d0c43142b3fc792272bbd6df75c84`.
- Android native Group preview/grant path: implemented with external signed-proof import and explicit user confirmation. No owner private key is imported into the APK.
- Positive Group grant against the fixed Hub: **PASS** on the final Group APK with a real native Codex Endpoint, independent external Owner signature and on-device confirmation. Status transitioned from `CURRENT` to `STALE` when the native lease expired, then to `PROOF_EXPIRED` after the signed proof expired. See the [separate acceptance record](client-group-key-v1.2.1-disposable-validation.md) for exact artifacts and limits.
- Real Node/Codex approval against this fixed image: **PASS** on the emulator with APK `70423555…`; same native Thread and Worker attempt 1, one Android approval, Worker/Goal completed, Intent resolved, and `goal.result` byte-matched. It does not establish physical-device or public HTTPS validation.
- Physical Android device and public HTTPS: **NOT_RUN**.

See the [validation report](client-hub-v1.2.1-967dbd-validation.md) for commands, exits, evidence paths, and the recorded failed initial recovery attempt. Do not infer an unlisted operation's Android compatibility from this protocol document.
