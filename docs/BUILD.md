# إعادة البناء

المطلوب: Python 3، JDK 17، apktool 2.11.1، jadx 1.5.2 all jar (أو أدوات D8/apksig متوافقة)، android-all 14-robolectric-10818077.jar من Maven Central، وAPK الأنمي الأصلي المطابق للبصمة في RESUME.md.

```sh
python3 build.py --anime inputs/anime.apk --drama inputs/drama.apk --apktool tooling/cache/apktool.jar --compiler tooling/cache/jadx-1.5.2-all.jar --android-jar tooling/cache/android-all-14.jar
```

ينتج build/anime-witcher-unsigned.apk وحزمة artifacts/mt-manager-patch.zip. للتوقيع أضف --keystore /مسار/المفتاح.p12 --alias awr واضبط AWR_KEYSTORE_PASSWORD محلياً. لا ترفع المفتاح أو كلمة مروره إلى هذا المستودع العام.

البناء ينقل ملف layout وManifest المترجمين فقط، ثم يضيف DEX الجديد. جميع DEX الأصلية وresources.arsc والمكتبات الأصلية تؤخذ كما هي من APK الأصلي. لا يغيّر منطق التطبيق الأصلي أو اسم حزمته.

دعم DEX الإضافي على minSdk 21 قائم على دعم ART الأصلي: [توثيق Android](https://developer.android.com/build/multidex). لا يُستبدل ApplicationClass أو onCreate المحمي.

التوقيع الجديد لا يطابق توقيع الناشر الأصلي؛ تثبيت تحديث فوق النسخة المثبتة يتطلب مفتاحها نفسه. احتفظ ببياناتك والنسخة الأصلية، ولا تحذفها لمجرد اختبار البناء قبل التأكد من طريقة التثبيت المناسبة على جهازك.

يتضمن البناء الآن المستخرجات الأصلية. خيار --without-extractors مخصص لفحص الواجهة فقط. اختبارات الواجهة: python3 tooling/test.py بعد البناء. الملفات داخل tooling/cache وbuild يعاد إنشاؤها وليست مصدر المشروع.

## تحديث APK المستخدم الأخير دون إرجاع تعديلاته
بعد `--compile-only` واختبارات `python3 tooling/test.py`، استخدم:

```sh
python3 tooling/patch_apk.py --input inputs/latest-user.apk --dex build/dex/classes.dex --output build/Anime-Witcher-MX-servers-fixed.apk --compiler tooling/cache/jadx-1.5.2-all.jar --keystore /مسار/المفتاح.p12 --alias awr
```

كلمة المرور من `AWR_KEYSTORE_PASSWORD`. يتغير classes29.dex والتوقيع فقط. الأداة تفحص تطابق كل ملف آخر، محاذاة الملفات المخزنة والمكتبات، وصحة التوقيع وتطابق شهادة APK السابق كي يقبل التحديث. لا تستخدم حزمة MT القديمة فوق نسخة عدّلها المستخدم. `artifacts/classes29.dex` عند وجوده هو نسخة DEX مطابقة لبصمة التقرير؛ workflow يعيد إنتاج الحزمة من المصدر.

إذا لم يوجد مفتاح النسخة الأخيرة، يتوقف فحص التحديث. يمكن إنشاء APK صالح لتثبيت جديد بـ `--allow-new-signature`؛ يبقى التقرير `install_as_update=false`، وهذا لا يثبت التطبيق أو يحذف النسخة الحالية. لا يمكن تحويله إلى تحديث متوافق من دون المفتاح الصحيح. في جلسة 2026-09-13 كان مفتاح AWR WORLD متاحاً، بينما clean-mx-quality موقّع بـ AWR Clean MX Test؛ لا تخلط بينهما.
