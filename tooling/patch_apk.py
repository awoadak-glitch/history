#!/usr/bin/env python3
"""Replace only the integration DEX in an existing signed user APK, then verify."""
import argparse, hashlib, importlib.util, json, pathlib, re, struct, subprocess, tempfile, zipfile

ROOT=pathlib.Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('awr_build',ROOT/'build.py')
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def signature(name):return name.startswith('META-INF/') and re.search(r'\.(SF|RSA|DSA|EC|MF)$',name,re.I)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['input','dex','output','compiler','keystore']:
        p.add_argument('--'+name,required=True,type=pathlib.Path)
    p.add_argument('--alias',required=True)
    p.add_argument('--allow-new-signature',action='store_true',help='Export a fresh-install APK if the previous signing key is unavailable; never installs or removes an app')
    p.add_argument('--report',type=pathlib.Path,default=ROOT/'artifacts/build-report.json')
    a=p.parse_args()
    if a.input.resolve()==a.output.resolve():raise ValueError('Choose a separate output path')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    dex=a.dex.read_bytes()
    if not dex.startswith(b'dex\n'):raise ValueError('Expected compiled DEX')
    with tempfile.TemporaryDirectory(prefix='awr-patch-') as temporary:
        unsigned=pathlib.Path(temporary)/'unsigned.apk'
        with zipfile.ZipFile(a.input) as original,zipfile.ZipFile(unsigned,'w') as out:
            if 'classes29.dex' not in original.namelist():raise ValueError('Input does not contain the integration DEX')
            for info in original.infolist():
                if signature(info.filename):continue
                builder.write_aligned(out,info.filename,dex if info.filename=='classes29.dex' else original.read(info),info.compress_type)
        subprocess.run(['java','-cp',str(a.compiler),str(ROOT/'tooling/SignApk.java'),str(unsigned),str(a.output),str(a.keystore),a.alias],check=True)
    verification=subprocess.run(['java','-cp',str(a.compiler),str(ROOT/'tooling/VerifyApk.java'),str(a.input),str(a.output)])
    if verification.returncode!=0 and not (verification.returncode==42 and a.allow_new_signature):
        verification.check_returncode()
    matching_certificate=verification.returncode==0
    preserved=0;aligned=0
    with zipfile.ZipFile(a.input) as original,zipfile.ZipFile(a.output) as updated,a.output.open('rb') as raw:
        assert updated.testzip() is None
        expected={n for n in original.namelist() if not signature(n)}
        actual={n for n in updated.namelist() if not signature(n)}
        assert expected==actual
        for name in expected:
            assert updated.read(name)==(dex if name=='classes29.dex' else original.read(name)),name
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
        'all_other_payload_entries_unchanged':True,'stored_entries_alignment_verified':aligned,
        'signature_verified_v1':True,'signature_verified_v2':True,'signature_verified_v3':True,
        'signing_certificate_matches_input':matching_certificate,'runtime_tested_on_device':False,
        'install_as_update':matching_certificate,
        'mx_player_package':'com.mxtech.videoplayer.ad',
        'download_access_rule':'The supplied Drama V4.2f guest paths return true in both branches; flags 2/3 do not block this edition.',
        'source_routing':'external=false still requires provider extraction for MOV/WEBM/M3U8 pages.',
        'quality_selection':'Choose extracted qualities before MX or TDM; reject invalid HLS responses.',
        'known_limitations':'No real-device playback test. Some original extractors depend on unavailable protected settings; provider availability can change.'
    }
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=='__main__':main()
