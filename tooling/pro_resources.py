#!/usr/bin/env python3
"""Pro-base resources: retain every existing ID and native-host component.

The Pro wrapper replaces the process AssetManager with its embedded
``assets/base.apk``.  Consequently the visible Oscar layouts must be themed in
both the outer installer and that redirected inner resource APK.
"""
import sys, pathlib, copy, json, re, shutil, xml.etree.ElementTree as E
from PIL import Image
from atheer_resources import brand, A, APP, save, public

def write_shape(root,name,fill,radius,stroke):
 p=root/'res/drawable'/f'{name}.xml'
 p.write_text(f'<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="{fill}"/><corners android:radius="{radius}"/><stroke android:width="1dp" android:color="{stroke}"/></shape>')

def restyle_original_ui(root):
 """Give the original host screens a coherent Emmy identity without moving IDs."""
 palette={'accent':'#ffd8b56a','anime_accent':'#ffd8b56a','anime_accent_dark':'#ff947443','black_bg':'#ff0d111b','black_elevated':'#ff1b2534'}
 old={'#ff000000':'#ff0d111b','#ff0a0a0a':'#ff0d111b','#ff111111':'#ff171f2c','#ff1a1a1a':'#ff1b2534','#fc0a0a0a':'#fc0d111b','#ffe50914':'#ffd8b56a','#ffeec60a':'#ffd8b56a','#ff5b4fff':'#ffd8b56a','#ff101116':'#ff0d111b','#ff1b1d25':'#ff171f2c'}
 for p in root.glob('res/values*/colors.xml'):
  tree=E.parse(p)
  for x in tree.getroot():
   if x.get('name') in palette:x.text=palette[x.get('name')]
  save(tree,p)
 for p in root.glob('res/values*/strings.xml'):
  tree=E.parse(p);edited=False
  for x in tree.getroot():
   if x.get('name')=='app_name':x.text='إيمي';edited=True
   elif x.text:
    value=x.text.replace('أثير','إيمي').replace('Atheer','EMMY').replace('Oscar TV','EMMY').replace('أوسكار تيفي','إيمي').replace('أوسكار تي في','إيمي').replace('أوسكار','إيمي')
    if value!=x.text:x.text=value;edited=True
  if edited:save(tree,p)
 for p in root.glob('res/layout*/*.xml'):
  tree=E.parse(p);edited=False
  for x in tree.iter():
   for key,val in list(x.attrib.items()):
    if ('color' in key.lower() or key.endswith('background')) and val.lower() in old:x.set(key,old[val.lower()]);edited=True
   if x.tag.endswith('CardView'):
    if x.get(APP+'cardCornerRadius') is not None:x.set(APP+'cardCornerRadius','20dp');edited=True
    if x.get(APP+'cardElevation') is not None:x.set(APP+'cardElevation','1dp');edited=True
    if x.tag.endswith('MaterialCardView'):
     x.set(APP+'strokeColor','#26d8b56a');x.set(APP+'strokeWidth','1dp');edited=True
  if edited:save(tree,p)
 write_shape(root,'atheer_panel','#f2171f2c','22dp','#ff33445d')
 write_shape(root,'atheer_chip','#22d8b56a','24dp','#66d8b56a')
 write_shape(root,'emmy_drawer_bg','#ff101722','0dp','#ff33445d')
 write_shape(root,'emmy_drawer_item','#ff1b2534','16dp','#26d8b56a')

 # Compact branded masthead. The view contract stays unchanged for host code.
 p=root/'res/layout/fragment_home.xml'
 if p.exists():
  tree=E.parse(p);top=next((x for x in tree.iter() if x.get(A+'id')=='@id/topIconsBar'),None)
  if top is not None:
   top.set(A+'background','#f20d111b');top.set(A+'paddingTop','6dp');top.set(A+'paddingBottom','6dp')
   title=next((x for x in list(top) if x.tag=='TextView' and x.get(A+'text')=='إيمي'),None)
   if title is None:
    for i,x in enumerate(list(top)):
     if x.tag=='View' and x.get(A+'layout_weight')=='1.0':
      top.remove(x);title=E.Element('TextView',{A+'text':'إيمي',A+'textSize':'25sp',A+'textStyle':'bold',A+'textColor':'@color/red_primary',A+'fontFamily':'@font/cairo',A+'gravity':'center',A+'layout_width':'0dp',A+'layout_height':'48dp',A+'layout_weight':'1'});top.insert(i,title);break
   elif title is not None:title.set(A+'textColor','@color/red_primary')
  save(tree,p)
 p=root/'res/layout/activity_splash.xml'
 if p.exists():
  tree=E.parse(p)
  for x in tree.iter():
   if x.get(A+'id')=='@id/tvAppName':x.set(A+'text','إيمي')
   elif x.get(A+'id') in ['@id/tvSubtitle','@id/tvBadge']:
    x.set(A+'text','');x.set(A+'visibility','gone');x.set(A+'alpha','0.0')
  save(tree,p)
 p=root/'res/layout/item_home_banner.xml'
 if p.exists():
  tree=E.parse(p);card=tree.getroot()
  card.set(A+'layout_marginStart','14dp');card.set(A+'layout_marginEnd','14dp');card.set(A+'layout_marginTop','72dp');card.set(A+'layout_marginBottom','10dp')
  card.set(A+'background','@drawable/atheer_panel');card.set(A+'clipToOutline','true')
  save(tree,p)
 p=root/'res/layout/activity_main.xml'
 if p.exists():
  tree=E.parse(p)
  for x in tree.iter():
   if x.get(A+'id')=='@id/viewBgOverlay':x.set(A+'background','#fc0d111b')
   if x.get(A+'id')=='@id/bottomNav':
    x.set(A+'background','@drawable/atheer_panel');x.set(A+'layout_marginStart','10dp');x.set(A+'layout_marginEnd','10dp');x.set(A+'layout_marginBottom','8dp');x.set(A+'elevation','8dp')
  save(tree,p)
 p=root/'res/layout/item_home_section.xml'
 if p.exists():
  tree=E.parse(p)
  for x in tree.iter():
   if x.get(A+'id')=='@id/sectionHeader':x.set(A+'layout_marginTop','20dp')
   if x.get(A+'id')=='@id/tvSeeAll':x.set(A+'background','@drawable/atheer_chip');x.set(A+'paddingStart','14dp');x.set(A+'paddingEnd','14dp')
  save(tree,p)

 # Distinct collection pages: framed title, gold/navy pills and roomier grid.
 for p in root.glob('res/layout/fragment_all_*.xml'):
  tree=E.parse(p);changed=False
  for x in tree.iter():
   identity=x.get(A+'id','')
   if identity=='@id/titleRow':
    x.set(A+'background','@drawable/atheer_panel');x.set(A+'paddingStart','14dp');x.set(A+'paddingEnd','10dp');x.set(A+'paddingTop','6dp');x.set(A+'paddingBottom','6dp');changed=True
   elif identity.startswith('@id/chip'):
    x.set(A+'background','@drawable/atheer_chip');x.set(A+'layout_height','36dp');x.set(A+'paddingStart','13dp');x.set(A+'paddingEnd','13dp');changed=True
   elif x.tag.endswith('RecyclerView'):
    x.set(A+'padding','14dp');changed=True
  if changed:save(tree,p)

 # A full-height navy drawer with framed, rounded menu rows.
 p=root/'res/layout/layout_drawer.xml'
 if p.exists():
  tree=E.parse(p);drawer=tree.getroot();drawer.set(A+'background','@drawable/emmy_drawer_bg');drawer.set(A+'layout_width','300dp')
  first=next((x for x in list(drawer) if x.tag=='ImageView'),None)
  if first is not None:
   first.set(A+'layout_width','96dp');first.set(A+'layout_height','96dp');first.set(A+'layout_marginTop','28dp');first.set(A+'layout_marginBottom','10dp')
  for x in drawer.iter('LinearLayout'):
   identity=x.get(A+'id','')
   if identity.startswith('@id/drawer'):
    x.set(A+'background','@drawable/emmy_drawer_item');x.set(A+'layout_height','46dp');x.set(A+'layout_marginStart','10dp');x.set(A+'layout_marginEnd','10dp');x.set(A+'layout_marginTop','2dp');x.set(A+'layout_marginBottom','2dp')
  save(tree,p)

def prepare(host,module,inner,logo,rows):
 brand(host,logo);brand(inner,logo);brand(module,logo,True)
 restyle_original_ui(host);restyle_original_ui(inner)
 # Shared Arabic font in the legacy screens, using their existing resource ID.
 shutil.copyfile(host/'res/font/cairo_regular.ttf',module/'res/font/montserrat.ttf')
 # Add the isolated activities with system themes; the guest overrides translate them.
 t=E.parse(host/'AndroidManifest.xml');r=t.getroot();app=r.find('application');g=E.parse(module/'AndroidManifest.xml').getroot();ga=g.find('application')
 assert app.get(A+'appComponentFactory')=='com.pandora.core.AppFactory'
 app.set(A+'appComponentFactory','com.atheer.shell.MergeFactory');app.set(A+'label','إيمي');app.set(A+'usesCleartextTraffic','true')
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
 y=host/'apktool.yml';s=y.read_text();s=re.sub(r'minSdkVersion: [^\n]+','minSdkVersion: 28',s);s=re.sub(r'versionCode: [^\n]+','versionCode: 18',s);s=re.sub(r'versionName: [^\n]+','versionName: 1.1.5-emmy.3',s);y.write_text(s)
 print(json.dumps({'activities':len(theme_map),'host_factory_superclass_preserved':True,'brand':'EMMY','restyled_redirected_inner_resources':True},ensure_ascii=False))
if __name__=='__main__':prepare(*[pathlib.Path(p) for p in sys.argv[1:]])
