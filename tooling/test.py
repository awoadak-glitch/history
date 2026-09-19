#!/usr/bin/env python3
"""Run isolated native UI/intent regressions without a device or external content requests."""
import argparse,os,pathlib,subprocess,tarfile,urllib.request,urllib.parse,zipfile
from xml.sax.saxutils import escape
ROOT=pathlib.Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser();parser.add_argument('--suite',choices=['NativeFlowTest','HitvFlowTest','OscarFlowTest'],default='NativeFlowTest');args=parser.parse_args()
CACHE=ROOT/'tooling/cache';CACHE.mkdir(exist_ok=True)
def fetch(url,path):
    if not path.exists():urllib.request.urlretrieve(url,path)
def run(args,**kw):subprocess.run([str(a) for a in args],check=True,**kw)
maven=CACHE/'apache-maven-3.9.9'
if not maven.exists():
    archive=CACHE/'maven.tar.gz';fetch('https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz',archive)
    with tarfile.open(archive) as t:t.extractall(CACHE,filter='data')
deps=CACHE/'test-deps';deps.mkdir(exist_ok=True)
pom=CACHE/'test-pom.xml'
pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>awr</groupId><artifactId>native-tests</artifactId><version>1</version><repositories><repository><id>google</id><url>https://dl.google.com/dl/android/maven2/</url></repository></repositories><dependencies><dependency><groupId>org.robolectric</groupId><artifactId>robolectric</artifactId><version>4.14.1</version></dependency><dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version></dependency></dependencies></project>''')
proxy=urllib.request.getproxies().get('https');settings=CACHE/'maven-settings.xml'
if proxy:
    u=urllib.parse.urlsplit(proxy);auth=''
    if u.username:auth='<username>'+escape(urllib.parse.unquote(u.username))+'</username><password>'+escape(urllib.parse.unquote(u.password or ''))+'</password>'
    settings.write_text('<settings><proxies><proxy><id>runtime</id><active>true</active><protocol>http</protocol><host>'+escape(u.hostname)+'</host><port>'+str(u.port or 80)+'</port>'+auth+'</proxy></proxies></settings>')
else:settings.write_text('<settings/>')
if not (deps/'robolectric-4.14.1.jar').exists():run([maven/'bin/mvn','-q','-s',settings,'-f',pom,'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies','-DoutputDirectory='+str(deps)])
for aar in deps.glob('*.aar'):
    with zipfile.ZipFile(aar) as z:
        if 'classes.jar' in z.namelist():aar.with_suffix('.jar').write_bytes(z.read('classes.jar'))
for version in ['14-robolectric-10818077','15-robolectric-12650502']:
    name='android-all-'+version+'.jar';dest=CACHE/name
    if version.startswith('14-') and (CACHE/'android-all-14.jar').exists() and not dest.exists():os.link(CACHE/'android-all-14.jar',dest)
    fetch('https://repo.maven.apache.org/maven2/org/robolectric/android-all/'+version+'/'+name,dest)
classes=ROOT/('build/oscar-classes' if args.suite=='OscarFlowTest' else 'build/classes');test_classes=ROOT/'build/test-classes';test_classes.mkdir(exist_ok=True)
cp=os.pathsep.join(str(p) for p in [classes,CACHE/'android-all-14-robolectric-10818077.jar',*sorted(deps.glob('*.jar'))])
run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-cp',cp,'-d',test_classes,*sorted((ROOT/'tests').glob('*.java'))])
command=['java','-Drobolectric.offline=true','-Drobolectric.usePreinstrumentedJars=false','-Drobolectric.dependency.dir='+str(CACHE),'-cp',str(test_classes)+os.pathsep+cp,'org.junit.runner.JUnitCore','awr.witcher.'+args.suite]
result=subprocess.run(command,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(ROOT/'artifacts'/('oscar-test-results.txt' if args.suite=='OscarFlowTest' else 'hitv-test-results.txt' if args.suite=='HitvFlowTest' else 'unit-test-results.txt')).write_text(result.stdout)
print(result.stdout);raise SystemExit(result.returncode)
