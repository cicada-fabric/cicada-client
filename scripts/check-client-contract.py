#!/usr/bin/env python3
"""Verify the pinned public Client/Hub protocol snapshot without core tooling."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent / "contracts" / "client-hub-v1.2.1-967dbd"
SOURCE_REVISION = "967dbd885fae9a150b3d9a77c8e4e30da1d0dd8a"
CATALOG_SHA256 = "25c3d7f585b1811781cb46669a09e2e08ab8c58765a7b9318145cea5bbce4df9"


def main() -> None:
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["contract_revision"] == "client-hub-v1.2.1"
    assert manifest["source_revision"] == SOURCE_REVISION
    assert manifest["source_dirty"] is False
    assert manifest["catalog_sha256"] == CATALOG_SHA256
    assert manifest["wire_version"] == 1

    listed = manifest["files"]
    for name, expected in listed.items():
        path = Path(name)
        assert not path.is_absolute() and ".." not in path.parts, name
        contents = (ROOT / path).read_bytes()
        assert len(contents) == expected["bytes"], name
        assert hashlib.sha256(contents).hexdigest() == expected["sha256"], name

    actual = {
        path.relative_to(ROOT).as_posix()
        for path in ROOT.rglob("*")
        if path.is_file() and path.name != "manifest.json"
    }
    assert actual == set(listed), "snapshot contains missing or unlisted files"
    catalog_bytes = (ROOT / "cicada-go/internal/clientcontract/catalog.json").read_bytes()
    assert hashlib.sha256(catalog_bytes).hexdigest() == CATALOG_SHA256
    catalog = json.loads(catalog_bytes)
    assert catalog["contract_revision"] == manifest["contract_revision"]
    assert catalog["wire_version"] == manifest["wire_version"]
    print(f"PASS client-hub-v1.2.1 {CATALOG_SHA256} ({len(listed)} files)")


if __name__ == "__main__":
    main()
