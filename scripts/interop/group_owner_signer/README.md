# External Owner signer

This development utility stays outside the APK. It signs the fixed Client
OwnerDeviceGrant and Group Endpoint key grant formats with CICADA's own
`internal/e2ee` implementation. It performs no Hub or database requests.

The private identity must be the fixture's `owner-key generate --private`
file, which is the `e2ee.Identity.MarshalBinary()` format. Keep that file on
the trusted Owner machine with mode `0600`. The existing Java
`scripts/interop/OwnerGrantSigner.java` uses a different private file format
and does not implement Group grants.

## Build boundary

`internal/e2ee` can only be imported from a package under CICADA's module
`github.com/cicada-ai/cicada`. Copy `main.go` into a temporary clean archive of
the fixed `967dbd885fae9a150b3d9a77c8e4e30da1d0dd8a` source at
`cicada-go/cmd/groupownersigner/main.go`, then build it from that module with
Go 1.27.1. Do not build from the current CICADA worktree, which may contain
unrelated uncommitted source changes. Keep the archive, module cache and Go
build cache outside this repository.

## Commands

Device enrollment signing:

```text
group_owner_signer device-grant \
  --private OWNER_PRIVATE_JSON \
  --device-public ANDROID_DEVICE_PUBLIC_JSON \
  --owner OWNER_ID --device DEVICE_ID --hub PINNED_HUB_ID \
  --expect-device-key ANDROID_DEVICE_KEY_ID \
  --out OWNER_DEVICE_GRANT_JSON
```

Group Endpoint grant signing:

```text
group_owner_signer group-grant \
  --private OWNER_PRIVATE_JSON \
  --manifest VERIFIED_GROUP_MANIFEST_RESULT_JSON \
  --expect-owner OWNER_ID --expect-hub PINNED_HUB_ID \
  --expect-group GROUP_ID --expect-endpoint ENDPOINT_ID \
  --expect-node NODE_ID --expect-principal PRINCIPAL_ID \
  --expect-digest MANIFEST_DIGEST \
  --out OWNER_GROUP_KEY_PROOF_JSON
```

The Group command takes the exact `result` object returned by the Android
`group.key_manifest` preview. Do not pass the surrounding RPC response or a
`group.key_status` record. Compare the expected scope and manifest digest to
the Android screen. The signer checks the complete Endpoint attestation and
its digest, public key ID and fingerprint, all positive revisions and current
binding fields, and both domain-separated digests. It then displays those
values and requires the user to type the exact digest challenge before
signing. It rejects a manifest that expires while waiting for confirmation.

Both commands write a new `0600` proof file, refuse to overwrite an existing
file, and never print a private key or proof. The Group signature uses
`SignOwnerLinkKeyGrant` with operation `group-endpoint-key-grant:v1`, manifest
digest, candidate binding digest/version, `SOURCE`, and the manifest validity
interval. A proof is consent for that precise candidate only; the Hub still
rechecks current owner, membership, Node binding, revisions and expiry.
