using Microsoft.Win32;
using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;

namespace PalmaDesktop
{
    public partial class MainWindow : Window
    {
        readonly RuntimeService runtime = new RuntimeService();
        CancellationTokenSource operation;
        bool busy, closed;
        public MainWindow()
        {
            InitializeComponent(); runtime.Progress = SetStatus; Refresh();
            Closed += (s, e) => { closed = true; operation?.Cancel(); };
            Loaded += async (s, e) =>
            {
                try { await Task.Run(() => RuntimeService.VerifyApk(runtime.ApkPath)); ApkStatus.Text = "الملف الأصلي جاهز"; }
                catch { ApkStatus.Text = "اختر ملف APK"; }
            };
        }
        void SetStatus(string title, string text) { if (!closed) { StatusTitle.Text = title; StatusText.Text = text; } }
        void Refresh()
        {
            var previous = runtime.Selected?.Id;
            var instances = runtime.Discover(); InstanceBox.ItemsSource = instances;
            InstanceBox.SelectedItem = instances.FirstOrDefault(i => i.Id == previous) ?? instances.FirstOrDefault();
            RuntimeStatus.Text = runtime.Ready ? (instances.Count > 0 ? "متاح للفحص" : "أنشئ نسخة 64-bit") : "يحتاج إلى إعداد";
        }
        void InstanceChanged(object sender, SelectionChangedEventArgs e)
        { runtime.Selected = InstanceBox.SelectedItem as AndroidInstance; if (PlayerStatus != null) PlayerStatus.Text = "بانتظار الفحص"; }
        void RefreshClick(object sender, RoutedEventArgs e) { try { Refresh(); SetStatus("تم تحديث البيئات", "اختر البيئة المطلوبة ثم اضغط تشغيل بالما."); } catch (Exception error) { SetStatus("تعذر التحديث", error.Message); } }
        async Task Execute(Func<CancellationToken, Task> action)
        {
            if (busy) return;
            busy = true; operation = new CancellationTokenSource();
            foreach (var button in new[] { LaunchButton, CheckButton, OpenEngineButton, RefreshButton, BrowseButton, CompanionButton, PhoneButton }) button.IsEnabled = false;
            InstanceBox.IsEnabled = false; ProgressBar.Visibility = CancelButton.Visibility = Visibility.Visible;
            try { await action(operation.Token); }
            catch (OperationCanceledException) { SetStatus("أُلغيت العملية", "يمكنك المحاولة مجددًا عندما تكون جاهزًا. لم يُغلق BlueStacks."); }
            catch (Exception error) { SetStatus("تحتاج هذه الخطوة إلى مراجعة", error.Message); runtime.Report.Add("Action error: " + error.Message); }
            finally
            {
                busy = false; operation.Dispose(); operation = null;
                if (!closed)
                {
                    foreach (var button in new[] { LaunchButton, CheckButton, OpenEngineButton, RefreshButton, BrowseButton, CompanionButton, PhoneButton }) button.IsEnabled = true;
                    InstanceBox.IsEnabled = true; ProgressBar.Visibility = CancelButton.Visibility = Visibility.Collapsed;
                }
            }
        }
        async Task<string> ConnectAndCheck(bool start, CancellationToken token)
        {
            await runtime.Connect(start, token); var missing = await runtime.Check(token);
            RuntimeStatus.Text = "ARM64 متوافق";
            PlayerStatus.Text = missing.Count == 0 ? "التطبيقات موجودة" : "تحتاج ملحقات";
            return missing.Count == 0
                ? "تطبيقات TDM وMX موجودة؛ اختبر المشاهدة للتأكد من توافق نسخها المعدّلة."
                : "أضف " + string.Join(" و", missing) + " من ملف أو من هاتفك قبل اختبار المشاهدة.";
        }
        async void CheckClick(object sender, RoutedEventArgs e)
        { await Execute(async t => { string details = await ConnectAndCheck(false, t); SetStatus("اكتمل فحص بيئة التشغيل", details + " ظهور المحتوى يُفحص داخل بالما."); }); }
        async void LaunchClick(object sender, RoutedEventArgs e)
        {
            if (!File.Exists(runtime.ApkPath))
            {
                var picker = new OpenFileDialog { Title = "اختر ملف بالما الأصلي 1.1.7", Filter = "Android APK|*.apk" };
                if (picker.ShowDialog(this) != true) return; runtime.ApkPath = picker.FileName;
            }
            await Execute(async t =>
            {
                await Task.Run(() => RuntimeService.VerifyApk(runtime.ApkPath), t); ApkStatus.Text = "الملف الأصلي جاهز";
                string details = await ConnectAndCheck(true, t);
                await runtime.InstallPalma(t); await runtime.Launch(t);
                SetStatus("فُتح تطبيق بالما", details + " انتقل إلى نافذة BlueStacks وافتح القوائم لاختبار المحتوى.");
            });
        }
        void OpenEngineClick(object sender, RoutedEventArgs e)
        { try { runtime.StartEngine(); SetStatus("فُتحت بيئة التشغيل", "بعد اكتمال تشغيل Android اضغط فحص الجاهزية أو تشغيل بالما."); } catch (Exception error) { SetStatus("راجع الإعداد", error.Message); } }
        void BrowseEngineClick(object sender, RoutedEventArgs e)
        {
            var picker = new OpenFileDialog { Title = "اختر HD-Player.exe من BlueStacks 5", Filter = "BlueStacks App Player|HD-Player.exe" };
            if (picker.ShowDialog(this) != true) return;
            try { runtime.SetRoot(Path.GetDirectoryName(picker.FileName)); Refresh(); SetStatus("تم تحديد المحرك", "اختر نسخة التشغيل من القائمة."); }
            catch (Exception error) { SetStatus("تعذر تحديد المحرك", error.Message); }
        }
        async void CompanionClick(object sender, RoutedEventArgs e)
        {
            var picker = new OpenFileDialog { Title = "اختر نسخة TDM أو MX المتوافقة مع بالما", Filter = "Android packages|*.apk;*.apks;*.xapk" };
            if (picker.ShowDialog(this) != true) return;
            await Execute(async t => { await ConnectAndCheck(true, t); await runtime.InstallCompanion(picker.FileName, t); string details = await ConnectAndCheck(false, t); SetStatus("اكتمل تثبيت الملحق", details); });
        }
        async void PhoneClick(object sender, RoutedEventArgs e)
        {
            if (MessageBox.Show(this,
                "صل هاتف Android واحدًا عبر USB، وفعّل USB debugging في خيارات المطور ثم وافق على رسالة السماح على هاتفك.\n\nسيُنسخ ملفا تثبيت TDM وMX الموجودان على الهاتف إلى BlueStacks. لن تُنسخ الحسابات أو سجل المشاهدة أو الملفات الشخصية.\n\nهل الهاتف جاهز لنسخ التطبيقات؟",
                "نسخ ملحقات المشاهدة", MessageBoxButton.OKCancel, MessageBoxImage.Information) != MessageBoxResult.OK) return;
            await Execute(async t =>
            {
                await ConnectAndCheck(true, t); var phones = await runtime.Phones(t);
                if (phones.Count != 1) throw new InvalidOperationException(phones.Count == 0
                    ? "لم يظهر هاتف USB مصرح له. وافق على رسالة USB debugging وافحص كابل البيانات وتعريف الهاتف، أو استخدم إضافة من ملف."
                    : "يوجد أكثر من هاتف متصل. أبقِ الهاتف الذي عليه نسخ بالما فقط ثم حاول مجددًا.");
                int count = await runtime.ImportPhone(phones[0], t);
                string details = await ConnectAndCheck(false, t);
                SetStatus(count > 0 ? "نُقلت تطبيقات المشاهدة" : "لم توجد التطبيقات المطلوبة على الهاتف", details);
            });
        }
        void CancelClick(object sender, RoutedEventArgs e) { operation?.Cancel(); }
        void OpenUrl(string url) { try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch (Exception error) { SetStatus("تعذر فتح الرابط", error.Message); } }
        void DownloadClick(object sender, RoutedEventArgs e) => OpenUrl("https://www.bluestacks.com/download.html");
        void WebsiteClick(object sender, RoutedEventArgs e) => OpenUrl("https://palma-web-lac.vercel.app/");
        void HelpClick(object sender, RoutedEventArgs e)
        {
            MessageBox.Show(this,
                "١. ثبّت BlueStacks 5 من موقعه الرسمي.\n\n٢. من Multi-instance Manager أنشئ Pie 64-bit أو Android 11. في ABI اختر Custom ثم ARM 64 bit.\n\n٣. افتح النسخة، ثم Settings ← Advanced، وفعّل Android Debug Bridge واضغط Save changes.\n\n٤. ارجع إلى هذه النافذة واضغط تحديث، اختر النسخة ثم تشغيل بالما. يثبت ملف APK الأصلي دون تعديل.\n\n٥. لبعض المصادر، تحتاج TDM المعدّل وMX اللذين يعملان مع بالما على هاتفك. أضف ملفاتهما أو انقلهما بزر نسخ الملحقات من الهاتف.\n\n٦. اختبر الرئيسية وعالم المصادر ثم حلقة وفيلمًا. استخدم F11 داخل BlueStacks لملء الشاشة.\n\nتعمل واجهات بالما الأصلية داخل Android. توفر المصادر وتوافق التطبيق مع المحاكي لا يُثبتان بمجرد نجاح التثبيت؛ يلزم اختبار المشاهدة. لا تحذف بيئتك القديمة عند اختلاف توقيع تطبيق.",
                "إعداد PALMA على Windows", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        void ReportClick(object sender, RoutedEventArgs e)
        {
            var picker = new SaveFileDialog { Title = "حفظ تقرير الفحص", FileName = "PALMA-Diagnostics.txt", Filter = "Text report|*.txt" };
            if (picker.ShowDialog(this) != true) return;
            try { File.WriteAllLines(picker.FileName, runtime.Report.Concat(new[] { "Required PALMA SHA256: " + RuntimeService.ApkSha })); SetStatus("حُفظ تقرير الفحص", "يتضمن نتائج البيئة والتثبيت، ولا يتضمن حساباتك أو مفاتيح الخادم."); }
            catch (Exception error) { SetStatus("تعذر حفظ التقرير", error.Message); }
        }
    }
}
