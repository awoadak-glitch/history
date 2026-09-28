#!/usr/bin/env python3
"""Small dependency-free Android binary XML dumper for manifest investigations."""

import argparse
import struct
import xml.sax.saxutils


STRING_POOL = 0x0001
START_NAMESPACE = 0x0100
END_NAMESPACE = 0x0101
START_ELEMENT = 0x0102
END_ELEMENT = 0x0103
CDATA = 0x0104
UTF8_FLAG = 0x100


def u16(data, offset):
    return struct.unpack_from("<H", data, offset)[0]


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def length8(data, offset):
    first = data[offset]
    if first & 0x80:
        return ((first & 0x7F) << 8) | data[offset + 1], offset + 2
    return first, offset + 1


def length16(data, offset):
    first = u16(data, offset)
    if first & 0x8000:
        return ((first & 0x7FFF) << 16) | u16(data, offset + 2), offset + 4
    return first, offset + 2


def string_pool(data, offset):
    header_size = u16(data, offset + 2)
    string_count = u32(data, offset + 8)
    flags = u32(data, offset + 16)
    strings_start = u32(data, offset + 20)
    offsets = [u32(data, offset + header_size + index * 4) for index in range(string_count)]
    values = []
    for relative in offsets:
        cursor = offset + strings_start + relative
        if flags & UTF8_FLAG:
            _, cursor = length8(data, cursor)
            byte_length, cursor = length8(data, cursor)
            values.append(data[cursor:cursor + byte_length].decode("utf-8", "replace"))
        else:
            char_length, cursor = length16(data, cursor)
            values.append(data[cursor:cursor + char_length * 2].decode("utf-16le", "replace"))
    return values


def index(strings, value):
    return "" if value == 0xFFFFFFFF else strings[value]


def typed(strings, raw, value_type, value):
    if raw != 0xFFFFFFFF:
        return index(strings, raw)
    if value_type == 0x03:
        return index(strings, value)
    if value_type == 0x12:
        return "true" if value else "false"
    if value_type == 0x10:
        return str(value)
    if value_type == 0x11:
        return hex(value)
    if value_type == 0x01:
        return "@0x%08x" % value
    return "0x%08x" % value


def dump(path):
    data = open(path, "rb").read()
    strings = []
    namespaces = {}
    depth = 0
    offset = u16(data, 2)
    while offset + 8 <= len(data):
        chunk_type = u16(data, offset)
        header_size = u16(data, offset + 2)
        size = u32(data, offset + 4)
        if size < 8 or offset + size > len(data):
            raise SystemExit("Invalid chunk at 0x%x" % offset)
        if chunk_type == STRING_POOL:
            strings = string_pool(data, offset)
        elif chunk_type == START_NAMESPACE:
            prefix = index(strings, u32(data, offset + 16))
            uri = index(strings, u32(data, offset + 20))
            namespaces[uri] = prefix
        elif chunk_type == END_NAMESPACE:
            uri = index(strings, u32(data, offset + 20))
            namespaces.pop(uri, None)
        elif chunk_type == START_ELEMENT:
            ns = index(strings, u32(data, offset + 16))
            name = index(strings, u32(data, offset + 20))
            attribute_start = u16(data, offset + 24)
            attribute_size = u16(data, offset + 26)
            attribute_count = u16(data, offset + 28)
            attrs = []
            cursor = offset + 16 + attribute_start
            for attribute in range(attribute_count):
                item = cursor + attribute * attribute_size
                attr_ns = index(strings, u32(data, item))
                attr_name = index(strings, u32(data, item + 4))
                raw = u32(data, item + 8)
                value_type = data[item + 15]
                value = u32(data, item + 16)
                prefix = namespaces.get(attr_ns, "")
                qualified = (prefix + ":" if prefix else "") + attr_name
                attrs.append((qualified, typed(strings, raw, value_type, value)))
            prefix = namespaces.get(ns, "")
            qualified = (prefix + ":" if prefix else "") + name
            rendered = "".join(" %s=\"%s\"" % (key, xml.sax.saxutils.escape(value, {'\"': '&quot;'})) for key, value in attrs)
            print("  " * depth + "<" + qualified + rendered + ">")
            depth += 1
        elif chunk_type == END_ELEMENT:
            depth = max(0, depth - 1)
            ns = index(strings, u32(data, offset + 16))
            name = index(strings, u32(data, offset + 20))
            prefix = namespaces.get(ns, "")
            qualified = (prefix + ":" if prefix else "") + name
            print("  " * depth + "</" + qualified + ">")
        elif chunk_type == CDATA:
            value = index(strings, u32(data, offset + 16))
            print("  " * depth + xml.sax.saxutils.escape(value))
        offset += size


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest")
    dump(parser.parse_args().manifest)
