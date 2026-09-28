#!/usr/bin/env python3
"""Replace only PALMA's direct UI/playback DEX in the verified direct.3 APK."""

import argparse
import hashlib
import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from build import write_aligned  # noqa: E402

EXPECTED_INPUT = "018e7bf00a25e1d3e99ec971fdaf98a0767d7bdfe79551be2a7d8a834ba4bea1"
EXPECTED_BASE = "e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7"


def sha_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha_file(path):
    return sha_bytes(pathlib.Path(path).read_bytes())


def signature(name):
    return name.startswith("META-INF/") and re.search(r"\.(RSA|DSA|EC|SF|MF)$", name, re.I)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=pathlib.Path)
    parser.add_argument("--dex", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--report", type=pathlib.Path)
    args = parser.parse_args()
    args.input = args.input.resolve()
    args.dex = args.dex.resolve()
    args.output = args.output.resolve()

    if sha_file(args.input) != EXPECTED_INPUT:
        raise SystemExit("Refusing an APK other than the verified PALMA direct.3 input")
    replacement = args.dex.read_bytes()
    if not replacement.startswith(b"dex\n"):
        raise SystemExit("Replacement is not a DEX file")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    preserved = []
    with zipfile.ZipFile(args.input) as source, zipfile.ZipFile(args.output, "w") as target:
        names = set(source.namelist())
        expected_dex = {"classes.dex", *{f"classes{i}.dex" for i in range(2, 11)}}
        if not expected_dex.issubset(names):
            raise SystemExit("PALMA DEX set is incomplete")
        if sha_bytes(source.read("assets/base.apk")) != EXPECTED_BASE:
            raise SystemExit("The protected base payload changed")
        for info in source.infolist():
            if signature(info.filename):
                continue
            data = replacement if info.filename == "classes4.dex" else source.read(info.filename)
            write_aligned(target, info.filename, data, info.compress_type)
            if info.filename != "classes4.dex":
                preserved.append(info.filename)

    with zipfile.ZipFile(args.input) as before, zipfile.ZipFile(args.output) as after:
        if after.testzip() is not None:
            raise SystemExit("Output ZIP is corrupt")
        for name in ["classes.dex", "classes2.dex", "classes3.dex", *[f"classes{i}.dex" for i in range(5, 11)], "assets/base.apk"]:
            if before.read(name) != after.read(name):
                raise SystemExit("Unexpected changed entry: " + name)
        if after.read("classes4.dex") != replacement:
            raise SystemExit("Replacement DEX was not installed")
        nested = [name for name in after.namelist() if name.lower().endswith(".apk") and name != "assets/base.apk"]
        if nested:
            raise SystemExit("Unexpected nested APK: " + ",".join(nested))

    report = {
        "input_sha256": sha_file(args.input),
        "unsigned_sha256": sha_file(args.output),
        "replacement_dex_sha256": sha_bytes(replacement),
        "replaced_entry": "classes4.dex",
        "preserved_outer_dex": ["classes.dex", "classes2.dex", "classes3.dex", *[f"classes{i}.dex" for i in range(5, 11)]],
        "assets_base_apk_sha256": EXPECTED_BASE,
        "assets_base_apk_byte_identical": True,
        "download_rows_untouched": True,
        "new_nested_apk": False,
        "preserved_entry_count": len(preserved),
    }
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
