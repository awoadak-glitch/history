using Microsoft.Win32;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace PalmaDesktop
{
    internal sealed class AndroidInstance
    {
        public string Id { get; set; }
        public string Label { get; set; }
        public int Port { get; set; }
        public string ConfigPath { get; set; }
    }
    internal sealed class CommandResult { public int Code; public string Output; }
    internal sealed class RuntimeService
    {
        public const string Package = "com.drama.mp4";
        public const string ApkSha = "e433d9e12233d7412cb624821ec29cc011b30a0bfb07eb84d18292db75eaabe1";
        public static readonly string[] Companions = { "com.tdm.manager", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro" };
        public string Root { get; private set; }
        public AndroidInstance Selected;
        public string ApkPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "app", "PALMA-VISION-1.1.7.apk");
        public readonly List<string> Report = new List<string> { "PALMA Desktop 1.0 / Windows x64", "Catalog and video playback: not verified by this launcher." };
        public Action<string, string> Progress = delegate { };
        string Adb => Path.Combine(Root ?? "", "HD-Adb.exe");
        string Player => Path.Combine(Root ?? "", "HD-Player.exe");
        public bool Ready => File.Exists(Adb) && File.Exists(Player);
        string Serial
        {
            get
            {
                if (Selected == null || Selected.Port < 1024 || Selected.Port > 65535)
                    throw new InvalidOperationException("فعّل Android Debug Bridge من إعدادات BlueStacks ← Advanced ثم احفظ الإعدادات واضغط تحديث.");
                return "127.0.0.1:" + Selected.Port;
            }
        }
        public static bool SafeInstance(string id) => id != null && Regex.IsMatch(id, @"^(Pie64|Rvc64|Tiramisu64)(_[0-9]+)?$");
        public static List<AndroidInstance> ParseConfig(string content, string path)
        {
            var map = new Dictionary<string, AndroidInstance>();
            foreach (Match m in Regex.Matches(content, "(?m)^bst\\.instance\\.([A-Za-z0-9_]+)\\.(display_name|adb_port|status\\.adb_port)=\"([^\"\\r\\n]*)\"\\s*$"))
            {
                string id = m.Groups[1].Value;
                if (!SafeInstance(id)) continue;
                if (!map.TryGetValue(id, out var item)) map[id] = item = new AndroidInstance { Id = id, Label = id, ConfigPath = path };
                if (m.Groups[2].Value == "display_name") item.Label = m.Groups[3].Value + " · " + id;
                else if (int.TryParse(m.Groups[3].Value, out int port) && port >= 1024 && port <= 65535) item.Port = port;
            }
            return map.Values.OrderBy(x => x.Id.StartsWith("Pie64") ? 0 : 1).ThenBy(x => x.Id).ToList();
        }
        public void SetRoot(string root)
        {
            if (!File.Exists(Path.Combine(root, "HD-Player.exe")) || !File.Exists(Path.Combine(root, "HD-Adb.exe")))
                throw new InvalidOperationException("اختر HD-Player.exe من مجلد تثبيت BlueStacks 5 الذي يحتوي أيضًا على HD-Adb.exe.");
            Root = root;
        }
        public List<AndroidInstance> Discover()
        {
            var roots = new List<string>();
            var configs = new List<string> { Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "BlueStacks_nxt", "bluestacks.conf") };
            if (Root != null) roots.Add(Root);
            foreach (var view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                try
                {
                    using (var machine = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
                    using (var key = machine.OpenSubKey(@"SOFTWARE\BlueStacks_nxt"))
                    {
                        var install = key?.GetValue("InstallDir") as string;
                        var data = key?.GetValue("UserDefinedDir") as string;
                        if (!string.IsNullOrWhiteSpace(install)) roots.Add(install);
                        if (!string.IsNullOrWhiteSpace(data)) configs.Add(Path.Combine(data, "bluestacks.conf"));
                    }
                }
                catch (System.Security.SecurityException) { }
                catch (UnauthorizedAccessException) { }
            }
            roots.Add(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "BlueStacks_nxt"));
            roots.Add(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "BlueStacks_nxt"));
            Root = roots.FirstOrDefault(r => File.Exists(Path.Combine(r, "HD-Player.exe")) && File.Exists(Path.Combine(r, "HD-Adb.exe")));
            var all = new List<AndroidInstance>();
            foreach (var path in configs.Distinct(StringComparer.OrdinalIgnoreCase))
            {
                try { if (File.Exists(path)) all.AddRange(ParseConfig(File.ReadAllText(path), path)); }
                catch (IOException) { }
                catch (UnauthorizedAccessException) { }
            }
            return all.GroupBy(x => x.Id).Select(g => g.Last()).ToList();
        }
        public static string Quote(string input)
        {
            if (input == null || input.IndexOfAny(new[] { '\0', '\r', '\n' }) >= 0) throw new ArgumentException("Invalid process argument.");
            var b = new StringBuilder("\""); int slashes = 0;
            foreach (char c in input)
            {
                if (c == '\\') { slashes++; continue; }
                if (c == '"') b.Append('\\', slashes * 2 + 1).Append('"');
                else b.Append('\\', slashes).Append(c);
                slashes = 0;
            }
            return b.Append('\\', slashes * 2).Append('"').ToString();
        }
        internal static async Task<CommandResult> Run(string exe, IEnumerable<string> args, int seconds, CancellationToken cancel)
        {
            cancel.ThrowIfCancellationRequested();
            using (var p = new Process { StartInfo = new ProcessStartInfo(exe, string.Join(" ", args.Select(Quote)))
            { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true, StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8 } })
            {
                p.Start();
                var stdout = p.StandardOutput.ReadToEndAsync(); var stderr = p.StandardError.ReadToEndAsync();
                using (cancel.Register(() => { try { if (!p.HasExited) p.Kill(); } catch (InvalidOperationException) { } }))
                {
                    bool exited = await Task.Run(() => p.WaitForExit(seconds * 1000));
                    if (!exited) { try { p.Kill(); } catch (InvalidOperationException) { } throw new TimeoutException("استغرق الاتصال وقتًا طويلًا. تحقق من BlueStacks ثم أعد المحاولة."); }
                    cancel.ThrowIfCancellationRequested();
                    return new CommandResult { Code = p.ExitCode, Output = (await stdout) + (await stderr) };
                }
            }
        }
        Task<CommandResult> On(string serial, CancellationToken c, int timeout, params string[] args)
            => Run(Adb, new[] { "-s", serial }.Concat(args), timeout, c);
        Task<CommandResult> Device(CancellationToken c, int timeout, params string[] args) => On(Serial, c, timeout, args);
        public void StartEngine()
        {
            if (!Ready || Selected == null || !SafeInstance(Selected.Id)) throw new InvalidOperationException("ثبّت BlueStacks 5 وأنشئ نسخة Pie 64-bit أو Android 11 تدعم ARM 64 bit، ثم اضغط تحديث.");
            Process.Start(new ProcessStartInfo(Player, "--instance " + Quote(Selected.Id)) { UseShellExecute = true, WorkingDirectory = Root });
        }
        void UpdatePort()
        {
            if (Selected == null || !File.Exists(Selected.ConfigPath)) return;
            var updated = ParseConfig(File.ReadAllText(Selected.ConfigPath), Selected.ConfigPath).FirstOrDefault(x => x.Id == Selected.Id);
            if (updated != null) Selected.Port = updated.Port;
        }
        public async Task Connect(bool start, CancellationToken c)
        {
            if (!Ready || Selected == null) throw new InvalidOperationException("اختر نسخة BlueStacks متوافقة أولًا. استخدم دليل الإعداد.");
            if (start) StartEngine();
            Progress("جارٍ الاتصال ببيئة التشغيل", "ننتظر اكتمال تشغيل Android. قد تستغرق المرة الأولى دقيقتين.");
            var deadline = DateTime.UtcNow.AddSeconds(start ? 150 : 15);
            do
            {
                c.ThrowIfCancellationRequested(); UpdatePort();
                if (Selected.Port >= 1024 && Selected.Port <= 65535)
                {
                    try
                    {
                        await Run(Adb, new[] { "connect", Serial }, 8, c);
                        var boot = await Device(c, 8, "shell", "getprop", "sys.boot_completed");
                        if (boot.Code == 0 && boot.Output.Trim() == "1") return;
                    }
                    catch (TimeoutException) { /* A booting engine can temporarily stop answering. */ }
                }
                await Task.Delay(1500, c);
            } while (DateTime.UtcNow < deadline);
            throw new InvalidOperationException("تعذر الاتصال. افتح نسخة BlueStacks المختارة، وفعّل Android Debug Bridge من Settings ← Advanced ثم Save changes. بعد وصولك إلى شاشة Android اضغط فحص الجاهزية.");
        }
        public static void RequireArchitecture(string sdk, string abis)
        {
            if (!int.TryParse(sdk.Trim(), out int version) || version < 28)
                throw new InvalidOperationException("هذه النسخة تحتاج Android 9 أو أحدث. أنشئ Pie 64-bit أو Android 11.");
            if (!abis.Trim().Split(',').Contains("arm64-v8a"))
                throw new InvalidOperationException("ARM64 غير متاح. أنشئ نسخة جديدة واختر ABI ← Custom ← ARM 64 bit في BlueStacks.");
        }
        Task<CommandResult> PackageInfo(string package, CancellationToken c) => Device(c, 15, "shell", "dumpsys", "package", package);
        static bool Installed(CommandResult info) => info.Code == 0 && Regex.IsMatch(info.Output, @"\bversionCode=\d+");
        public async Task<List<string>> Check(CancellationToken c)
        {
            var sdk = await Device(c, 10, "shell", "getprop", "ro.build.version.sdk");
            var abis = await Device(c, 10, "shell", "getprop", "ro.product.cpu.abilist");
            RequireArchitecture(sdk.Output, abis.Output);
            Report.Add("Android API: " + sdk.Output.Trim() + "; ABI: " + abis.Output.Trim());
            var missing = new List<string>();
            foreach (var package in Companions.Take(2))
            {
                bool installed = Installed(await PackageInfo(package, c));
                Report.Add(package + ": " + (installed ? "installed; modified build compatibility not verified" : "missing"));
                if (!installed) missing.Add(package == Companions[0] ? "TDM المعدّل" : "MX Player الخاص ببالما");
            }
            return missing;
        }
        public static void VerifyApk(string path)
        {
            if (!File.Exists(path)) throw new FileNotFoundException("اختر ملف بالما الأصلي المرفق بالحزمة.");
            using (var input = File.OpenRead(path)) using (var sha = SHA256.Create())
            {
                string actual = BitConverter.ToString(sha.ComputeHash(input)).Replace("-", "").ToLowerInvariant();
                if (actual != ApkSha) throw new InvalidDataException("هذا ليس ملف بالما 1.1.7 الذي فُحص لهذه الحزمة. اختر PALMA-VISION-1.1.7-NO-ADS-DOWNLOAD-FIX.apk الأصلي.");
            }
        }
        public async Task InstallPalma(CancellationToken c)
        {
            Progress("جارٍ تجهيز بالما", "نتحقق من سلامة الملف ونثبت النسخة الأصلية بمعمارية ARM64.");
            await Task.Run(() => VerifyApk(ApkPath), c);
            string stamp = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PALMA-Desktop", Selected.Id + ".installed");
            var current = await PackageInfo(Package, c);
            if (File.Exists(stamp) && File.ReadAllText(stamp) == ApkSha && Installed(current)
                && Regex.IsMatch(current.Output, @"\bversionCode=17\b") && current.Output.Contains("primaryCpuAbi=arm64-v8a")) return;
            var result = await Device(c, 180, "install", "-r", "--abi", "arm64-v8a", ApkPath);
            RequireInstallSuccess(result);
            current = await PackageInfo(Package, c);
            if (!Installed(current) || !current.Output.Contains("primaryCpuAbi=arm64-v8a"))
                throw new InvalidOperationException("لم يتم تأكيد تثبيت ARM64. استخدم نسخة BlueStacks مع ARM 64 bit فقط ثم أعد المحاولة.");
            Directory.CreateDirectory(Path.GetDirectoryName(stamp)); File.WriteAllText(stamp, ApkSha);
            Report.Add("Original PALMA APK SHA256 verified; installed as arm64-v8a.");
        }
        public static string InstallError(string output)
        {
            if (output.Contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) return "يوجد تطبيق بتوقيع مختلف. لم تتم إزالته. استخدم نسخة BlueStacks جديدة لحماية بياناته.";
            if (output.Contains("INSTALL_FAILED_NO_MATCHING_ABIS")) return "معمارية التطبيق غير متوافقة. اختر ARM 64 bit في نسخة BlueStacks الجديدة.";
            if (output.Contains("INSTALL_FAILED_VERSION_DOWNGRADE")) return "توجد نسخة أحدث. لم يتم الرجوع إلى إصدار أقدم؛ استخدم نسخة BlueStacks منفصلة.";
            if (output.Contains("INSTALL_FAILED_INSUFFICIENT_STORAGE")) return "لا توجد مساحة كافية داخل Android. أفرغ مساحة ثم حاول مجددًا.";
            string code = Regex.Match(output, @"INSTALL_FAILED_[A-Z_]+").Value;
            return "لم يكتمل التثبيت. " + (code == "" ? "تحقق من الملف واتصال BlueStacks." : code);
        }
        static void RequireInstallSuccess(CommandResult result)
        { if (result.Code != 0 || !Regex.IsMatch(result.Output, @"(?m)^Success\s*$")) throw new InvalidOperationException(InstallError(result.Output)); }
        public async Task Launch(CancellationToken c)
        {
            Progress("جارٍ فتح بالما", "سيظهر التطبيق الأصلي بقوائمه داخل نافذة BlueStacks.");
            var result = await Device(c, 30, "shell", "am", "start", "-W", "-n", Package + "/com.drama.mp4.ui.splash.SplashActivity");
            if (result.Code != 0 || Regex.IsMatch(result.Output, @"(?im)^Error")) throw new InvalidOperationException("تعذر فتح شاشة بالما. افتح التطبيق من شاشة Android لمراجعة الرسالة.");
            await Task.Delay(4000, c);
            var process = await Device(c, 10, "shell", "pidof", Package);
            if (process.Code != 0 || !Regex.IsMatch(process.Output.Trim(), @"^\d+( \d+)*$"))
                throw new InvalidOperationException("أُرسل طلب التشغيل لكن لم يبقَ بالما مفتوحًا. تحقق من ARM64 وافتحه داخل BlueStacks لمعرفة السبب.");
            Report.Add("PALMA process was running 4 seconds after launch. Catalog/video still require in-app verification.");
        }
        public async Task InstallCompanion(string file, CancellationToken c)
        {
            string temp = Path.Combine(Path.GetTempPath(), "palma-companion-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(temp);
            try
            {
                var files = new List<string>();
                if (Path.GetExtension(file).Equals(".apk", StringComparison.OrdinalIgnoreCase)) files.Add(file);
                else
                {
                    using (var zip = ZipFile.OpenRead(file))
                    {
                        var entries = zip.Entries.Where(e => e.FullName.EndsWith(".apk", StringComparison.OrdinalIgnoreCase)).ToList();
                        if (entries.Count == 0 || entries.Count > 80 || entries.Sum(e => e.Length) > 1024L * 1024 * 1024)
                            throw new InvalidDataException("حزمة الملحق غير صالحة أو كبيرة جدًا.");
                        foreach (var entry in entries)
                        {
                            c.ThrowIfCancellationRequested();
                            var destination = Path.Combine(temp, files.Count.ToString("D3") + ".apk");
                            entry.ExtractToFile(destination); files.Add(destination);
                        }
                    }
                }
                var metadata = files.Select(ApkMetadata.Read).ToList();
                if (metadata.Count == 0 || !Companions.Contains(metadata[0].Package) || metadata.Any(m => m.Package != metadata[0].Package) || metadata.Count(m => m.Split == "") != 1)
                    throw new InvalidDataException("اختر APK أو APKS واحدًا خاصًا بـ TDM أو MX Player. الحزمة يجب أن تحتوي نسخة أساسية واحدة وملحقاتها.");
                var abis = new HashSet<string>(metadata.SelectMany(m => m.Abis));
                var args = new List<string> { files.Count == 1 ? "install" : "install-multiple", "-r" };
                if (abis.Contains("arm64-v8a")) args.AddRange(new[] { "--abi", "arm64-v8a" });
                else if (abis.Contains("armeabi-v7a")) args.AddRange(new[] { "--abi", "armeabi-v7a" });
                args.AddRange(files);
                Progress("جارٍ تثبيت ملحق المشاهدة", "يُثبّت التطبيق داخل النسخة المختارة من BlueStacks.");
                RequireInstallSuccess(await Device(c, 240, args.ToArray()));
                Report.Add("Companion installed: " + metadata[0].Package + "; source supplied by user.");
            }
            finally { try { Directory.Delete(temp, true); } catch (IOException) { } catch (UnauthorizedAccessException) { } }
        }
        public async Task<List<string>> Phones(CancellationToken c)
        {
            var result = await Run(Adb, new[] { "devices" }, 15, c);
            return Regex.Matches(result.Output, @"(?m)^([A-Za-z0-9_-]+)\s+device\s*$").Cast<Match>()
                .Select(m => m.Groups[1].Value).Where(s => !s.StartsWith("emulator-", StringComparison.Ordinal)).ToList();
        }
        public async Task<int> ImportPhone(string serial, CancellationToken c)
        {
            if (!Regex.IsMatch(serial, @"^[A-Za-z0-9_-]+$") || serial.StartsWith("emulator-")) throw new ArgumentException("اختر هاتف USB مصرحًا له.");
            int copied = 0;
            string temp = Path.Combine(Path.GetTempPath(), "palma-phone-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(temp);
            try
            {
                foreach (string package in Companions.Take(2))
                {
                    var paths = await On(serial, c, 15, "shell", "pm", "path", package);
                    var apks = paths.Output.Split('\n').Select(x => x.Trim()).Where(x => x.StartsWith("package:/data/app/", StringComparison.Ordinal)).Select(x => x.Substring(8)).ToList();
                    if (paths.Code != 0 || apks.Count == 0) continue;
                    if (apks.Count > 80 || apks.Any(p => !Regex.IsMatch(p, @"^/data/app/[A-Za-z0-9_./=+~\-]+\.apk$"))) throw new InvalidDataException("مسار APK غير متوقع على الهاتف.");
                    Progress("جارٍ نسخ تطبيق المشاهدة من الهاتف", "يتم نسخ ملفات التثبيت فقط؛ حساباتك وسجل المشاهدة يبقيان على الهاتف.");
                    string folder = Path.Combine(temp, package); Directory.CreateDirectory(folder);
                    for (int i = 0; i < apks.Count; i++)
                    {
                        string local = Path.Combine(folder, i.ToString("D3") + ".apk");
                        var pull = await On(serial, c, 120, "pull", apks[i], local);
                        if (pull.Code != 0 || !File.Exists(local)) throw new IOException("تعذر نسخ ملف التثبيت. أبقِ الهاتف موصولًا ومفتوحًا.");
                    }
                    string archive = folder + ".apks"; ZipFile.CreateFromDirectory(folder, archive, CompressionLevel.NoCompression, false);
                    await InstallCompanion(archive, c); copied++;
                }
                return copied;
            }
            finally { try { Directory.Delete(temp, true); } catch (IOException) { } catch (UnauthorizedAccessException) { } }
        }
    }
}
