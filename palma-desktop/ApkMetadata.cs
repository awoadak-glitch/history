using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;

namespace PalmaDesktop
{
    internal sealed class ApkMetadata
    {
        public string Package = "", Split = "";
        public HashSet<string> Abis = new HashSet<string>(StringComparer.Ordinal);

        public static ApkMetadata Read(string path)
        {
            using (var zip = ZipFile.OpenRead(path))
            {
                var entry = zip.GetEntry("AndroidManifest.xml");
                if (entry == null || entry.Length > 2 * 1024 * 1024)
                    throw new InvalidDataException("ملف APK لا يحتوي على تعريف Android صالح.");
                ApkMetadata data;
                using (var input = entry.Open()) using (var buffer = new MemoryStream())
                { input.CopyTo(buffer); data = ReadManifest(buffer.ToArray()); }
                foreach (var e in zip.Entries)
                {
                    var parts = e.FullName.Split('/');
                    if (parts.Length == 3 && parts[0] == "lib") data.Abis.Add(parts[1]);
                }
                return data;
            }
        }

        static void Bounds(byte[] b, int p, int count, int end)
        { if (p < 0 || count < 0 || end > b.Length || p > end - count) throw new InvalidDataException("تعريف APK تالف."); }
        static int U16(byte[] b, int p) { Bounds(b, p, 2, b.Length); return b[p] | b[p + 1] << 8; }
        static int I32(byte[] b, int p) { Bounds(b, p, 4, b.Length); return BitConverter.ToInt32(b, p); }
        static int Length8(byte[] b, ref int p, int end)
        {
            Bounds(b, p, 1, end); int n = b[p++];
            if ((n & 0x80) != 0) { Bounds(b, p, 1, end); n = ((n & 0x7f) << 8) | b[p++]; }
            return n;
        }
        static int Length16(byte[] b, ref int p, int end)
        {
            Bounds(b, p, 2, end); int n = U16(b, p); p += 2;
            if ((n & 0x8000) != 0) { Bounds(b, p, 2, end); n = ((n & 0x7fff) << 16) | U16(b, p); p += 2; }
            return n;
        }
        static string Lookup(List<string> strings, int index)
        { if (index < 0 || index >= strings.Count) throw new InvalidDataException("فهرس APK تالف."); return strings[index]; }

        public static ApkMetadata ReadManifest(byte[] b)
        {
            if (b.Length < 8 || U16(b, 0) != 3 || U16(b, 2) != 8 || I32(b, 4) != b.Length)
                throw new InvalidDataException("صيغة تعريف Android غير مدعومة.");
            var strings = new List<string>();
            for (int offset = 8; offset < b.Length;)
            {
                Bounds(b, offset, 8, b.Length);
                int type = U16(b, offset), header = U16(b, offset + 2), size = I32(b, offset + 4);
                if (size < header || header < 8) throw new InvalidDataException("حجم تعريف APK غير صالح.");
                Bounds(b, offset, size, b.Length); int end = offset + size;
                if (type == 1)
                {
                    if (header < 28) throw new InvalidDataException("جدول APK غير صالح.");
                    int count = I32(b, offset + 8), flags = I32(b, offset + 16), start = I32(b, offset + 20);
                    if (count < 0 || count > 100000 || start < header || start > size) throw new InvalidDataException("جدول APK غير صالح.");
                    Bounds(b, offset + header, checked(count * 4), offset + start);
                    for (int i = 0; i < count; i++)
                    {
                        int relative = I32(b, offset + header + i * 4);
                        if (relative < 0 || relative >= size - start) throw new InvalidDataException("موضع APK غير صالح.");
                        int p = offset + start + relative;
                        if ((flags & 0x100) != 0)
                        {
                            Length8(b, ref p, end); int n = Length8(b, ref p, end);
                            Bounds(b, p, n + 1, end); strings.Add(Encoding.UTF8.GetString(b, p, n));
                        }
                        else
                        {
                            int n = checked(Length16(b, ref p, end) * 2);
                            Bounds(b, p, checked(n + 2), end); strings.Add(Encoding.Unicode.GetString(b, p, n));
                        }
                    }
                }
                if (type == 0x102)
                {
                    Bounds(b, offset, 36, end);
                    if (Lookup(strings, I32(b, offset + 20)) == "manifest")
                    {
                        int start = U16(b, offset + 24), stride = U16(b, offset + 26), count = U16(b, offset + 28);
                        if (start < 20 || stride < 20) throw new InvalidDataException("خصائص APK غير صالحة.");
                        int p = checked(offset + 16 + start); Bounds(b, p, checked(stride * count), end);
                        var result = new ApkMetadata();
                        for (int i = 0; i < count; i++, p += stride)
                        {
                            string name = Lookup(strings, I32(b, p + 4));
                            int raw = I32(b, p + 8);
                            string value = raw >= 0 ? Lookup(strings, raw) : b[p + 15] == 3 ? Lookup(strings, I32(b, p + 16)) : "";
                            if (name == "package") result.Package = value;
                            if (name == "split") result.Split = value;
                        }
                        if (string.IsNullOrWhiteSpace(result.Package)) throw new InvalidDataException("هوية APK مفقودة.");
                        return result;
                    }
                }
                offset = end;
            }
            throw new InvalidDataException("تعريف التطبيق غير موجود.");
        }
    }
}
