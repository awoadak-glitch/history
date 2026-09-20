import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;

/** Checks update compatibility without exposing signing keys or passwords. */
public class VerifyApk {
    private static Set<String> certificates(ApkVerifier.Result r) throws Exception {
        Set<String> hashes=new TreeSet<>();
        for(X509Certificate cert:r.getSignerCertificates()) {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            hashes.add(Base64.getEncoder().encodeToString(hash));
        }
        return hashes;
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("previous.apk updated.apk");
        ApkVerifier.Result previous=new ApkVerifier.Builder(new File(args[0])).build().verify();
        ApkVerifier.Result updated=new ApkVerifier.Builder(new File(args[1])).build().verify();
        if(!previous.isVerified()||!updated.isVerified())throw new IllegalStateException("APK signature verification failed");
        if(!updated.isVerifiedUsingV1Scheme()||!updated.isVerifiedUsingV2Scheme()||!updated.isVerifiedUsingV3Scheme())
            throw new IllegalStateException("Updated APK must verify with v1, v2 and v3 signing schemes");
        if(!certificates(previous).equals(certificates(updated))){
            System.err.println("Both APK signatures are valid, but certificates differ; this is a fresh-install build, not a compatible update.");
            System.exit(42);
        }
        System.out.println("Previous and updated APK signatures verified; signing certificates match.");
    }
}
