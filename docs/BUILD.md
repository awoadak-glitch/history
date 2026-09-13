# إعادة البناء

المطلوب: Python 3، JDK 17، apktool 2.11.1، jadx 1.5.2 all jar (أو أدوات D8/apksig متوافقة)، android-all 14-robolectric-10818077.jar من Maven Central، وAPK الأنمي الأصلي المطابق للبصمة في RESUME.md.

```sh
python3 build.py --anime inputs/anime.apk --apktool tooling/cache/apktool.jar --compiler tooling/cache/jadx-1.5.2-all.jar --android-jar tooling/cache/android-all-14.jar
```

ينتج build/anime-witcher-unsigned.apk وحزمة artifacts/mt-manager-patch.zip. للتوقيع أضف --keystore /مسار/المفتاح.p12 --alias awr واضبط AWR_KEYSTORE_PASSWORD محلياً. لا ترفع المفتاح أو كلمة مروره إلى هذا المستودع العام.

البناء ينقل ملف layout وManifest المترجمين فقط، ثم يضيف DEX الجديد. جميع DEX الأصلية وresources.arsc والمكتبات الأصلية تؤخذ كما هي من APK الأصلي. لا يغيّر منطق التطبيق الأصلي أو اسم حزمته.

دعم DEX الإضافي على minSdk 21 قائم على دعم ART الأصلي: [توثيق Android](https://developer.android.com/build/multidex). لا يُستبدل ApplicationClass أو onCreate المحمي.

التوقيع الجديد لا يطابق توقيع الناشر الأصلي؛ تثبيت تحديث فوق النسخة المثبتة يتطلب مفتاحها نفسه. احتفظ ببياناتك والنسخة الأصلية، ولا تحذفها لمجرد اختبار البناء قبل التأكد من طريقة التثبيت المناسبة على جهازك.
