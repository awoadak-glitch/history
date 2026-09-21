#!/usr/bin/env python3
"""Rebuild Atheer from the two owner-supplied inputs. Requires the retained private signing key."""
import argparse,hashlib,importlib.util,json,pathlib,re,shutil,struct,subprocess,zipfile,xml.etree.ElementTree as E
ROOT=pathlib.Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('basebuild',ROOT/'build.py');base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
A='{http://schemas.android.com/apk/res/android}'
EXPECTED_HOST='e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7'
EXPECTED_MODULE='e79a2a945b4a73ee0a4f62f46d2901f14d3ce09a7784d6828e8a01bb6fef0388'
OMITTED={'com.android.billingclient.api.ProxyBillingActivity','com.example.animewitcher.activites.OurAppsActivity','com.example.animewitcher.activites.StreamActivity','com.example.animewitcher.activites.news.NewsDetailsActivity'}
def run(*args,log=None):
 with open(log,'w') if log else open('/dev/stdout','w') as out:subprocess.run([str(x) for x in args],cwd=ROOT,check=True,stdout=out,stderr=subprocess.STDOUT)
def sha(p):return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
def signature(n):return n.startswith('META-INF/') and re.search(r'\.(RSA|DSA|EC|SF|MF)$',n,re.I)
def public(p):return {(n.get('type'),n.get('name')):n.get('id') for n in E.parse(p/'res/values/public.xml').getroot()}
def repack(original,resources,dexdir,out,extra=None):
 with zipfile.ZipFile(original) as src,zipfile.ZipFile(resources) as res,zipfile.ZipFile(out,'w') as dest:
  new_resources={n for n in res.namelist() if n.startswith('res/') or n in ['resources.arsc','AndroidManifest.xml']}
  names={n for n in src.namelist() if not signature(n)}|new_resources|set(extra or {})
  for n in sorted(names):
   patch=dexdir/n
   if n in (extra or {}):data=(extra or {})[n].read_bytes();compression=zipfile.ZIP_STORED
   elif patch.is_file():data=patch.read_bytes();compression=zipfile.ZIP_DEFLATED
   elif n in new_resources:data=res.read(n);compression=res.getinfo(n).compress_type
   else:data=src.read(n);compression=src.getinfo(n).compress_type
   base.write_aligned(dest,n,data,compression)
def main():
 p=argparse.ArgumentParser()
 for n in ['host','module','apktool','compiler','android-jar','keystore','output']:p.add_argument('--'+n,type=pathlib.Path,required=True)
 p.add_argument('--alias',required=True);p.add_argument('--prepared',action='store_true');a=p.parse_args()
 for n in ['host','module','apktool','compiler','android_jar','keystore','output']:setattr(a,n,getattr(a,n).resolve())
 if sha(a.host)!=EXPECTED_HOST or sha(a.module)!=EXPECTED_MODULE:raise ValueError('Unrecognized input APK; review instead of applying blindly')
 b=ROOT/'build';h=b/'atheer-base';m=b/'atheer-module';cp=str(a.compiler.parent/'*');rows_path=b/'module-activities.txt'
 if not a.prepared:
  run('java','-jar',a.apktool,'d','--no-src','-f','-o',h,a.host,log=b/'atheer-host-decode.log')
  run('java','-jar',a.apktool,'d','--no-src','-f','-o',m,a.module,log=b/'atheer-module-decode.log')
  hr=E.parse(h/'AndroidManifest.xml').getroot().find('application');mr=E.parse(m/'AndroidManifest.xml').getroot().find('application')
  host_names={n.get(A+'name') for n in hr};ids=public(m);rows=[]
  style_dump=subprocess.check_output(['java','-cp',str(a.android_jar),'com.sun.tools.javap.Main','-constants','android.R$style'],text=True)
  system_styles=dict(re.findall(r'public static final int (\w+) = (\d+);',style_dump))
  for n in mr.findall('activity'):
   name=n.get(A+'name')
   if name in host_names or name in OMITTED:continue
   theme=n.get(A+'theme',mr.get(A+'theme'))
   theme=hex(int(system_styles[theme.split('/')[-1].replace('.','_')])) if theme.startswith('@android:') else ids[tuple(theme[1:].split('/'))]
   rows.append(name+'='+theme[2:])
  rows_path.write_text('\n'.join(rows)+'\n')
  run('python',ROOT/'tooling/atheer_resources.py',h,m,ROOT/'branding/atheer-logo.png',rows_path,log=b/'atheer-branding.json')
  for kind,inp in [('host',a.host),('module',a.module)]:
   args=['java','-cp',cp,ROOT/'tooling/AtheerDex.java',kind,inp,b/('atheer-'+kind+'-dex')]
   if kind=='module':args.append(rows_path)
   run(*args,log=b/('atheer-'+kind+'-patch.json'))
  run('java','-jar',a.apktool,'b',h,'-o',b/'atheer-host-resources.apk',log=b/'atheer-host-resources.log')
  run('java','-jar',a.apktool,'b',m,'-o',b/'atheer-module-resources.apk',log=b/'atheer-module-resources.log')
 # Independent resource decode confirms preserved original IDs after aapt linking.
 for name,decoded in [('host',h),('module',m)]:
  checked=b/('atheer-'+name+'-check')
  run('java','-jar',a.apktool,'d','--no-src','--no-assets','-f','-o',checked,b/('atheer-'+name+'-resources.apk'),log=b/('atheer-'+name+'-check.log'))
  old,new=public(decoded),public(checked)
  if any(new.get(k)!=v for k,v in old.items()):raise ValueError('Resource IDs changed in '+name)
 feature=b/'atheer-sources.apk'
 repack(a.module,b/'atheer-module-resources.apk',b/'atheer-module-dex',feature)
 rows=rows_path.read_text().splitlines();default_theme=public(m)[('style','AppTheme')]
 generated=b/'atheer-generated/com/atheer/shell';generated.mkdir(parents=True,exist_ok=True)
 (generated/'ModuleConfig.java').write_text('package com.atheer.shell; public final class ModuleConfig {public static final String SHA256="'+sha(feature)+'"; public static final int DEFAULT_THEME='+default_theme+'; public static final String[] ACTIVITIES={'+','.join(json.dumps(x) for x in rows)+'};}\n')
 classes=b/'atheer-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 sources=sorted((ROOT/'atheer/src').rglob('*.java'))+list(generated.glob('*.java'))
 run('java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',a.android_jar,'-d',classes,*sources,log=b/'atheer-javac.log')
 jar=b/'atheer.jar'
 with zipfile.ZipFile(jar,'w') as z:
  for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 d=b/'atheer-shell-dex';d.mkdir(exist_ok=True);java_home=pathlib.Path(shutil.which('java')).resolve().parent.parent
 run('java','-cp',a.compiler,'com.android.tools.r8.D8','--min-api','28','--lib',a.android_jar,'--lib',java_home,'--output',d,jar,log=b/'atheer-d8.log')
 if len(list(d.glob('*.dex')))!=1:raise ValueError('Unexpected shell DEX layout')
 unsigned=b/'atheer-unsigned.apk'
 repack(a.host,b/'atheer-host-resources.apk',b/'atheer-host-dex',unsigned,{'classes2.dex':d/'classes.dex','assets/atheer/sources.apk':feature})
 a.output.parent.mkdir(parents=True,exist_ok=True)
 run('java','-cp',cp,ROOT/'tooling/SignApk.java',unsigned,a.output,a.keystore,a.alias,log=b/'atheer-signing.log')
 v=subprocess.run(['java','-cp',cp,str(ROOT/'tooling/VerifyApk.java'),str(a.host),str(a.output)],cwd=ROOT,capture_output=True,text=True)
 (b/'atheer-signature-check.log').write_text(v.stdout+v.stderr)
 if v.returncode not in [0,42]:raise ValueError('APK signature verification failed')
 libs={};aligned=0
 with zipfile.ZipFile(a.host) as old,zipfile.ZipFile(a.output) as new,a.output.open('rb') as raw,zipfile.ZipFile(a.module) as gm,zipfile.ZipFile(feature) as fm:
  if new.testzip() is not None or fm.testzip() is not None:raise ValueError('ZIP corruption')
  for n in old.namelist():
   if n.startswith('lib/'):
    assert old.read(n)==new.read(n),n;libs[n]=hashlib.sha256(new.read(n)).hexdigest()
  for n in gm.namelist():
   if n.startswith('lib/') or n.startswith('assets/'):assert gm.read(n)==fm.read(n),n
  for i in new.infolist():
   if i.compress_type!=zipfile.ZIP_STORED:continue
   raw.seek(i.header_offset+26);nl,el=struct.unpack('<HH',raw.read(4));offset=i.header_offset+30+nl+el
   assert offset%(16384 if i.filename.startswith('lib/') and i.filename.endswith('.so') else 4)==0,i.filename;aligned+=1
  assert hashlib.sha256(new.read('assets/atheer/sources.apk')).hexdigest()==sha(feature)
 report={'apk_sha256':sha(a.output),'apk_bytes':a.output.stat().st_size,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'base_apk_sha256':sha(a.host),'module_input_sha256':sha(a.module),'embedded_module_sha256':sha(feature),'module_activities':len(rows),'package':'com.drama.mp4','label':'أثير','android_min_sdk':28,'new_certificate':True,'signature_v1_v2_v3_valid':True,'source_resource_ids_preserved':True,'module_assets_and_native_libraries_unchanged':True,'host_native_libraries_unchanged':libs,'stored_entries_alignment_verified':aligned,'installed_as_separate_app':False,'runtime_tested_on_device':False,'live_catalogue_verified':False,'live_playback_verified':False,'limitations':['Not an in-place update of original Oscar signed with another certificate.','Embedded activity lifecycle, native Anime execution, SDK initialization, and server acceptance require device testing.','Original AWR watch history is not migrated from its separate installed package.','Four dangling source manifest activities were excluded; background third-party services are not fully migrated.']}
 (ROOT/'artifacts/atheer-build.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
