#!/usr/bin/env python3
"""Read public signing-certificate fingerprints from a supplied APK, offline."""
import argparse
import hashlib
import io
import json
import struct
import zipfile
from pathlib import Path


def u32(data, offset):
    if offset < 0 or offset + 4 > len(data):
        raise ValueError("Truncated APK structure")
    return struct.unpack_from("<I", data, offset)[0]


def lp32(data, offset=0):
    size = u32(data, offset)
    end = offset + 4 + size
    if end > len(data):
        raise ValueError("Length-prefixed APK field exceeds its container")
    return data[offset + 4:end], end


def certificate(data):
    eocd = data.rfind(b"PK\x05\x06", max(0, len(data) - 65557))
    if eocd < 0 or eocd + 22 > len(data):
        raise ValueError("Missing ZIP end record")
    comment_size = struct.unpack_from("<H", data, eocd + 20)[0]
    if eocd + 22 + comment_size != len(data):
        raise ValueError("Invalid ZIP end record")
    central = u32(data, eocd + 16)
    if central < 32 or data[central - 16:central] != b"APK Sig Block 42":
        raise ValueError("APK has no supported v2/v3 signing block")
    size = struct.unpack_from("<Q", data, central - 24)[0]
    start = central - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        raise ValueError("APK signing-block size mismatch")
    position = start + 8
    candidates = {}
    while position < central - 24:
        if position + 12 > central - 24:
            raise ValueError("Truncated APK signing entry")
        length = struct.unpack_from("<Q", data, position)[0]
        end = position + 8 + length
        if length < 4 or end > central - 24:
            raise ValueError("Invalid APK signing entry")
        kind = u32(data, position + 8)
        if kind in (0x7109871A, 0xF05368C0):
            signers, _ = lp32(data[position + 12:end])
            signer, _ = lp32(signers)
            signed_data, _ = lp32(signer)
            _, next_field = lp32(signed_data)  # Digests
            certificates, _ = lp32(signed_data, next_field)
            first, _ = lp32(certificates)
            candidates[kind] = first
        position = end
    for kind in (0xF05368C0, 0x7109871A):
        if kind in candidates:
            return candidates[kind], "v3" if kind == 0xF05368C0 else "v2"
    raise ValueError("No v2/v3 signer certificate")


def profile(path, entry):
    data = Path(path).read_bytes()
    outer_cert, outer_scheme = certificate(data)
    source = data
    if entry:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            item = archive.getinfo(entry)
            if item.file_size > 100 * 1024 * 1024:
                raise ValueError("Embedded APK is unexpectedly large")
            source = archive.read(item)
    cert, scheme = certificate(source)
    return {
        "schema": 1,
        "input_apk_sha256": hashlib.sha256(data).hexdigest(),
        "certificate_source": entry or "outer APK",
        "source_apk_sha256": hashlib.sha256(source).hexdigest(),
        "certificate_sha256": hashlib.sha256(cert).hexdigest(),
        "signature_scheme": scheme,
        "outer_certificate_sha256": hashlib.sha256(outer_cert).hexdigest(),
        "outer_signature_scheme": outer_scheme,
        "note": "Public certificate identity only. No private signing key is read or required.",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--entry", default="assets/base.apk")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = json.dumps(profile(args.apk, args.entry), indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result)
    else:
        print(result, end="")
