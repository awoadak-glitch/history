#!/usr/bin/env python3
"""Compile Source World and brand an existing AWR APK without replacing host/Drama/HiTV code."""
import argparse,hashlib,importlib.util,json,pathlib,re,shutil,struct,subprocess,zipfile,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('builder',ROOT/'build.py');builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
def run(*args):subprocess.run([str(x) for x in args],check=True,cwd=ROOT)
def sha(p):return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
def signature(n):return n.startswith('META-INF/') and re.search(r'\.(SF|RSA|DSA|EC|MF)$',n,re.I)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['input','output','compiler','android-jar','apktool','keystore']:p.add_argument('--'+name,type=pathlib.Path,required=True)
    p.add_argument('--alias',required=True);p.add_argument('--prepared-resources',action='store_true');a=p.parse_args()
    for name in ['input','output','compiler','android_jar','apktool','keystore']:setattr(a,name,getattr(a,name).absolute())
    build=ROOT/'build';classes=build/'oscar-classes';dexdir=build/'oscar-dex';classes.mkdir(exist_ok=True);dexdir.mkdir(exist_ok=True)
    # Generated Drama configuration is produced by the existing build workflow.
    generated=list((build/'generated').rglob('*.java'))
    if not generated:raise SystemExit('Missing build/generated configuration. Run the existing compile preparation first.')
    shutil.rmtree(classes);classes.mkdir()
    run('java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',a.android_jar,'-d',classes,*sorted((ROOT/'native/src').rglob('*.java')),*generated)
    jar=build/'oscar-classes.jar'
    with zipfile.ZipFile(jar,'w') as out:
        for f in classes.rglob('*.class'):out.write(f,f.relative_to(classes))
    java_home=pathlib.Path(shutil.which('java')).resolve().parent.parent
    run('java','-cp',a.compiler,'com.android.tools.r8.D8','--min-api','21','--lib',a.android_jar,'--lib',java_home,'--output',dexdir,jar)
    merged=ROOT/'artifacts/oscar-classes29.dex'
    run('java','-cp',a.compiler,ROOT/'tooling/MergeOscarDex.java',a.input,dexdir/'classes.dex',merged,ROOT/'artifacts/oscar-preservation-report.json')
    decoded=build/'awr-brand';resources=build/'awr-resources.apk';entries=build/'oscar-resource-entries.json'
    if not a.prepared_resources:
        run('java','-jar',a.apktool,'d','--no-src','-f','-o',decoded,a.input)
        run('python3',ROOT/'tooling/brand_resources.py',decoded,'--entries',entries)
        run('java','-jar',a.apktool,'b',decoded,'-o',resources)
    checked=build/'awr-resource-check'
    run('java','-jar',a.apktool,'d','--no-src','--no-assets','-f','-o',checked,resources)
    public=lambda root:{(n.get('type'),n.get('name')):n.get('id') for n in ET.parse(root/'res/values/public.xml').getroot()}
    assert public(decoded)==public(checked),'Resource IDs changed'
    replacements=set(json.loads(entries.read_text()));replacements.add('classes29.dex')
    unsigned=build/'awr-world-unsigned.apk'
    with zipfile.ZipFile(a.input) as old,zipfile.ZipFile(resources) as branded,zipfile.ZipFile(unsigned,'w') as out:
        # Aapt2 normalizes some configuration suffixes (e.g. -hdpi-v4 -> -hdpi).
        # Keep every original entry and add the normalized resource aliases referenced
        # by the rebuilt table. Only explicitly branded existing entries are replaced.
        aliases={n for n in branded.namelist() if n.startswith('res/') and n not in old.namelist()}
        assert replacements.issubset(set(old.namelist())|aliases)
        replacements.intersection_update(set(old.namelist()))
        for info in old.infolist():
            if signature(info.filename):continue
            data=merged.read_bytes() if info.filename=='classes29.dex' else branded.read(info.filename) if info.filename in replacements else old.read(info)
            builder.write_aligned(out,info.filename,data,info.compress_type)
        for name in sorted(aliases):builder.write_aligned(out,name,branded.read(name),branded.getinfo(name).compress_type)
    run('java','-cp',a.compiler,ROOT/'tooling/SignApk.java',unsigned,a.output,a.keystore,a.alias)
    run('java','-cp',a.compiler,ROOT/'tooling/VerifyApk.java',a.input,a.output)
    preserved=0;alignment=0
    with zipfile.ZipFile(a.input) as old,zipfile.ZipFile(a.output) as new,a.output.open('rb') as raw:
        assert new.testzip() is None
        assert {n for n in old.namelist() if not signature(n)}=={n for n in new.namelist() if not signature(n)}-aliases
        for n in old.namelist():
            if signature(n) or n in replacements:continue
            assert old.read(n)==new.read(n),n
            if re.fullmatch(r'classes\d*\.dex',n):preserved+=1
        for i in new.infolist():
            if i.compress_type!=zipfile.ZIP_STORED:continue
            raw.seek(i.header_offset+26);nl,el=struct.unpack('<HH',raw.read(4));offset=i.header_offset+30+nl+el
            assert offset%(16384 if i.filename.startswith('lib/') and i.filename.endswith('.so') else 4)==0,i.filename
            alignment+=1
    report={'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'input_apk_sha256':sha(a.input),'apk_sha256':sha(a.output),'custom_dex_sha256':sha(merged),'preserved_other_dex':preserved,'explicitly_replaced_entries':sorted(replacements),'added_normalized_resource_aliases':len(aliases),'resource_ids_identical':True,'all_other_payload_entries_unchanged':True,'stored_entries_alignment_verified':alignment,'signature_verified_v1':True,'signature_verified_v2':True,'signature_verified_v3':True,'signing_certificate_matches_input':True,'install_as_update_to_input':True,'runtime_tested_on_device':False,'live_oscar_catalogue_verified':False,'live_oscar_playback_verified':False,'known_limitations':['Oscar server returned HTTP 403 / Cloudflare 1010 in this environment.','Opaque source-player deep links require the source application; only prepared HTTP media goes to MX.','Not a claim of complete feature parity: source account sync, comments, predictions and all sports subpages are not implemented.']}
    (ROOT/'artifacts/oscar-build-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
