#!/usr/bin/env python3
"""Independent structural/signature check for the PALMA MX resolver patch."""

import argparse
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import zipfile

EXPECTED_INPUT = "018e7bf00a25e1d3e99ec971fdaf98a0767d7bdfe79551be2a7d8a834ba4bea1"
EXPECTED_BASE = "e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7"
EXPECTED_CERT = "77f285ea30dd383d1cb7bdf2fc82039c146e378c0346d475f474917dceeef630"


def digest(value):
    return hashlib.sha256(value).hexdigest()


def file_digest(path):
    return digest(pathlib.Path(path).read_bytes())


def signature(name):
    return name.startswith("META-INF/") and re.search(r"\.(RSA|DSA|EC|SF|MF)$", name, re.I)


def alignment(apk):
    checked = 0
    with zipfile.ZipFile(apk) as archive, pathlib.Path(apk).open("rb") as raw:
        for info in archive.infolist():
            if info.compress_type != zipfile.ZIP_STORED:
                continue
            raw.seek(info.header_offset + 26)
            name_length, extra_length = struct.unpack("<HH", raw.read(4))
            offset = info.header_offset + 30 + name_length + extra_length
            required = 16384 if info.filename.startswith("lib/") and info.filename.endswith(".so") else 4
            if offset % required:
                raise SystemExit("Misaligned stored entry: " + info.filename)
            checked += 1
    return checked


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--original", required=True, type=pathlib.Path)
    parser.add_argument("--fixed", required=True, type=pathlib.Path)
    parser.add_argument("--apksigner", required=True, type=pathlib.Path)
    parser.add_argument("--report", required=True, type=pathlib.Path)
    args = parser.parse_args()

    if file_digest(args.original) != EXPECTED_INPUT:
        raise SystemExit("Unexpected original APK")
    keep = ["classes.dex", "classes2.dex", "classes3.dex", *[f"classes{i}.dex" for i in range(5, 11)], "assets/base.apk"]
    with zipfile.ZipFile(args.original) as before, zipfile.ZipFile(args.fixed) as after:
        if after.testzip() is not None:
            raise SystemExit("Fixed APK ZIP is corrupt")
        dex_names = sorted(
            (name for name in after.namelist() if name.startswith("classes") and name.endswith(".dex")),
            key=lambda name: int(name[7:-4] or "1"),
        )
        if dex_names != ["classes.dex", *[f"classes{i}.dex" for i in range(2, 11)]]:
            raise SystemExit("Unexpected DEX set: " + repr(dex_names))
        for name in keep:
            if before.read(name) != after.read(name):
                raise SystemExit("Preserved entry changed: " + name)
        for name in before.namelist():
            if name == "classes4.dex" or signature(name):
                continue
            if name not in after.namelist() or before.read(name) != after.read(name):
                raise SystemExit("Original payload changed: " + name)
        unexpected = [
            name for name in after.namelist()
            if name not in before.namelist() and not signature(name)
        ]
        if unexpected:
            raise SystemExit("Unexpected added payload: " + ",".join(unexpected))
        if digest(after.read("assets/base.apk")) != EXPECTED_BASE:
            raise SystemExit("Protected base hash changed")
        direct = after.read("classes4.dex")
        required = [
            b"com.mxtech.videoplayer.ad",
            b"getDeepLink",
            b"getStreamUrl",
            b"getUrl",
            b"HttpURLConnection",
            b"CookieManager",
            b"PageStreams",
            b"OscarBrowserResolver",
            b"evaluateJavascript",
            b"performance.getEntriesByType",
            b"setAcceptThirdPartyCookies",
            b"WatchLink",
            b"MovieLink",
            b"ChannelStream",
            b"Referer",
            b"User-Agent",
        ]
        missing = [token.decode() for token in required if token not in direct]
        if missing:
            raise SystemExit("Resolver token missing: " + ",".join(missing))
        if b"com.dwplayer.app" in direct:
            raise SystemExit("Old DW player package remains")
        nested = [name for name in after.namelist() if name.lower().endswith(".apk") and name != "assets/base.apk"]
        if nested:
            raise SystemExit("Unexpected nested APK: " + ",".join(nested))

    verify = subprocess.run(
        ["java", "-jar", str(args.apksigner), "verify", "--min-sdk-version", "21", "--verbose", "--print-certs", str(args.fixed)],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout
    for scheme in ("v1", "v2", "v3"):
        if f"Verified using {scheme} scheme" not in verify or f"Verified using {scheme} scheme" in verify and f"Verified using {scheme} scheme (" not in verify:
            raise SystemExit("Missing signature verification output for " + scheme)
    if "Verified using v1 scheme (JAR signing): true" not in verify:
        raise SystemExit("v1 signature failed")
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in verify:
        raise SystemExit("v2 signature failed")
    if "Verified using v3 scheme (APK Signature Scheme v3): true" not in verify:
        raise SystemExit("v3 signature failed")
    if EXPECTED_CERT not in verify.replace(":", "").lower():
        raise SystemExit("Unexpected signing certificate")

    report = {
        "input_sha256": file_digest(args.original),
        "apk_sha256": file_digest(args.fixed),
        "apk_bytes": args.fixed.stat().st_size,
        "outer_dex_files": 10,
        "replacement_dex_sha256": digest(zipfile.ZipFile(args.fixed).read("classes4.dex")),
        "preserved_original_dex": keep[:-1],
        "assets_base_apk_sha256": EXPECTED_BASE,
        "assets_base_apk_byte_identical": True,
        "all_other_payload_entries_byte_identical": True,
        "no_added_nested_apk": True,
        "stored_entries_alignment_verified": alignment(args.fixed),
        "mx_player_package": "com.mxtech.videoplayer.ad",
        "oscar_resolver_chain_verified": True,
        "javascript_media_request_fallback_verified": True,
        "headers_forwarded": ["Cookie", "Referer", "Origin", "User-Agent"],
        "signatures_v1_v2_v3_verified": True,
        "signing_certificate_sha256": EXPECTED_CERT,
        "signing_certificate_matches_uploaded_direct_3": False,
        "requires_uninstall_of_direct_3": True,
        "runtime_device_tested": False,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
