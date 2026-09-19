#!/usr/bin/env python3
"""Apply explicitly requested AWR branding to a decoded host; preserve every resource ID."""
import argparse,json,pathlib,xml.etree.ElementTree as ET
from PIL import Image
ROOT=pathlib.Path(__file__).resolve().parent.parent
A='{http://schemas.android.com/apk/res/android}';APP='{http://schemas.android.com/apk/res-auto}'
ET.register_namespace('android',A[1:-1]);ET.register_namespace('app',APP[1:-1])
def save(tree,path):tree.write(path,encoding='utf-8',xml_declaration=True)
def patch(decoded):
    entries=set(['resources.arsc'])
    palette={'windowBackground':'#ff101116','colorPrimary':'#ff1b1d25','colorPrimaryDark':'#ff101116','commentsBackground':'#ff1b1d25','textColor':'#fff5f5f7','textColor2':'#ffacb1c0','colorAccent':'#ffeec60a','accentTextColor':'#ffeec60a'}
    for path in (decoded/'res').glob('values*/colors.xml'):
        t=ET.parse(path);changed=False
        for node in t.getroot():
            if node.get('name') in palette:node.text=palette[node.get('name')];changed=True
        if changed:save(t,path)
    for path in (decoded/'res').glob('values*/strings.xml'):
        t=ET.parse(path);changed=False
        for node in t.getroot():
            if node.get('name')=='app_name':node.text='عالم AWR';changed=True
        if changed:save(t,path)
    for p in (decoded/'res').glob('values*/styles.xml'):
        t=ET.parse(p)
        theme=next((n for n in t.getroot() if n.get('name')=='AppTheme'),None)
        if theme is None:continue
        theme.set('parent','@style/Theme.MaterialComponents.NoActionBar')
        for key,val in {'android:windowLightStatusBar':'false','android:windowLightNavigationBar':'false','android:statusBarColor':'@color/windowBackground','android:navigationBarColor':'@color/windowBackground','android:textColorPrimary':'@color/textColor','android:textColorSecondary':'@color/textColor2','colorSurface':'@color/colorPrimary','colorOnSurface':'@color/textColor'}.items():
            node=next((n for n in theme if n.get('name')==key),None)
            if node is None:node=ET.SubElement(theme,'item',{'name':key})
            node.text=val
        save(t,p)
    # Main anime toolbar: new identity and original menu/navigation behavior.
    p=decoded/'res/layout/activity_home.xml';t=ET.parse(p)
    for n in t.iter():
        if n.tag.endswith('MaterialToolbar'):
            n.set(APP+'title','@string/app_name');n.set(APP+'subtitle','أنمي • دراما • عالم المصادر');n.set(APP+'titleTextColor','@color/textColor');n.set(APP+'subtitleTextColor','@color/textColor2');n.set(A+'elevation','0dp')
    save(t,p);entries.add(str(p.relative_to(decoded)))
    # Update card rounding/spacing without moving IDs or changing widget classes.
    for name in ['layout_anime_view_holder_small.xml','layout_anime_view_holder_small_more.xml','item_carousel.xml']:
        p=decoded/'res/layout'/name
        if not p.exists():continue
        t=ET.parse(p)
        for n in t.iter():
            if n.tag.endswith('CardView'):n.set(APP+'cardCornerRadius','14dp');n.set(APP+'cardElevation','0dp');n.set(APP+'cardBackgroundColor','@color/colorPrimary')
        save(t,p);entries.add(str(p.relative_to(decoded)))
    logo=Image.open(ROOT/'branding/awr-logo.png').convert('RGBA')
    for p in (decoded/'res').glob('mipmap-*/ic_launcher*.png'):
        with Image.open(p) as original:size=original.size
        if 'background' in p.name:img=Image.new('RGBA',size,'#101116')
        elif 'monochrome' in p.name:
            # Alpha-only artwork for Android themed launcher icons.
            small=logo.resize(size,Image.Resampling.LANCZOS);alpha=small.convert('RGB').point(lambda x:255 if x>100 else 0).convert('L');img=Image.new('RGBA',size,'white');img.putalpha(alpha)
        else:img=logo.resize(size,Image.Resampling.LANCZOS)
        img.save(p);entries.add(str(p.relative_to(decoded)))
    for p in (decoded/'res').glob('drawable-*/ic_launcher_foreground.png'):
        with Image.open(p) as old:size=old.size
        logo.resize(size,Image.Resampling.LANCZOS).save(p);entries.add(str(p.relative_to(decoded)))
    # Some splash configurations use the drawable XML foreground; retain its ID.
    for p in (decoded/'res').glob('drawable-*/ic_launcher_foreground.xml'):
        p.write_text('<?xml version="1.0" encoding="utf-8"?><bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@mipmap/ic_launcher_foreground" android:gravity="center"/>');entries.add(str(p.relative_to(decoded)))
    return sorted(entries)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('decoded',type=pathlib.Path);p.add_argument('--entries',type=pathlib.Path,required=True);a=p.parse_args();a.entries.write_text(json.dumps(patch(a.decoded),indent=2)+'\n')
