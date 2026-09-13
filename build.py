#!/usr/bin/env python3
"""Build an additive DEX patch. Preserve original code, resources and native libraries."""
import argparse, base64, hashlib, json, os, pathlib, re, shutil, struct, subprocess, zipfile
from xml.etree import ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent
A = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', A[1:-1])
ET.register_namespace('app', 'http://schemas.android.com/apk/res-auto')
EXPECTED = '7f4a930f77ed80ababcce04595b3c104a12b0a607c97397a27560e4d60254923'

def run(*args):
    subprocess.run([str(x) for x in args], check=True)

def sha(path):
    return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()

def patch_resources(decoded):
    home = decoded/'res/layout/activity_home.xml'
    tree = ET.parse(home); root = tree.getroot()
    body = root.find('RelativeLayout')
    if body is None: raise RuntimeError('Unexpected original home layout')
    content = next(e for e in body if e.get(A+'id') == '@id/rel_layout2')
    # Reserve space without introducing resource IDs or changing resources.arsc.
    content.set(A+'layout_marginBottom', '64dp')
    ET.SubElement(body, 'awr.witcher.WitcherTabs', {
        A+'layout_width':'match_parent', A+'layout_height':'64dp', A+'layout_alignParentBottom':'true'})
    tree.write(home, encoding='utf-8', xml_declaration=True)
    manifest = decoded/'AndroidManifest.xml'
    tree=ET.parse(manifest); root=tree.getroot(); app=root.find('application')
    ET.SubElement(app,'activity',{A+'name':'awr.witcher.DramaActivity',A+'exported':'false',
        A+'theme':'@style/AppTheme', A+'windowSoftInputMode':'adjustResize'})
    queries=root.find('queries')
    if queries is None: queries=ET.SubElement(root,'queries')
    for name in ['com.mxtech.videoplayer.ad','idm.internet.download.manager','idm.internet.download.manager.plus','com.dv.adm']:
        if not any(e.get(A+'name')==name for e in queries.findall('package')):
            ET.SubElement(queries,'package',{A+'name':name})
    tree.write(manifest,encoding='utf-8',xml_declaration=True)

def write_aligned(zout, name, data, compression):
    info=zipfile.ZipInfo(name,(2026,9,13,0,0,0));info.compress_type=compression
    if compression==zipfile.ZIP_STORED:
        alignment=16384 if name.startswith('lib/') and name.endswith('.so') else 4
        offset=zout.fp.tell()+30+len(name.encode('utf-8'))
        padding=(-offset)%alignment
        if 0<padding<4: padding+=alignment
        if padding: info.extra=struct.pack('<HH',0xd935,padding-4)+bytes(padding-4)
    zout.writestr(info,data)

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--anime',required=True,type=pathlib.Path)
    p.add_argument('--apktool',required=True,type=pathlib.Path)
    p.add_argument('--compiler',required=True,type=pathlib.Path,help='jadx 1.5.2 all jar containing D8 and apksig')
    p.add_argument('--android-jar',required=True,type=pathlib.Path)
    p.add_argument('--keystore',type=pathlib.Path)
    p.add_argument('--alias',default='awr')
    p.add_argument('--compile-only',action='store_true')
    args=p.parse_args()
    if sha(args.anime)!=EXPECTED: raise SystemExit('Wrong Anime Witcher original: refusing to patch a different APK')
    build=ROOT/'build';build.mkdir(exist_ok=True)
    classes=build/'classes';dex=build/'dex'
    for d in [classes,dex]:
        if d.exists():shutil.rmtree(d)
        d.mkdir()
    sources=list((ROOT/'native/src').rglob('*.java'))+list((build/'generated').rglob('*.java'))
    run('java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',args.android_jar,'-d',classes,*sources)
    archive=build/'classes.jar'
    with zipfile.ZipFile(archive,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
    java_home=pathlib.Path(shutil.which('java')).resolve().parent.parent
    run('java','-cp',args.compiler,'com.android.tools.r8.D8','--min-api','21','--lib',args.android_jar,'--lib',java_home,'--output',dex,archive)
    if args.compile_only:return
    decoded=build/'host'
    if not decoded.exists():run('java','-jar',args.apktool,'d','--no-src','-f','-o',decoded,args.anime)
    # Start fresh resource edits on every build; never append a second tab bar.
    stamp=decoded/'.awr_patched'
    if not stamp.exists():patch_resources(decoded);stamp.write_text('1')
    rebuilt=build/'resource-rebuild.apk'
    run('java','-jar',args.apktool,'b',decoded,'-o',rebuilt)
    artifacts=ROOT/'artifacts';artifacts.mkdir(exist_ok=True)
    patch={}
    with zipfile.ZipFile(rebuilt) as z:
        for n in ['res/layout/activity_home.xml','AndroidManifest.xml']:patch[n]=z.read(n)
    with zipfile.ZipFile(args.anime) as original:
        names=[n for n in original.namelist() if re.fullmatch(r'classes\d*\.dex',n)]
        next_id=max(int(re.search(r'\d+',n).group()) if re.search(r'\d+',n) else 1 for n in names)+1
        for f in sorted(dex.glob('*.dex')):
            patch[f'classes{next_id}.dex']=f.read_bytes();next_id+=1
        unsigned=build/'anime-witcher-unsigned.apk'
        with zipfile.ZipFile(unsigned,'w') as out:
            for info in original.infolist():
                if info.filename.startswith('META-INF/') and re.search(r'\.(SF|RSA|DSA|EC|MF)$',info.filename,re.I):continue
                write_aligned(out,info.filename,patch.pop(info.filename,original.read(info)),info.compress_type)
            for name,data in patch.items():write_aligned(out,name,data,zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(unsigned) as out:
            assert all(out.read(n)==original.read(n) for n in names), 'Original DEX changed'
            assert out.read('resources.arsc')==original.read('resources.arsc'), 'Host resources changed'
            differences=[n for n in out.namelist() if n not in original.namelist() or out.read(n)!=original.read(n)]
            with zipfile.ZipFile(artifacts/'mt-manager-patch.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
                for n in differences:z.writestr(n,out.read(n))
                z.writestr('README.txt','Copy these entries into an exact copy of Anime Witcher 1.3.8. Replace existing entries, then sign in MT Manager. This is a development checkpoint.\n')
    report={'original_sha256':EXPECTED,'preserved_original_dex':len(names),'resources_arsc_unchanged':True,
        'changed_entries':differences,'patch_sha256':sha(artifacts/'mt-manager-patch.zip'),
        'runtime_tested':False,'signing_verified':False}
    if args.keystore:
        # Password is passed only through the environment, never committed or printed.
        if not os.environ.get('AWR_KEYSTORE_PASSWORD'):raise SystemExit('Set AWR_KEYSTORE_PASSWORD')
        output=artifacts/'anime-witcher-checkpoint.apk'
        run('java','-cp',args.compiler,ROOT/'tooling/SignApk.java',unsigned,output,args.keystore,args.alias)
        report.update(signing_verified=True,apk_sha256=sha(output))
    (artifacts/'build-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__': main()
