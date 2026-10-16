"""Standalone Android TV build. No original PALMA APK or third-party wrapper needed."""
import hashlib, os, pathlib, shutil, subprocess, urllib.request, zipfile

root=pathlib.Path(__file__).resolve().parents[1]
work=root/'build/android';work.mkdir(parents=True,exist_ok=True)
release=root/'release';release.mkdir(exist_ok=True)
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/usr/local/lib/android/sdk')))
bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
ndk=sdk/'ndk/26.1.10909125/toolchains/llvm/prebuilt/linux-x86_64'
def run(*args):subprocess.run([str(a) for a in args],check=True)
archive=root/'build/nodejs-mobile-v18.20.4-android.zip'
digest='bd7321eaa1a7602fbe0bb87302df2d79d87835cf4363fbdd17c350dbb485c2af'
if not archive.exists():
    with urllib.request.urlopen('https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v18.20.4/nodejs-mobile-v18.20.4-android.zip',timeout=180) as response,archive.open('wb') as out:shutil.copyfileobj(response,out)
if hashlib.sha256(archive.read_bytes()).hexdigest()!=digest:raise SystemExit('Node mobile archive checksum mismatch')
node=root/'build/nodejs-mobile';node.mkdir(exist_ok=True)
with zipfile.ZipFile(archive) as z:
    for member in z.infolist():
        if not (node/member.filename).resolve().is_relative_to(node.resolve()):raise SystemExit('Invalid Node archive path')
    z.extractall(node)
abis={'armeabi-v7a':('armv7a-linux-androideabi','arm-linux-androideabi'),'arm64-v8a':('aarch64-linux-android','aarch64-linux-android'),'x86_64':('x86_64-linux-android','x86_64-linux-android')}
for abi,(target,triple) in abis.items():
    folder=work/'lib'/abi;folder.mkdir(parents=True,exist_ok=True)
    shutil.copy2(node/'bin'/abi/'libnode.so',folder/'libnode.so')
    shutil.copy2(ndk/'sysroot/usr/lib'/triple/'libc++_shared.so',folder/'libc++_shared.so')
    run(ndk/'bin'/f'{target}24-clang++','-shared','-fPIC','-O2','-std=c++17','-I'+str(node/'include/node'),root/'android/cpp/bridge.cpp','-L'+str(folder),'-lnode','-llog','-Wl,-z,max-page-size=16384','-Wl,-soname,libpalma_node.so','-o',folder/'libpalma_node.so')
gen=work/'generated';gen.mkdir(exist_ok=True)
classes=work/'classes';classes.mkdir(exist_ok=True)
dex=work/'dex';dex.mkdir(exist_ok=True)
run(bt/'aapt2','compile','--dir',root/'android/res','-o',work/'resources.zip')
run(bt/'aapt2','link','-I',android,'--manifest',root/'android/AndroidManifest.xml','--java',gen,'--version-code','10000','--version-name','1.0.0','-o',work/'base.apk',work/'resources.zip')
run('javac','--release','8','-encoding','UTF-8','-cp',android,'-d',classes,*list((root/'android/src').rglob('*.java')),*list(gen.rglob('*.java')))
run('jar','cf',work/'classes.jar','-C',classes,'.')
run(bt/'d8','--min-api','24','--lib',android,'--output',dex,work/'classes.jar')
unpacked=work/'assembled.apk';shutil.copy2(work/'base.apk',unpacked)
with zipfile.ZipFile(unpacked,'a',compression=zipfile.ZIP_DEFLATED) as z:
    z.write(dex/'classes.dex','classes.dex')
    for file in (work/'lib').rglob('*.so'):z.write(file,file.relative_to(work).as_posix())
    for file in (root/'build/runtime').rglob('*'):
        if file.is_file():z.write(file,'assets/runtime/'+file.relative_to(root/'build/runtime').as_posix())
unsigned=release/'PALMA-TV-1.0.0-unsigned.apk'
run(bt/'zipalign','-f','-p','4',unpacked,unsigned)
shutil.copy2(bt/'lib/apksigner.jar',release/'apksigner.jar')
# An ephemeral CI-only certificate installs the test build. It is never the release identity.
testkey=work/'test-only.p12'
if not testkey.exists():run('keytool','-genkeypair','-keystore',testkey,'-storepass','android-ci-test','-keypass','android-ci-test','-alias','test','-dname','CN=PALMA TV Ephemeral CI','-keyalg','RSA','-keysize','2048','-validity','3','-noprompt')
run(bt/'apksigner','sign','--ks',testkey,'--ks-pass','pass:android-ci-test','--out',work/'test.apk',unsigned)
run(bt/'apksigner','verify','--verbose',work/'test.apk')
(release/'unsigned.sha256').write_text(hashlib.sha256(unsigned.read_bytes()).hexdigest()+'\n')
print('Built independent universal APK (ARM32, ARM64, x86_64).')
