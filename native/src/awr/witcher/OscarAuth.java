package awr.witcher;

import android.content.Context;
import com.drama.mp4.security.IronFingerprint;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Calls the supplied native library with the actual host context and package.
 * No replacement certificate, package spoofing, integrity override or bundled secret.
 */
final class OscarAuth {
    interface Backend {
        boolean verify(Context context, String packageName);
        String sign(Context context, String packageName, String path, String timestamp, String nonce);
        String certificate(Context context);
    }
    interface Clock { long now(); }
    static final class NativeBackend implements Backend {
        private IronFingerprint bridge;
        private IronFingerprint bridge() {
            if (bridge == null) bridge = new IronFingerprint();
            return bridge;
        }
        public boolean verify(Context c, String p) { return bridge().nativeVerifyIntegrity(c,p); }
        public String sign(Context c,String p,String path,String ts,String nonce) {
            return bridge().nativeSign(c,p,path,ts,nonce);
        }
        public String certificate(Context c) { return bridge().nativeCertDiag(c); }
    }
    static final class Result {
        final Map<String,String> headers;
        final String status;
        Result(Map<String,String> h,String s) {
            headers=Collections.unmodifiableMap(new LinkedHashMap<>(h)); status=s;
        }
        String denialMessage() {
            switch(status) {
                case "IRON_READY": return "أُرسل توثيق المكتبة الأصلية، لكن الخادم رفض الطلب. يلزم فحص سبب الرفض في الخادم. [IRON_READY]";
                case "IRON_INTEGRITY": return "رفض فحص سلامة المكتبة الأصلية هذه النسخة. يلزم مراجعة إعدادات الحزمة والشهادة لدى المصدر. [IRON_INTEGRITY]";
                case "IRON_LIBRARY": return "تعذر تحميل مكتبة التحقق الأصلية على هذا الجهاز. [IRON_LIBRARY]";
                case "IRON_EMPTY": return "لم تُنتج المكتبة الأصلية توقيعاً صالحاً للطلب. [IRON_EMPTY]";
                case "IRON_ERROR": return "تعذر تنفيذ تحقق المصدر لهذه النسخة. [IRON_ERROR]";
                default: return "لم تكتمل تهيئة توثيق المصدر. [IRON_CONTEXT]";
            }
        }
    }
    private final Context context;
    private final Backend backend;
    private final Clock clock;
    private volatile long offsetSeconds;
    private String certificate;

    OscarAuth(Context c) { this(c,new NativeBackend(),System::currentTimeMillis); }
    OscarAuth(Context c,Backend b,Clock t) {
        context=c==null?null:c.getApplicationContext(); backend=b; clock=t;
    }
    static boolean accepts(URL url) {
        return "https".equalsIgnoreCase(url.getProtocol()) && "ostvapp.cam".equalsIgnoreCase(url.getHost())
            && (url.getPort()==-1 || url.getPort()==443) && url.getUserInfo()==null
            && url.getPath().startsWith("/api/");
    }
    static Result empty(String status) { return new Result(Collections.emptyMap(),status); }
    synchronized Result headers(URL url) {
        if(!accepts(url)) return empty("IRON_ORIGIN");
        if(context==null) return empty("IRON_CONTEXT");
        try {
            // Keep native integrity enforcement. A failure never invokes nativeSign.
            String packageName=context.getPackageName();
            if(!backend.verify(context,packageName)) return empty("IRON_INTEGRITY");
            String ts=Long.toString(clock.now()/1000+offsetSeconds);
            String nonce=UUID.randomUUID().toString().replace("-","").substring(0,8);
            String sig=backend.sign(context,packageName,url.getPath(),ts,nonce);
            if(!safe(sig)) return empty("IRON_EMPTY");
            if(certificate==null) {
                String diagnostic;
                try { diagnostic=backend.certificate(context); }
                catch(LinkageError | RuntimeException e) { diagnostic="na"; }
                certificate=safe(diagnostic)?diagnostic:"na";
            }
            Map<String,String> h=new LinkedHashMap<>();
            h.put("X-Iron-Sig",sig);h.put("X-Iron-Ts",ts);h.put("X-Iron-Nonce",nonce);
            h.put("X-Iron-Diag",certificate);
            return new Result(h,"IRON_READY");
        } catch(LinkageError e) { return empty("IRON_LIBRARY"); }
          catch(RuntimeException e) { return empty("IRON_ERROR"); }
    }
    static boolean safe(String value) {
        if(value==null || value.isEmpty() || value.length()>8192) return false;
        for(int i=0;i<value.length();i++) if(value.charAt(i)<32 || value.charAt(i)==127) return false;
        return true;
    }
    void observeServerDate(long serverMillis) {
        if(serverMillis>0) offsetSeconds=serverMillis/1000-clock.now()/1000;
    }
}
