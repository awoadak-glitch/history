# PALMA VISION — تطبيق Windows مستقل

[تنزيل PALMA VISION 2.1.0 لـ Windows 64-bit](https://github.com/awoadak-glitch/history/releases/download/palma-windows-v2.1.0/PALMA-VISION-Windows-2.1.0.zip)

فك ضغط الحزمة كاملة ثم افتح `PALMA VISION.exe`. يجلب التطبيق محتوى بالما مباشرة
دون APK أو محاكي أو تثبيت Node.js. المشغّل المدمج بديل MX، ومدير التنزيل يجمع
أجزاء HLS في MP4 مع التقدّم والإلغاء وإعادة المحاولة وفتح مجلد الحفظ، بديل TDM.

- [`palma-core`](palma-core/README.md): تحليل التطبيق وبروتوكول جلب المحتوى.
- [`palma-windows`](palma-windows/README-AR.md): التطبيق المستقل وتعليمات البناء.
- [`palma-web`](palma-web/README.md): الواجهات العربية وعميل المصادر المشتركان.

اجتازت الحزمة المجمّعة [اختبار Windows](https://github.com/awoadak-glitch/history/actions/runs/38615613464)
بتاريخ 2026-10-14: الرئيسية الأصلية (28 قسمًا و32 بانرًا)، التفاصيل والمواسم
والحلقات، وظهور الصور و278 بطاقة، وفيديو حقيقي 1920×1080 داخل المشغّل.
نُزّلت عينة HLS من جزأين وحُفظت MP4 بحجم 4,250,646 بايت ثم فُكّت بنجاح.
هذه عينة تحقق، وليست ادعاء مشاهدة كل حلقة أو تنزيل فيلم كامل.
قوائم المسلسلات والأفلام والقنوات والمصارعة والمجموعات والممثلين أعادت محتوى.
التقارير: [`palma-core/docs/windows-verification.json`](palma-core/docs/windows-verification.json)
و[`catalogue-verification.json`](palma-core/docs/catalogue-verification.json).
الحزمة والصور وبصمة SHA-256 في [الإصدار المنشور](https://github.com/awoadak-glitch/history/releases/tag/palma-windows-v2.1.0).

Vercel أعاد بيانات فارغة لبعض الطلبات الصحيحة؛ نسخة Windows تتصل بالمصدر
مباشرة ولا تعتمد على نشر الويب. مصدر الأنمي هو AWR المحدد من المالك.
عالم الدراما غير مفعّل، ولم يثبت عمل جميع السيرفرات الخارجية.
المجلد القديم `palma-desktop` خاص بمشغّل المحاكي، وليس التطبيق المستقل الجديد.

## تاريخ مشروع Android

# PALMA — Oscar Pro + AWR Source World

تطوير داخل APK الأصلي، محفوظ بخطوات صغيرة. ابدأ بقراءة [RESUME.md](RESUME.md).

المسار المعتمد حاليًا: تطبيق **PALMA** مبني فوق مضيف أوسكار Pro المستقر، و«عالم المصادر» واجهات وDEX مباشرة داخل التطبيق نفسه. الاسم والشعار وأيقونات المشغّل وشاشة البدء بهوية PALMA. لا توجد وحدة APK إضافية ولا محمّل DEX وقت التشغيل، و`assets/base.apk` الأصلي محفوظ بايتًا ببايت.

التبويبات: الرئيسية، الأنمي، المسلسلات، الأفلام، القنوات. تبويب الأنمي واجهة أصلية متصلة بمصادر Anime Witcher ويدعم البحث والتفاصيل والحلقات والجودات والسيرفرات والمفضلة والسجل. ضغط سيرفر مشاهدة أوسكار يلتقط رابط `WatchLink`/`MovieLink`/`ChannelStream`، يحل رابط embed عند الحاجة، ثم يرسله إلى MX Player بالحزمة `com.mxtech.videoplayer.ad`؛ صفوف التنزيل لم تُغيّر. تفاصيل البناء والتحقق في [docs/OSCAR-ANIME-DIRECT.md](docs/OSCAR-ANIME-DIRECT.md) و[docs/OSCAR-DIRECT-AWR.md](docs/OSCAR-DIRECT-AWR.md).

الحالة الحالية: APK موقع اجتاز التحقق البنيوي والتوقيع والمحاذاة؛ لم يُختبر على هاتف/محاكي ضمن بيئة البناء.
