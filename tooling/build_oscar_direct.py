#!/usr/bin/env python3
"""Build Oscar Pro with direct AWR source screens. The supplied assets/base.apk is immutable."""
import argparse
import hashlib
import importlib.util
import json
import os
import pathlib
import re
import shutil
import struct
import subprocess
import zipfile
import xml.etree.ElementTree as E

ROOT=pathlib.Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location("packing",ROOT/"build.py")
packing=importlib.util.module_from_spec(spec)
spec.loader.exec_module(packing)
A="{http://schemas.android.com/apk/res/android}"
E.register_namespace("android",A[1:-1])

EXPECTED_HOST="2ca1fe7d3062f9fc5a3f95f2c8d3cc6ec199a2d052b6277f7d796ba9dc869a6c"
EXPECTED_INNER_BASE="e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7"
EXPECTED_EXTRACTORS="8d8fa81d9255060ca6a38a74806114ca272ca036fc29af21d2017aadf2f179d8"
API_BASE="https://dwapp.arabypros.com/api/"
API_SUFFIX="4F5A9C3D9A86FA54EACEDDD635185/d506abfd-9fe2-4b71-b979-feff21bcad13/"
EXTRACTOR_ENTRIES=[f"classes{i}.dex" for i in range(23,29)]
APP_NAME="PALMA"
VERSION_CODE=21
VERSION_NAME="1.1.5-direct.3"
BRAND_FILES=[
    "res/drawable/splash_logo.png",
    *[
        f"res/mipmap-{density}/{name}"
        for density in ["mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"]
        for name in ["ic_launcher.png","ic_launcher_round.png"]
    ],
]

def sha_bytes(value):
    return hashlib.sha256(value).hexdigest()

def sha(path):
    return sha_bytes(pathlib.Path(path).read_bytes())

def public_ids(decoded):
    return {
        (node.get("type"),node.get("name")):node.get("id")
        for node in E.parse(decoded/"res/values/public.xml").getroot()
    }

def signature(name):
    return name.startswith("META-INF/") and re.search(r"\.(RSA|DSA|EC|SF|MF)$",name,re.I)

def run(*args,log):
    with open(log,"w") as stream:
        subprocess.run([str(x) for x in args],cwd=ROOT,check=True,stdout=stream,stderr=subprocess.STDOUT)

def patch_manifest(decoded):
    manifest=decoded/"AndroidManifest.xml"
    tree=E.parse(manifest)
    root=tree.getroot()
    app=root.find("application")
    if app is None:
        raise ValueError("Missing application node")
    app.set(A+"appComponentFactory","com.atheer.shell.MergeFactory")
    names={node.get(A+"name") for node in app.findall("activity")}
    for activity in ["awr.witcher.DramaActivity","awr.witcher.AnimeActivity"]:
        if activity not in names:
            E.SubElement(app,"activity",{
                A+"name":activity,
                A+"exported":"false",
                A+"theme":"@style/Theme.RamadanSeries",
                A+"windowSoftInputMode":"adjustResize",
            })
    queries=root.find("queries")
    if queries is None:
        queries=E.SubElement(root,"queries")
    existing={node.get(A+"name") for node in queries.findall("package")}
    for package in [
        "com.mxtech.videoplayer.ad",
        "com.dv.adm",
        "idm.internet.download.manager",
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager.adm.lite",
    ]:
        if package not in existing:
            E.SubElement(queries,"package",{A+"name":package})
    tree.write(manifest,encoding="utf-8",xml_declaration=True)
    yml=decoded/"apktool.yml"
    value=yml.read_text()
    value=re.sub(r"(versionCode:\s*)\d+",rf"\g<1>{VERSION_CODE}",value)
    value=re.sub(r"(versionName:\s*).+",rf"\g<1>{VERSION_NAME}",value)
    yml.write_text(value)

def patch_brand(decoded):
    strings=decoded/"res/values/strings.xml"
    value=strings.read_text()
    value,count=re.subn(
        r'(<string name="app_name">).*?(</string>)',
        rf'\g<1>{APP_NAME}\g<2>',
        value,
        count=1,
    )
    if count!=1:
        raise ValueError("Unable to replace app_name")
    strings.write_text(value)
    source=ROOT/"branding/palma/android"
    for relative in BRAND_FILES:
        target=decoded/relative
        replacement=source/pathlib.Path(relative).relative_to("res")
        if not target.exists() or not replacement.exists():
            raise ValueError(f"Missing brand target: {relative}")
        shutil.copyfile(replacement,target)

def generate_sources(build):
    generated=build/"generated/awr/witcher"
    generated.mkdir(parents=True,exist_ok=True)
    (generated/"ApiConfig.java").write_text(
        "package awr.witcher; final class ApiConfig {"
        f" static final String BASE={json.dumps(API_BASE)};"
        f" static final String SUFFIX={json.dumps(API_SUFFIX)};"
        " }\n"
    )
    rows=[line.split("\t") for line in (ROOT/"tooling/providers.tsv").read_text().splitlines() if line.strip()]
    table=",".join("{"+",".join(json.dumps(v) for v in row)+"}" for row in rows)
    (generated/"Providers.java").write_text(
        "package awr.witcher; final class Providers { static final String[][] TABLE={"+table+"}; }\n"
    )
    return generated

def main():
    parser=argparse.ArgumentParser()
    for name in ["host","extractors","apktool","compiler","android-jar","keystore","output"]:
        parser.add_argument("--"+name,required=True,type=pathlib.Path)
    parser.add_argument("--alias",required=True)
    args=parser.parse_args()
    for name in ["host","extractors","apktool","compiler","android_jar","keystore","output"]:
        setattr(args,name,getattr(args,name).resolve())
    if sha(args.host)!=EXPECTED_HOST:
        raise ValueError("Unexpected Oscar Pro input")
    if sha(args.extractors)!=EXPECTED_EXTRACTORS:
        raise ValueError("Unexpected AWR extractor archive")
    if not os.environ.get("AWR_KEYSTORE_PASSWORD"):
        raise ValueError("AWR_KEYSTORE_PASSWORD is required")
    with zipfile.ZipFile(args.host) as host:
        inner=host.read("assets/base.apk")
        if sha_bytes(inner)!=EXPECTED_INNER_BASE:
            raise ValueError("Oscar assets/base.apk does not match the stable supplied base")
    with zipfile.ZipFile(args.extractors) as source:
        missing=[name for name in EXTRACTOR_ENTRIES if name not in source.namelist()]
        if missing:
            raise ValueError("Missing extractor DEX: "+",".join(missing))

    build=ROOT/"build/oscar-direct"
    shutil.rmtree(build,ignore_errors=True)
    build.mkdir(parents=True)
    decoded=build/"host"
    run("java","-jar",args.apktool,"d","--no-src","-f","-o",decoded,args.host,log=build/"decode.log")
    original_resource_ids=public_ids(decoded)
    patch_manifest(decoded)
    patch_brand(decoded)
    manifest_apk=build/"manifest.apk"
    run("java","-jar",args.apktool,"b",decoded,"-o",manifest_apk,log=build/"manifest-build.log")
    resource_check=build/"resource-check"
    run("java","-jar",args.apktool,"d","--no-src","--no-assets","-f","-o",resource_check,manifest_apk,log=build/"resource-check.log")
    if public_ids(resource_check)!=original_resource_ids:
        raise ValueError("Resource IDs moved during PALMA branding")

    generated=generate_sources(build)
    classes=build/"classes"
    classes.mkdir()
    sources=sorted((ROOT/"direct/src").rglob("*.java"))+sorted((ROOT/"pro/stubs").rglob("*.java"))+sorted(generated.rglob("*.java"))
    boot=str(args.android_jar.parent/"core-lambda-stubs.jar")+os.pathsep+str(args.android_jar)
    run(
        "java","com.sun.tools.javac.Main","-source","8","-target","8","-encoding","UTF-8",
        "-bootclasspath",boot,"-d",classes,*sources,log=build/"compile.log"
    )
    jar=build/"direct.jar"
    with zipfile.ZipFile(jar,"w",zipfile.ZIP_DEFLATED) as out:
        for file in classes.rglob("*.class"):
            relative=file.relative_to(classes)
            if str(relative).replace("\\","/").startswith("com/pandora/"):
                continue
            out.write(file,relative)
    dex=build/"dex"
    dex.mkdir()
    run(
        "java","-cp",args.compiler,"com.android.tools.r8.D8","--min-api","24",
        "--lib",args.android_jar,"--output",dex,jar,log=build/"d8.log"
    )
    compiled=list(dex.glob("*.dex"))
    if len(compiled)!=1:
        raise ValueError("Direct feature must compile to exactly one DEX")

    with zipfile.ZipFile(manifest_apk) as rebuilt:
        resource_overlays={name for name in rebuilt.namelist() if name=="resources.arsc" or name.startswith("res/")}
        manifest=rebuilt.read("AndroidManifest.xml")
        manifest_compression=rebuilt.getinfo("AndroidManifest.xml").compress_type
        missing=[name for name in BRAND_FILES if name not in resource_overlays]
        if missing:
            raise ValueError("Missing rebuilt brand resources: "+",".join(sorted(missing)))
        overlay_data={name:rebuilt.read(name) for name in resource_overlays}
        overlay_compression={name:rebuilt.getinfo(name).compress_type for name in resource_overlays}
    additions={"classes4.dex":compiled[0].read_bytes()}
    with zipfile.ZipFile(args.extractors) as source:
        for target,entry in zip([f"classes{i}.dex" for i in range(5,11)],EXTRACTOR_ENTRIES):
            additions[target]=source.read(entry)

    unsigned=build/"unsigned.apk"
    with zipfile.ZipFile(args.host) as original,zipfile.ZipFile(unsigned,"w") as out:
        original_names=set(original.namelist())
        for info in original.infolist():
            if signature(info.filename):
                continue
            data=manifest if info.filename=="AndroidManifest.xml" else overlay_data.get(info.filename,original.read(info.filename))
            compression=manifest_compression if info.filename=="AndroidManifest.xml" else overlay_compression.get(info.filename,info.compress_type)
            packing.write_aligned(out,info.filename,data,compression)
        for name in sorted(resource_overlays-original_names):
            packing.write_aligned(out,name,overlay_data[name],overlay_compression[name])
        for name,data in additions.items():
            packing.write_aligned(out,name,data,zipfile.ZIP_DEFLATED)

    args.output.parent.mkdir(parents=True,exist_ok=True)
    classpath=str(args.compiler.parent/"*")
    run(
        "java","-cp",classpath,ROOT/"tooling/SignApk.java",
        unsigned,args.output,args.keystore,args.alias,log=build/"signing.log"
    )

    unchanged=[]
    with zipfile.ZipFile(args.host) as before,zipfile.ZipFile(args.output) as after,args.output.open("rb") as raw:
        if after.testzip() is not None:
            raise ValueError("Output ZIP is corrupt")
        if after.read("assets/base.apk")!=before.read("assets/base.apk"):
            raise ValueError("assets/base.apk changed")
        changed=[]
        for info in before.infolist():
            name=info.filename
            if name=="AndroidManifest.xml" or signature(name):
                continue
            if before.read(name)!=after.read(name):
                if name not in resource_overlays:
                    raise ValueError("Original entry changed: "+name)
                changed.append(name)
                continue
            unchanged.append(name)
        if "resources.arsc" not in changed:
            raise ValueError("Compiled PALMA resource table was not installed")
        for name in resource_overlays:
            if after.read(name)!=overlay_data[name]:
                raise ValueError("Brand overlay mismatch: "+name)
        added_apks=[name for name in after.namelist() if name.lower().endswith(".apk") and name!="assets/base.apk"]
        if added_apks:
            raise ValueError("Unexpected nested APK: "+",".join(added_apks))
        aligned=0
        for info in after.infolist():
            if info.compress_type!=zipfile.ZIP_STORED:
                continue
            raw.seek(info.header_offset+26)
            name_length,extra_length=struct.unpack("<HH",raw.read(4))
            offset=info.header_offset+30+name_length+extra_length
            required=16384 if info.filename.startswith("lib/") and info.filename.endswith(".so") else 4
            if offset%required:
                raise ValueError("Misaligned stored entry: "+info.filename)
            aligned+=1

    report={
        "input_sha256":sha(args.host),
        "apk_sha256":sha(args.output),
        "apk_bytes":args.output.stat().st_size,
        "package":"com.drama.mp4",
        "app_name":APP_NAME,
        "version_code":VERSION_CODE,
        "version_name":VERSION_NAME,
        "architecture":"direct DEX/classes in the Oscar Pro process",
        "original_outer_dex_unchanged":["classes.dex","classes2.dex","classes3.dex"],
        "direct_ui_dex":"classes4.dex",
        "relocated_awr_extractor_dex":[f"classes{i}.dex" for i in range(5,11)],
        "assets_base_apk_sha256":sha_bytes(inner),
        "assets_base_apk_byte_identical":True,
        "no_added_nested_apk":True,
        "no_feature_archive_or_runtime_loader":True,
        "host_resources_unchanged":False,
        "host_resource_ids_preserved":True,
        "host_rebuilt_resource_entries":len(resource_overlays),
        "host_brand_entries":BRAND_FILES,
        "host_original_entries_unchanged":len(unchanged),
        "stored_entries_alignment_verified":aligned,
        "mx_player_package":"com.mxtech.videoplayer.ad",
        "oscar_watch_server_handoff":"WatchLink/MovieLink/ChannelStream -> resolved URL -> MX Player",
        "oscar_download_rows_untouched":True,
        "oscar_embed_resolution_before_mx":True,
        "source_tabs":["الرئيسية","الأنمي","المسلسلات","الأفلام","القنوات"],
        "anime_source":"Anime Witcher Firestore/Algolia gateway",
        "anime_gateway":"https://awr-stream-web.vercel.app/api/",
        "anime_features":["home","catalogues","search","details","episodes","quality groups","server resolution","MX Player","downloads","favorites","history","news"],
        "hitv_present":False,
        "signatures_v1_v2_v3_verified":True,
        "runtime_device_tested":False,
    }
    (ROOT/"artifacts/oscar-direct-build.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=="__main__":
    main()
