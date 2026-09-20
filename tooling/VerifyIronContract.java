import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Static APK/JNI contract check; does not claim native execution or server acceptance. */
public final class VerifyIronContract {
    static final String OWNER="Lcom/drama/mp4/security/IronFingerprint;";
    static final String CTX="Landroid/content/Context;", STR="Ljava/lang/String;";
    static void check(ClassDef owner,String name,String result,String... parameters) {
        for(Method m:owner.getMethods()) if(m.getName().equals(name)) {
            if(!m.getReturnType().equals(result)||!m.getParameterTypes().toString().equals(Arrays.toString(parameters))
                ||(m.getAccessFlags()&0x100)==0||(m.getAccessFlags()&8)!=0)
                throw new IllegalStateException("Wrong native ABI: "+name);
            return;
        }
        throw new IllegalStateException("Missing native entry point: "+name);
    }
    public static void main(String[] args)throws Exception {
        Set<String> seen=new HashSet<>();ClassDef bridge=null;int dexCount=0;
        try(ZipFile z=new ZipFile(args[0])) {
            for(ZipEntry e:Collections.list(z.entries())) if(e.getName().matches("classes\\d*\\.dex")) {
                dexCount++;
                try(InputStream in=new BufferedInputStream(z.getInputStream(e))) {
                    for(ClassDef c:DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in).getClasses()) {
                        if(!seen.add(c.getType()))throw new IllegalStateException("Duplicate DEX class: "+c.getType());
                        if(c.getType().equals(OWNER))bridge=c;
                    }
                }
            }
            if(bridge==null)throw new IllegalStateException("Missing native bridge");
            check(bridge,"nativeSign",STR,CTX,STR,STR,STR,STR);
            check(bridge,"nativeVerifyIntegrity","Z",CTX,STR);
            check(bridge,"nativeCertDiag",STR,CTX);
            check(bridge,"nativeMatchProviderSig","I",CTX,STR,STR);
            check(bridge,"nativeQueryRendererCaps",STR,STR);
            for(String abi:new String[]{"arm64-v8a","armeabi-v7a","x86","x86_64"})
                if(z.getEntry("lib/"+abi+"/libiron_fingerprint.so")==null)
                    throw new IllegalStateException("Missing native library: "+abi);
        }
        System.out.println("{\"dex_files\":"+dexCount+",\"unique_classes\":"+seen.size()+",\"duplicate_classes\":0,\"jni_signatures_verified\":5,\"native_abis_present\":4,\"native_runtime_verified\":false}");
    }
}
