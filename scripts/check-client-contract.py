#!/usr/bin/env python3
"""Verify the pinned public Client/Hub protocol snapshot without core tooling."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent / "contracts" / "client-hub-v1.1"
SOURCE_REVISION = "fb0f07a2330084b9402eb72878388bd1866bee10"
CATALOG_SHA256 = "f6f05783ddc00e51b92ebe050b5d8e6b9b185fae80d8fc6f04bc3143b6782374"


def main() -> None:
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["contract_revision"] == "client-hub-v1.1"
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
    catalog = json.loads((ROOT / "cicada-go/internal/clientcontract/catalog.json").read_text())
    assert catalog["contract_revision"] == manifest["contract_revision"]
    assert catalog["wire_version"] == manifest["wire_version"]
    print(f"PASS client-hub-v1.1 {CATALOG_SHA256} ({len(listed)} files)")


if __name__ == "__main__":
    main()
