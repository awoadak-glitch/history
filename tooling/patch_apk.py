#!/usr/bin/env python3
"""Replace only the integration DEX in an existing signed user APK, then verify."""
import argparse, hashlib, importlib.util, json, pathlib, re, shutil, struct, subprocess, tempfile, zipfile

ROOT=pathlib.Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('awr_build',ROOT/'build.py')
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def signature(name):return name.startswith('META-INF/') and re.search(r'\.(SF|RSA|DSA|EC|MF)$',name,re.I)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['input','dex','output']:
        p.add_argument('--'+name,required=True,type=pathlib.Path)
    p.add_argument('--compiler',type=pathlib.Path)
    p.add_argument('--iron-apk',type=pathlib.Path,help='Copy unchanged IronFingerprint native libraries from the verified owner-supplied Oscar 1.1.4 APK')
    signing=p.add_mutually_exclusive_group(required=True)
    signing.add_argument('--keystore',type=pathlib.Path)
    signing.add_argument('--unsigned',action='store_true',help='Prepare a verified APK payload for later signing; not installable')
    p.add_argument('--alias',default='awr')
    p.add_argument('--allow-new-signature',action='store_true',help='Export a fresh-install APK if the previous signing key is unavailable; never installs or removes an app')
    p.add_argument('--report',type=pathlib.Path,default=ROOT/'artifacts/build-report.json')
    a=p.parse_args()
    if a.keystore and not a.compiler:p.error('--compiler is required with --keystore')
    if a.unsigned and a.allow_new_signature:p.error('--allow-new-signature requires --keystore')
    if a.input.resolve()==a.output.resolve():raise ValueError('Choose a separate output path')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    dex=a.dex.read_bytes()
    if not dex.startswith(b'dex\n'):raise ValueError('Expected compiled DEX')
    native={}
    if a.iron_apk:
        if sha(a.iron_apk)!='e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7':
            raise ValueError('Unexpected IronFingerprint source APK')
        with zipfile.ZipFile(a.iron_apk) as source:
            for abi in ['arm64-v8a','armeabi-v7a','x86','x86_64']:
                name='lib/'+abi+'/libiron_fingerprint.so'
                data=source.read(name)
                if not data.startswith(b'\x7fELF'):raise ValueError('Invalid native library: '+name)
                native[name]=data
        with zipfile.ZipFile(a.input) as original:
            host_abis={n.split('/')[1] for n in original.namelist() if re.fullmatch(r'lib/[^/]+/[^/]+\.so',n)}
            if not host_abis.issubset({n.split('/')[1] for n in native}):raise ValueError('Native signer lacks a host ABI')
            for name,data in native.items():
                if name in original.namelist() and original.read(name)!=data:
                    raise ValueError('Refusing to replace a different native library: '+name)
    with tempfile.TemporaryDirectory(prefix='awr-patch-') as temporary:
        unsigned=pathlib.Path(temporary)/'unsigned.apk'
        with zipfile.ZipFile(a.input) as original,zipfile.ZipFile(unsigned,'w') as out:
            if 'classes29.dex' not in original.namelist():raise ValueError('Input does not contain the integration DEX')
            for info in original.infolist():
                if signature(info.filename):continue
                builder.write_aligned(out,info.filename,dex if info.filename=='classes29.dex' else original.read(info),info.compress_type)
            for name,data in native.items():
                if name not in original.namelist():builder.write_aligned(out,name,data,zipfile.ZIP_STORED)
        if a.unsigned:shutil.copyfile(unsigned,a.output)
        else:subprocess.run(['java','-cp',str(a.compiler),str(ROOT/'tooling/SignApk.java'),str(unsigned),str(a.output),str(a.keystore),a.alias],check=True)
    matching_certificate=False
    if not a.unsigned:
        verification=subprocess.run(['java','-cp',str(a.compiler),str(ROOT/'tooling/VerifyApk.java'),str(a.input),str(a.output)])
        if verification.returncode!=0 and not (verification.returncode==42 and a.allow_new_signature):
            verification.check_returncode()
        matching_certificate=verification.returncode==0
    preserved=0;aligned=0
    with zipfile.ZipFile(a.input) as original,zipfile.ZipFile(a.output) as updated,a.output.open('rb') as raw:
        assert updated.testzip() is None
        previous={n for n in original.namelist() if not signature(n)}
        expected=previous|set(native)
        actual={n for n in updated.namelist() if not signature(n)}
        assert expected==actual
        for name in expected:
            assert updated.read(name)==(dex if name=='classes29.dex' else native[name] if name in native else original.read(name)),name
            if re.fullmatch(r'classes\d*\.dex',name) and name!='classes29.dex':preserved+=1
        for info in updated.infolist():
            if info.compress_type!=zipfile.ZIP_STORED:continue
            raw.seek(info.header_offset+26);name_len,extra_len=struct.unpack('<HH',raw.read(4))
            offset=info.header_offset+30+name_len+extra_len
            alignment=16384 if info.filename.startswith('lib/') and info.filename.endswith('.so') else 4
            assert offset%alignment==0,info.filename
            aligned+=1
    report={
        'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'input_apk_sha256':sha(a.input),'apk_sha256':sha(a.output),'custom_dex_sha256':sha(a.dex),
        'replaced_entry':'classes29.dex','preserved_other_dex':preserved,
        'added_native_entries':sorted(set(native)-previous),
        'native_library_source_apk_sha256':sha(a.iron_apk) if a.iron_apk else None,
        'native_libraries_unchanged_sha256':{n:hashlib.sha256(b).hexdigest() for n,b in native.items()},
        'all_other_payload_entries_unchanged':True,'stored_entries_alignment_verified':aligned,
        'signature_verified_v1':not a.unsigned,'signature_verified_v2':not a.unsigned,'signature_verified_v3':not a.unsigned,
        'unsigned':a.unsigned,
        'signing_certificate_matches_input':matching_certificate,'runtime_tested_on_device':False,
        'install_as_update':matching_certificate,
        'mx_player_package':'com.mxtech.videoplayer.ad',
        'verification_scope':'APK signatures, ZIP payload preservation and alignment only; this tool does not validate network playback or application behavior.',
        'known_limitations':'Unsigned preparation only; cannot be installed until signed with the approved key.' if a.unsigned else 'No real-device playback test. A different signing certificate prevents installation as an update to the input APK.'
    }
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=='__main__':main()
