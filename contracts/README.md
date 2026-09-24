# Pinned Client/Hub contract

`client-hub-v1.1/` is the unmodified public protocol export from CICADA commit
`fb0f07a2330084b9402eb72878388bd1866bee10`. Its source archive was
`client-hub-a6655205f0145ba1aa81aa968229fbcd1c6bfe052ab3e6364d7a32278d3f8cad.tar.gz`
with SHA-256 `a6655205f0145ba1aa81aa968229fbcd1c6bfe052ab3e6364d7a32278d3f8cad`.
The core `client-contract.py verify` command passed before extraction; this
repository's `python3 scripts/check-client-contract.py` checks every imported
file against the pinned manifest.

The public test vector includes deliberately published synthetic private keys,
public keys and ciphertext for deterministic interop tests. It is not a live
device identity or a Hub trust anchor. Never reuse these fixed private keys in
production or for a real test Owner/Hub. A matching catalog does
not authorize any operation: the app still requires a trusted Hub pin,
OwnerDeviceGrant, encrypted `session.capabilities`, and server Guard approval.
