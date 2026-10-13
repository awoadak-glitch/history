using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace PalmaDesktop
{
    internal static class Program
    {
        [STAThread]
        public static int Main(string[] args)
        {
            try
            {
                if (args.Length == 1 && args[0] == "--self-test") { SelfTest(); return 0; }
                var app = new Application(); var window = new MainWindow();
                if (args.Length >= 2 && args[0] == "--render")
                {
                    if (args.Length == 4) { window.Width = int.Parse(args[2]); window.Height = int.Parse(args[3]); }
                    window.Show(); window.UpdateLayout();
                    window.Dispatcher.Invoke(() => { }, DispatcherPriority.ApplicationIdle);
                    var bitmap = new RenderTargetBitmap((int)window.ActualWidth, (int)window.ActualHeight, 96, 96, PixelFormats.Pbgra32);
                    bitmap.Render(window);
                    var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
                    using (var output = File.Create(args[1])) encoder.Save(output);
                    window.Close(); return 0;
                }
                app.Run(window); return 0;
            }
            catch (Exception error)
            {
                string folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PALMA-Desktop");
                try { Directory.CreateDirectory(folder); File.WriteAllText(Path.Combine(folder, "startup-error.txt"), error.ToString()); } catch (IOException) { }
                if (args.Length == 0) MessageBox.Show("تعذر تشغيل واجهة PALMA.\n" + error.Message, "PALMA Desktop", MessageBoxButton.OK, MessageBoxImage.Error);
                return 1;
            }
        }
        [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
        static extern IntPtr CommandLineToArgvW(string command, out int argc);
        [DllImport("kernel32.dll")] static extern IntPtr LocalFree(IntPtr memory);
        static void Assert(bool condition, string message) { if (!condition) throw new Exception(message); }
        static void Reject(Action action)
        { bool failed = false; try { action(); } catch { failed = true; } Assert(failed, "Expected invalid input to be rejected."); }
        static void SelfTest()
        {
            var lines = new List<string>();
            string[] values = { "", "C:\\Users\\A B\\بالما\\", "a\"b", "& del x", "$(literal)", "a\\\\\"b", "--not-a-switch" };
            string command = RuntimeService.Quote("test.exe") + " " + string.Join(" ", values.Select(RuntimeService.Quote));
            IntPtr argv = CommandLineToArgvW(command, out int argc);
            Assert(argv != IntPtr.Zero && argc == values.Length + 1, "Windows argument parsing failed.");
            try { for (int i = 0; i < values.Length; i++) Assert(Marshal.PtrToStringUni(Marshal.ReadIntPtr(argv, (i + 1) * IntPtr.Size)) == values[i], "Argument quoting failed."); }
            finally { LocalFree(argv); }
            Reject(() => RuntimeService.Quote("line\nbreak"));
            lines.Add("PASS: Windows command arguments round-trip, including Arabic paths, empty strings, quotes and shell metacharacters.");
            string config = "bst.instance.Pie64.display_name=\"PALMA\"\nbst.instance.Pie64.adb_port=\"5565\"\nbst.instance.Rvc64.adb_port=\"99999\"\nbst.instance.Nougat32.adb_port=\"5555\"\nbst.instance.Pie64_evil.adb_port=\"5556\"\n";
            var parsed = RuntimeService.ParseConfig(config, "fixture");
            Assert(parsed.Count == 2 && parsed[0].Id == "Pie64" && parsed[0].Port == 5565 && parsed[1].Port == 0, "Instance parsing accepted incompatible runtime or invalid port.");
            RuntimeService.RequireArchitecture("28", "arm64-v8a,armeabi-v7a");
            RuntimeService.RequireArchitecture("30", "x86_64,arm64-v8a");
            Reject(() => RuntimeService.RequireArchitecture("27", "arm64-v8a"));
            Reject(() => RuntimeService.RequireArchitecture("30", "x86_64,x86"));
            Reject(() => RuntimeService.RequireArchitecture("bad", "arm64-v8a"));
            lines.Add("PASS: Reject unsupported Android version, missing ARM64, unsafe instance names and invalid ADB ports.");
            Assert(RuntimeService.InstallError("INSTALL_FAILED_UPDATE_INCOMPATIBLE").Contains("لم تتم إزالته"), "Signature conflict message must preserve data.");
            Reject(() => RuntimeService.VerifyApk(System.Reflection.Assembly.GetExecutingAssembly().Location));
            lines.Add("PASS: Unexpected APK bytes rejected; signature conflict has a non-destructive recovery message.");
            string fixture = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "test-fixtures", "palma-manifest.bin");
            byte[] original = File.ReadAllBytes(fixture);
            Assert(ApkMetadata.ReadManifest(original).Package == RuntimeService.Package, "Original APK manifest identity mismatch.");
            Reject(() => ApkMetadata.ReadManifest(new byte[8]));
            Reject(() => ApkMetadata.ReadManifest(original.Take(original.Length - 1).ToArray()));
            var corrupt = (byte[])original.Clone(); corrupt[12] = 0xff; corrupt[13] = 0xff; corrupt[14] = 0xff; corrupt[15] = 0x7f;
            Reject(() => ApkMetadata.ReadManifest(corrupt));
            lines.Add("PASS: Parse actual PALMA binary manifest; reject malformed, truncated and out-of-bounds binary XML.");
            lines.Add("NOT TESTED: BlueStacks installation, native APK startup, server catalog, modified TDM/MX and video playback on a physical Windows computer.");
            File.WriteAllLines(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "self-test.txt"), lines, Encoding.UTF8);
        }
    }
}
