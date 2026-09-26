#!/usr/bin/env python3
"""Reproducible Pro integration. Does not alter any supplied host DEX or native code."""
import argparse, hashlib, importlib.util, json, pathlib, re, shutil, struct, subprocess, zipfile, xml.etree.ElementTree as E
from build_atheer import public, signature, OMITTED
ROOT=pathlib.Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('packing',ROOT/'build.py');packing=importlib.util.module_from_spec(spec);spec.loader.exec_module(packing)
A='{http://schemas.android.com/apk/res/android}'
def sha(p):return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
def run(*args,log):
 with open(log,'w') as stream:subprocess.run([str(x) for x in args],cwd=ROOT,check=True,stdout=stream,stderr=subprocess.STDOUT)
def main():
 p=argparse.ArgumentParser()
 for n in ['host','module','apktool','compiler','android-jar','keystore','output']:p.add_argument('--'+n,required=True,type=pathlib.Path)
 p.add_argument('--alias',required=True);a=p.parse_args()
 for n in ['host','module','apktool','compiler','android_jar','keystore','output']:setattr(a,n,getattr(a,n).resolve())
 assert sha(a.host)=='2ca1fe7d3062f9fc5a3f95f2c8d3cc6ec199a2d052b6277f7d796ba9dc869a6c','Unexpected Pro base'
 assert sha(a.module)=='d7c47e5ac6df298db31fbc06f7f0477bc2b44efa79e3fe8a5cc1b9b08e9ec44d','Unexpected recovered AWR module'
 with zipfile.ZipFile(a.android_jar) as z:assert 'java/lang/ClassLoader.class' in z.namelist(),'Use official Android SDK boot classpath'
 b=ROOT/'build/pro';b.mkdir(parents=True,exist_ok=True);h=b/'host';m=b/'module';cp=str(a.compiler.parent/'*')
 for kind,inp,dest in [('host',a.host,h),('module',a.module,m)]:run('java','-jar',a.apktool,'d','--no-src','-f','-o',dest,inp,log=b/(kind+'-decode.log'))
 orig_resources={kind:public(dest) for kind,dest in [('host',h),('module',m)]}
 hr=E.parse(h/'AndroidManifest.xml').getroot().find('application');mr=E.parse(m/'AndroidManifest.xml').getroot().find('application')
 for kind,app in [('host',hr),('module',mr)]:
  service=next(n for n in app.findall('service') if n.get(A+'name')=='com.google.firebase.components.ComponentDiscoveryService')
  (b/(kind+'-registrars.json')).write_text(json.dumps({n.get(A+'name'):n.get(A+'value') for n in service})+'\n')
 host_names={n.get(A+'name') for n in hr};ids=public(m);rows=[]
 style_dump=subprocess.check_output(['java','-cp',str(a.android_jar),'com.sun.tools.javap.Main','-constants','android.R$style'],text=True)
 styles=dict(re.findall(r'public static final int (\w+) = (\d+);',style_dump))
 for n in mr.findall('activity'):
  name=n.get(A+'name')
  if name in host_names or name in OMITTED:continue
  theme=n.get(A+'theme',mr.get(A+'theme'))
  theme=hex(int(styles[theme.split('/')[-1].replace('.','_')])) if theme.startswith('@android:') else ids[tuple(theme[1:].split('/'))]
  rows.append(name+'='+theme[2:])
 rows_path=b/'activities.txt';rows_path.write_text('\n'.join(rows)+'\n')
 assert len(rows)==117,len(rows)
 run('python3',ROOT/'tooling/pro_resources.py',h,m,ROOT/'branding/atheer-pro-logo.webp',rows_path,log=b/'branding.json')
 for kind,dest in [('host',h),('module',m)]:
  run('java','-jar',a.apktool,'b',dest,'-o',b/(kind+'-resources.apk'),log=b/(kind+'-resources.log'))
  checked=b/(kind+'-checked')
  run('java','-jar',a.apktool,'d','--no-src','--no-assets','-f','-o',checked,b/(kind+'-resources.apk'),log=b/(kind+'-checked.log'))
  current=public(checked);assert all(current.get(k)==v for k,v in orig_resources[kind].items()),kind+' resource IDs moved'
 run('python3',ROOT/'tooling/verify_atheer_startup.py',b/'host-checked/AndroidManifest.xml',b/'host-registrars.json',b/'module-registrars.json',log=b/'firebase-verification.json')
 patches=b/'source-dex';run('java','-cp',cp,ROOT/'tooling/BrandSourceDex.java',a.module,patches,log=b/'source-brand.json')
 code=b/'source-code.jar';resources=b/'source-resources.pack'
 with zipfile.ZipFile(a.module) as original,zipfile.ZipFile(b/'module-resources.apk') as compiled,zipfile.ZipFile(code,'w',zipfile.ZIP_DEFLATED) as dz,zipfile.ZipFile(resources,'w',zipfile.ZIP_DEFLATED) as rz:
  for n in original.namelist():
   if re.fullmatch(r'classes\d*\.dex',n):dz.writestr(n,(patches/n).read_bytes() if (patches/n).exists() else original.read(n))
   elif n.startswith(('assets/','lib/')):rz.writestr(n,original.read(n))
  for n in compiled.namelist():
   if n=='resources.arsc' or n.startswith('res/'):rz.writestr(n,compiled.read(n))
 with zipfile.ZipFile(code) as z:assert all(re.fullmatch(r'classes\d*\.dex',n) for n in z.namelist());dex_count=len(z.namelist())
 with zipfile.ZipFile(resources) as z:assert 'AndroidManifest.xml' not in z.namelist() and not any(re.fullmatch(r'classes\d*\.dex',n) for n in z.namelist())
 generated=b/'generated/com/atheer/shell';generated.mkdir(parents=True,exist_ok=True)
 (generated/'ModuleConfig.java').write_text('package com.atheer.shell; public final class ModuleConfig { public static final String SHA256="'+sha(code)+'"; public static final String RESOURCE_SHA256="'+sha(resources)+'"; public static final int DEFAULT_THEME='+ids[('style','AppTheme')]+'; public static final String[] ACTIVITIES={'+','.join(json.dumps(r) for r in rows)+'};}\n')
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 sources=sorted((ROOT/'pro/src').rglob('*.java'))+sorted((ROOT/'pro/stubs').rglob('*.java'))+list(generated.glob('*.java'))
 run('java','com.sun.tools.javac.Main','-source','8','-target','8','-encoding','UTF-8','-bootclasspath',a.android_jar,'-d',classes,*sources,log=b/'compile.log')
 jar=b/'shell.jar'
 with zipfile.ZipFile(jar,'w') as z:
  for f in classes.rglob('*.class'):
   if '/com/pandora/' not in str(f):z.write(f,f.relative_to(classes))
 dex=b/'dex';dex.mkdir(exist_ok=True)
 run('java','-cp',a.compiler,'com.android.tools.r8.D8','--min-api','28','--lib',a.android_jar,'--output',dex,jar,log=b/'d8.log')
 assert len(list(dex.glob('*.dex')))==1
 unsigned=b/'unsigned.apk';extra={'classes4.dex':dex/'classes.dex','assets/atheer/source-code.jar':code,'assets/atheer/source-resources.pack':resources}
 with zipfile.ZipFile(a.host) as source,zipfile.ZipFile(b/'host-resources.apk') as compiled,zipfile.ZipFile(unsigned,'w') as out:
  replaced={n for n in compiled.namelist() if n.startswith('res/') or n in ['resources.arsc','AndroidManifest.xml']}
  names={n for n in source.namelist() if not signature(n)}|replaced|set(extra)
  for n in sorted(names):
   if n in extra:data=extra[n].read_bytes();compression=zipfile.ZIP_STORED
   elif n in replaced:data=compiled.read(n);compression=compiled.getinfo(n).compress_type
   else:data=source.read(n);compression=source.getinfo(n).compress_type
   packing.write_aligned(out,n,data,compression)
 a.output.parent.mkdir(parents=True,exist_ok=True)
 run('java','-cp',cp,ROOT/'tooling/SignApk.java',unsigned,a.output,a.keystore,a.alias,log=b/'signing.log')
 unchanged=[]
 with zipfile.ZipFile(a.host) as before,zipfile.ZipFile(a.output) as after, a.output.open('rb') as raw:
  assert after.testzip() is None
  for n in before.namelist():
   if re.fullmatch(r'classes\d*\.dex',n) or n.startswith(('lib/','assets/')):
    assert before.read(n)==after.read(n),n;unchanged.append(n)
  for i in after.infolist():
   if i.compress_type==zipfile.ZIP_STORED:
    raw.seek(i.header_offset+26);nl,el=struct.unpack('<HH',raw.read(4));offset=i.header_offset+30+nl+el
    assert offset%(16384 if i.filename.startswith('lib/') and i.filename.endswith('.so') else 4)==0,i.filename
 report={'apk_sha256':sha(a.output),'apk_bytes':a.output.stat().st_size,'base_sha256':sha(a.host),'recovered_feature_sha256':sha(a.module),'unchanged_host_entries':unchanged,'source_activities':len(rows),'feature_dex_files':dex_count,'feature_code_sha256':sha(code),'feature_resources_sha256':sha(resources),'no_feature_apk':True,'preserved_resource_ids':True,'host_factory_superclass':'com.pandora.core.AppFactory','signatures_v1_v2_v3_verified':True,'requires_android':9,'runtime_device_tested':False,'catalogue_playback_verified':False,'brand':'أثير / ATHEER'}
 (ROOT/'artifacts/atheer-pro-build.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
