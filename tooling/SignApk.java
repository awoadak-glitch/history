import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

public class SignApk {
    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("input output keystore alias; password from AWR_KEYSTORE_PASSWORD");
        char[] password=System.getenv("AWR_KEYSTORE_PASSWORD").toCharArray();
        KeyStore store=KeyStore.getInstance("PKCS12");
        try(InputStream in=new FileInputStream(args[2])){store.load(in,password);}
        PrivateKey key=(PrivateKey)store.getKey(args[3],password);
        List<X509Certificate> chain=new ArrayList<>();
        for(java.security.cert.Certificate cert:store.getCertificateChain(args[3]))chain.add((X509Certificate)cert);
        Arrays.fill(password,'\0');
        ApkSigner.SignerConfig signer=new ApkSigner.SignerConfig.Builder("AWR Witcher",key,chain).build();
        new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(new File(args[0])).setOutputApk(new File(args[1]))
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false)
            .setOtherSignersSignaturesPreserved(false).build().sign();
        ApkVerifier.Result result=new ApkVerifier.Builder(new File(args[1])).build().verify();
        if(!result.isVerified())throw new IllegalStateException("Signature invalid: "+result.getErrors());
        System.out.println("Signature verified: v1="+result.isVerifiedUsingV1Scheme()+" v2="+result.isVerifiedUsingV2Scheme()+" v3="+result.isVerifiedUsingV3Scheme());
    }
}
