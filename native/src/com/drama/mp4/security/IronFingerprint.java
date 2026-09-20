package com.drama.mp4.security;

import android.content.Context;

/** JNI entry points for the owner's unchanged libiron_fingerprint.so.
 * The class/method names are the exported native ABI, not an Android package identity.
 */
public final class IronFingerprint {
    static { System.loadLibrary("iron_fingerprint"); }

    public native String nativeSign(Context context, String packageName, String path,
                                    String timestamp, String nonce);
    public native boolean nativeVerifyIntegrity(Context context, String packageName);
    public native String nativeCertDiag(Context context);
    public native int nativeMatchProviderSig(Context context, String packageName, String expected);
    public native String nativeQueryRendererCaps(String classList);
}
