#!/usr/bin/env python3
"""Verify the pinned public Client/Hub protocol snapshot without core tooling."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent / "contracts" / "client-hub-v1.2"
SOURCE_REVISION = "01d51ece186a7ec53dc2a83b77e05085f939bd28"
CATALOG_SHA256 = "613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a"


def main() -> None:
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["contract_revision"] == "client-hub-v1.2"
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
    print(f"PASS client-hub-v1.2 {CATALOG_SHA256} ({len(listed)} files)")


if __name__ == "__main__":
    main()
