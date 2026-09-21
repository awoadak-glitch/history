#!/usr/bin/env python3
"""Prepare branded Oscar host and an isolated, resource-ID-preserving AWR module."""
import argparse,copy,json,pathlib,re,shutil,xml.etree.ElementTree as E
from PIL import Image
A='{http://schemas.android.com/apk/res/android}';APP='{http://schemas.android.com/apk/res-auto}'
E.register_namespace('android',A[1:-1]);E.register_namespace('app',APP[1:-1])
def save(t,p):t.write(p,encoding='utf-8',xml_declaration=True)
def public(root):return {f'@{x.get("type")}/{x.get("name")}':x.get('id') for x in E.parse(root/'res/values/public.xml').getroot()}
def brand(root,logo,module=False):
 palette={'red_primary':'#ffd8b56a','red_light':'#ffffdf9b','red_dark':'#ff947443','black_pure':'#ff0d111b','black_background':'#ff0d111b','black_surface':'#ff171f2c','black_card':'#ff1b2534','grey_text':'#ffacb7c9','grey_hint':'#ff8592a8','white_primary':'#fff6f0e5','shimmer_base':'#ff1b2534','shimmer_highlight':'#ff29374b','windowBackground':'#ff0d111b','colorPrimary':'#ff171f2c','colorPrimaryDark':'#ff0d111b','colorAccent':'#ffd8b56a','commentsBackground':'#ff171f2c','textColor':'#fff6f0e5','textColor2':'#ffacb7c9','accentTextColor':'#ffd8b56a'}
 for p in root.glob('res/values*/colors.xml'):
  t=E.parse(p)
  for n in t.getroot():
   if n.get('name') in palette:n.text=palette[n.get('name')]
  save(t,p)
 for p in root.glob('res/values*/strings.xml'):
  t=E.parse(p)
  for n in t.getroot():
   if n.get('name')=='app_name':n.text='عالم المصادر' if module else 'أثير'
   elif n.text and ('أوسكار' in n.text or 'Oscar TV' in n.text):n.text=n.text.replace('أوسكار تيفي','أثير').replace('أوسكار تي في','أثير').replace('أوسكار','أثير').replace('Oscar TV','Atheer')
  save(t,p)
 for p in root.glob('res/layout*/*.xml'):
  t=E.parse(p);changed=False
  for n in t.iter():
   text=n.get(A+'text','')
   if 'أوسكار' in text or 'Oscar TV' in text:
    n.set(A+'text',text.replace('أوسكار تيفي','أثير').replace('أوسكار','أثير').replace('Oscar TV','Atheer'));changed=True
   if n.get(A+'id')=='@id/tvSubtitle' and p.name=='activity_splash.xml':n.set(A+'text','كل حكاية لها أثير');changed=True
   if n.get(A+'id')=='@id/tvBadge' and p.name=='activity_splash.xml':n.set(A+'text','أنمي • دراما • سينما');changed=True
   if module and n.tag.endswith('MaterialToolbar') and p.name=='activity_home.xml':n.set(APP+'title','عالم المصادر');n.set(APP+'subtitle','أنمي • مسلسلات • أفلام • قنوات');changed=True
   if n.tag.endswith('CardView') and n.get(APP+'cardCornerRadius'):
    n.set(APP+'cardCornerRadius','16dp');n.set(APP+'cardElevation','0dp');changed=True
  if changed:save(t,p)
 for name in ['rounded_card.xml','bg_card_final.xml','bg_card_outlined.xml','badge_sheet_card.xml','bg_comment_card.xml']:
  p=root/'res/drawable'/name
  if not p.exists():continue
  t=E.parse(p)
  for n in t.iter('corners'):n.set(A+'radius','16dp')
  save(t,p)
 # Replace every launcher-density image and the splash asset under existing names/IDs.
 image=Image.open(logo).convert('RGBA')
 for p in root.glob('res/*/*'):
  if p.suffix.lower() not in ['.png','.webp']:continue
  if p.stem.startswith('ic_launcher') or p.stem=='splash_logo':
   with Image.open(p) as old:size=old.size
   image.resize(size,Image.Resampling.LANCZOS).save(p)
 # Adaptive icon foregrounds may be vector XML. Repoint to existing splash/logo PNG.
 for p in root.glob('res/mipmap-anydpi*/ic_launcher*.xml'):
  t=E.parse(p)
  for n in t.getroot():
   if n.tag=='background':n.set(A+'drawable','@color/windowBackground' if module else '@color/black_pure')
   elif n.tag in ['foreground','monochrome']:
    if not module:n.set(A+'drawable','@drawable/splash_logo')
  save(t,p)

def main():
 p=argparse.ArgumentParser();p.add_argument('host',type=pathlib.Path);p.add_argument('module',type=pathlib.Path);p.add_argument('logo',type=pathlib.Path);p.add_argument('rows',type=pathlib.Path);a=p.parse_args()
 brand(a.host,a.logo);brand(a.module,a.logo,True)
 t=E.parse(a.host/'AndroidManifest.xml');r=t.getroot();app=r.find('application');g=E.parse(a.module/'AndroidManifest.xml').getroot();ga=g.find('application')
 app.set(A+'appComponentFactory','com.atheer.shell.MergeFactory');app.set(A+'label','أثير');app.set(A+'usesCleartextTraffic','true')
 existing={n.get(A+'name') for n in app};theme_map=dict(line.split('=') for line in a.rows.read_text().splitlines())
 for n in ga.findall('activity'):
  if n.get(A+'name') not in theme_map or n.get(A+'name') in existing:continue
  attrs={k:v for k,v in n.attrib.items() if not v.startswith('@') and k not in [A+'permission',A+'process',A+'taskAffinity',A+'targetActivity',A+'parentActivityName']}
  attrs.update({A+'exported':'false',A+'theme':'@android:style/Theme.Material.NoActionBar'})
  E.SubElement(app,'activity',attrs)
 provider=E.SubElement(app,'provider',{A+'name':'com.atheer.shell.SourcesFileProvider',A+'authorities':'com.drama.mp4.provider',A+'exported':'false',A+'grantUriPermissions':'true'})
 paths='@xml/atheer_source_paths'
 shutil.copyfile(a.module/'res/xml/provider_paths.xml',a.host/'res/xml/atheer_source_paths.xml')
 E.SubElement(provider,'meta-data',{A+'name':'android.support.FILE_PROVIDER_PATHS',A+'resource':paths})
 permissions={n.get(A+'name') for n in r.findall('uses-permission')}
 for n in g.findall('uses-permission'):
  if n.get(A+'name') not in permissions and not n.get(A+'name','').startswith('com.anime.witcher'):
   r.insert(0,copy.deepcopy(n));permissions.add(n.get(A+'name'))
 queries=r.find('queries')
 if queries is None:queries=E.SubElement(r,'queries')
 names={x.get(A+'name') for x in queries.findall('package')}
 for name in ['com.mxtech.videoplayer.ad','com.dv.adm','idm.internet.download.manager','idm.internet.download.manager.plus']:
  if name not in names:E.SubElement(queries,'package',{A+'name':name})
 discovery='com.google.firebase.components.ComponentDiscoveryService'
 gs=next((n for n in ga.findall('service') if n.get(A+'name')==discovery),None)
 if gs is None:raise ValueError('Source Firebase discovery metadata missing')
 # Each isolated Firebase runtime must discover only its own registrars. Combining
 # these services registers old/new coroutine dispatchers twice and crashes the
 # host FirebaseInitProvider before Application.onCreate or the launcher activity.
 source_discovery=E.SubElement(app,'service',{A+'name':'com.atheer.shell.SourceDiscoveryService',A+'exported':'false',A+'directBootAware':'true'})
 for n in gs:source_discovery.append(copy.deepcopy(n))
 save(t,a.host/'AndroidManifest.xml')
 y=a.host/'apktool.yml';s=y.read_text();s=re.sub(r'minSdkVersion: [^\n]+','minSdkVersion: 28',s);y.write_text(s)
 print(json.dumps({'host_package':r.get('package'),'module_activities':len(theme_map),'file_provider_paths':paths,'min_sdk':28}))
if __name__=='__main__':main()
