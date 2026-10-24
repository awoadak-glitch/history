import {APP_PROFILE} from './client.mjs';

// Recovered application identity from the owner's supplied 1.1.6 APK. The native
// file/raw readers hash the first signer certificate in the APK signing block;
// the package-manager reader hashes the same certificate. This is public release
// metadata, not an APK signing key, device secret, or Android hardware proof.
export const SOURCE_PROFILE=Object.freeze({
  versionName:'1.1.6',
  versionCode:'18',
  apkSHA256:'b6b23d4f667d4662281bb2a8363d3821d36aa37ecc13e06384227dd96a45326b',
  certificateSHA256:APP_PROFILE.certificate_sha256,
  componentFactory:'androidx.core.app.CoreComponentFactory',
});

// Compatibility identity for this release. Keep platform=windows in register;
// the source decides the trust level for our own per-installation software key.
// No attest_chain or challenge-bound hardware claim is made by this provider.
export function sourceApplicationProof() {
  const cert=SOURCE_PROFILE.certificateSHA256;
  return {attest:JSON.stringify({file:cert,raw:cert,pm:cert,hook:0,acf:'ok'})};
}
