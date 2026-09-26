#!/usr/bin/env python3
"""Pro-base resources: retain every existing ID and native-host component."""
import sys, pathlib, copy, json, re, shutil, xml.etree.ElementTree as E
from PIL import Image
from atheer_resources import brand, A, APP, save, public

def prepare(host,module,logo,rows):
 brand(host,logo);brand(module,logo,True)
 palette={'accent':'#ffd8b56a','anime_accent':'#ffd8b56a','anime_accent_dark':'#ff947443','black_bg':'#ff0d111b','black_elevated':'#ff1b2534'}
 old={'#ff000000':'#ff0d111b','#ff0a0a0a':'#ff0d111b','#ff111111':'#ff171f2c','#ff1a1a1a':'#ff1b2534','#fc0a0a0a':'#fc0d111b','#ffe50914':'#ffd8b56a','#ffeec60a':'#ffd8b56a','#ff5b4fff':'#ffd8b56a','#ff101116':'#ff0d111b','#ff1b1d25':'#ff171f2c'}
 for root in [host,module]:
  for p in root.glob('res/values*/colors.xml'):
   tree=E.parse(p)
   for x in tree.getroot():
    if x.get('name') in palette:x.text=palette[x.get('name')]
   save(tree,p)
  for p in root.glob('res/layout*/*.xml'):
   tree=E.parse(p);edited=False
   for x in tree.iter():
    for key,val in list(x.attrib.items()):
     if ('color' in key.lower() or key.endswith('background')) and val.lower() in old:x.set(key,old[val.lower()]);edited=True
   if edited:save(tree,p)
 # Shared Arabic font in the legacy screens, using their existing resource ID.
 shutil.copyfile(host/'res/font/cairo_regular.ttf',module/'res/font/montserrat.ttf')
 # Compact masthead and floating navigation; original view IDs and types stay intact.
 p=host/'res/layout/fragment_home.xml';tree=E.parse(p)
 top=next(x for x in tree.iter() if x.get(A+'id')=='@id/topIconsBar')
 top.set(A+'background','#ef0d111b');top.set(A+'paddingTop','4dp')
 for i,x in enumerate(list(top)):
  if x.tag=='View' and x.get(A+'layout_weight')=='1.0':
   top.remove(x);top.insert(i,E.Element('TextView',{A+'text':'أثير',A+'textSize':'24sp',A+'textStyle':'bold',A+'textColor':'@color/red_primary',A+'fontFamily':'@font/cairo',A+'gravity':'center',A+'layout_width':'0dp',A+'layout_height':'48dp',A+'layout_weight':'1'}));break
 save(tree,p)
 p=host/'res/layout/item_home_banner.xml';tree=E.parse(p);root=tree.getroot()
 root.set(A+'layout_marginStart','14dp');root.set(A+'layout_marginEnd','14dp');root.set(A+'layout_marginTop','72dp');root.set(A+'layout_marginBottom','10dp')
 root.set(A+'background','@drawable/atheer_panel');root.set(A+'clipToOutline','true')
 save(tree,p)
 p=host/'res/layout/activity_main.xml';tree=E.parse(p)
 for x in tree.iter():
  if x.get(A+'id')=='@id/viewBgOverlay':x.set(A+'background','#fc0d111b')
  if x.get(A+'id')=='@id/bottomNav':
   x.set(A+'background','@drawable/atheer_panel');x.set(A+'layout_marginStart','10dp');x.set(A+'layout_marginEnd','10dp');x.set(A+'layout_marginBottom','8dp');x.set(A+'elevation','8dp')
 save(tree,p)
 p=host/'res/layout/item_home_section.xml';tree=E.parse(p)
 for x in tree.iter():
  if x.get(A+'id')=='@id/sectionHeader':x.set(A+'layout_marginTop','20dp')
  if x.get(A+'id')=='@id/tvSeeAll':x.set(A+'background','@drawable/atheer_chip');x.set(A+'paddingStart','14dp');x.set(A+'paddingEnd','14dp')
 save(tree,p)
 for name,fill,radius,stroke in [('atheer_panel','#ff171f2c','22dp','#ff29374b'),('atheer_chip','#22d8b56a','24dp','#40d8b56a')]:
  (host/'res/drawable'/f'{name}.xml').write_text(f'<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="{fill}"/><corners android:radius="{radius}"/><stroke android:width="1dp" android:color="{stroke}"/></shape>')
 # Add the isolated activities with system themes; the guest overrides translate them.
 t=E.parse(host/'AndroidManifest.xml');r=t.getroot();app=r.find('application');g=E.parse(module/'AndroidManifest.xml').getroot();ga=g.find('application')
 assert app.get(A+'appComponentFactory')=='com.pandora.core.AppFactory'
 app.set(A+'appComponentFactory','com.atheer.shell.MergeFactory');app.set(A+'label','أثير');app.set(A+'usesCleartextTraffic','true')
 known={n.get(A+'name') for n in app};theme_map=dict(line.split('=') for line in rows.read_text().splitlines())
 for n in ga.findall('activity'):
  if n.get(A+'name') not in theme_map or n.get(A+'name') in known:continue
  attrs={k:v for k,v in n.attrib.items() if not v.startswith('@') and k not in [A+'permission',A+'process',A+'taskAffinity',A+'targetActivity',A+'parentActivityName']}
  attrs.update({A+'exported':'false',A+'theme':'@android:style/Theme.Material.NoActionBar'})
  E.SubElement(app,'activity',attrs)
 provider=E.SubElement(app,'provider',{A+'name':'com.atheer.shell.SourcesFileProvider',A+'authorities':'com.drama.mp4.provider',A+'exported':'false',A+'grantUriPermissions':'true'})
 shutil.copyfile(module/'res/xml/provider_paths.xml',host/'res/xml/atheer_source_paths.xml')
 E.SubElement(provider,'meta-data',{A+'name':'android.support.FILE_PROVIDER_PATHS',A+'resource':'@xml/atheer_source_paths'})
 permissions={n.get(A+'name') for n in r.findall('uses-permission')}
 for n in g.findall('uses-permission'):
  if n.get(A+'name') not in permissions and not n.get(A+'name','').startswith('com.anime.witcher'):r.insert(0,copy.deepcopy(n));permissions.add(n.get(A+'name'))
 q=r.find('queries')
 if q is None:q=E.SubElement(r,'queries')
 names={n.get(A+'name') for n in q.findall('package')}
 for name in ['com.mxtech.videoplayer.ad','com.dv.adm','idm.internet.download.manager','idm.internet.download.manager.plus']:
  if name not in names:E.SubElement(q,'package',{A+'name':name})
 gs=next(n for n in ga.findall('service') if n.get(A+'name')=='com.google.firebase.components.ComponentDiscoveryService')
 service=E.SubElement(app,'service',{A+'name':'com.atheer.shell.SourceDiscoveryService',A+'exported':'false',A+'directBootAware':'true'})
 for n in gs:service.append(copy.deepcopy(n))
 save(t,host/'AndroidManifest.xml')
 y=host/'apktool.yml';s=y.read_text();s=re.sub(r'minSdkVersion: [^\n]+','minSdkVersion: 28',s);s=re.sub(r'versionCode: [^\n]+','versionCode: 16',s);s=re.sub(r'versionName: [^\n]+','versionName: 1.1.5-atheer.1',s);y.write_text(s)
 print(json.dumps({'activities':len(theme_map),'host_factory_superclass_preserved':True,'brand':'Atheer'},ensure_ascii=False))
if __name__=='__main__':prepare(*[pathlib.Path(p) for p in sys.argv[1:]])
