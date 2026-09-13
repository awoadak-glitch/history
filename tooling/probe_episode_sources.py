#!/usr/bin/env python3
"""Sanitized live probe for Drama World public episode sources.

Uses normal public content GETs only. It never calls protected config/identity validation routes
and never prints source URLs, signed query values, cookies, or response bodies.
"""
import base64, json, re, sys, urllib.parse, urllib.request, urllib.error
from pathlib import Path

BASE="https://dwapp.arabypros.com/api/"
SUFFIX="4F5A9C3D9A86FA54EACEDDD635185/d506abfd-9fe2-4b71-b979-feff21bcad13/"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

def get(url, headers=None, limit=2*1024*1024):
    req=urllib.request.Request(url,headers={"User-Agent":UA,**(headers or {})})
    with urllib.request.urlopen(req,timeout=25) as r:
        data=r.read(limit+1)
        if len(data)>limit: data=data[:limit]
        return r.status,r.geturl(),dict(r.headers),data

def api(route):
    status,_,_,data=get(BASE+route+SUFFIX)
    if status!=200: raise RuntimeError(f"API HTTP {status} for {route}")
    text=data.decode("utf-8","replace").strip().lstrip("\ufeff")
    if text[:1] in "[{": return json.loads(text)
    start=text.find("W3s")
    if start>=0: return json.loads(base64.b64decode(text[start:]).decode("utf-8"))
    raise RuntimeError("Unrecognized API envelope")

def decode_transport(value):
    value=(value or "").strip()
    if value.startswith(("http://","https://")): return value
    if len(value)<=17: return value
    try:
        return base64.b64decode(value[:-17][::-1]).decode("utf-8").strip()
    except Exception:
        return value

def source_url(value):
    current=decode_transport(value)
    for _ in range(6):
        try:
            u=urllib.parse.urlsplit(current)
            if (u.hostname or "").lower() not in {"dwapp.qzz.io","dw.uns.bio"}: break
            q=urllib.parse.parse_qs(u.query).get("url")
            if not q: break
            nxt=decode_transport(q[0])
            if not nxt.startswith(("http://","https://")) or nxt==current: break
            current=nxt
        except Exception: break
    return current

def provider_keys():
    out=[]
    for line in Path("tooling/providers.tsv").read_text().splitlines():
        if line.strip(): out.append(line.split("\t",1)[0].lower())
    return out

def provider_for(host, keys):
    norm=re.sub(r"[^a-z0-9]","",host.lower())
    aliases={"ok.ru":"okru","vk.com":"vk","vk.ru":"vk"}
    exact=aliases.get(host.lower())
    for k in keys:
        if k==exact or (len(k)>=4 and k in norm): return k
    return None

def find_series():
    result=api("search/"+urllib.parse.quote("The Mentalist",safe="")+"/0/")
    posters=result.get("posters",[]) if isinstance(result,dict) else []
    for p in posters:
        if "mentalist" in str(p.get("title","")).lower() and p.get("id"):
            return p
    # fallback: scan English query variants but keep output sanitized
    for q in ["Mentalist","ذا منتاليست"]:
        result=api("search/"+urllib.parse.quote(q,safe="")+"/0/")
        posters=result.get("posters",[]) if isinstance(result,dict) else []
        for p in posters:
            if p.get("id"): return p
    raise RuntimeError("The Mentalist was not found by public search")

def episode14(series_id):
    seasons=api(f"season/by/serie/{series_id}/")
    for season in seasons if isinstance(seasons,list) else []:
        for e in season.get("episodes",[]):
            title=str(e.get("title",e.get("name","")))
            number=str(e.get("episode",e.get("number","")))
            if number=="14" or re.search(r"(?:^|\D)14(?:\D|$)",title): return e,season
    # deterministic fallback: the 14th episode of season 1
    if seasons and seasons[0].get("episodes") and len(seasons[0]["episodes"])>=14:
        return seasons[0]["episodes"][13],seasons[0]
    raise RuntimeError("Episode 14 not found")

def classify_body(data,ctype):
    text=data[:1024*1024].decode("utf-8","replace")
    stripped=text.lstrip("\ufeff\r\n\t ")
    low=text.lower()
    return {
        "kind":"hls" if stripped.startswith("#EXTM3U") else ("html" if "<html" in low or "<!doctype" in low else ("text" if "text" in ctype or "json" in ctype or "javascript" in ctype else "binary")),
        "source_tag": "<source" in low,
        "sources_array": bool(re.search(r"\bsources\s*:\s*\[",text,re.I)),
        "file_field": bool(re.search(r"(?:^|[,\s{])['\"]?file['\"]?\s*:\s*['\"]",text,re.I)),
        "src_field": bool(re.search(r"(?:^|[,\s{])['\"]?src['\"]?\s*:\s*['\"]",text,re.I)),
        "hls_text": ".m3u8" in low,
        "packed": "eval(function(p,a,c,k" in low,
        "data_page": "data-page=" in low,
    }

def main():
    keys=provider_keys();series=find_series();episode,season=episode14(int(series["id"]));eid=int(episode["id"])
    sources=api(f"episode/source/by/{eid}/")
    report={"series":{"id":series.get("id"),"title":series.get("title")},"season_title":season.get("title"),"episode":{"id":eid,"title":episode.get("title")},"source_count":len(sources),"sources":[]}
    for idx,s in enumerate(sources,1):
        raw=source_url(s.get("url",""));u=urllib.parse.urlsplit(raw);host=(u.hostname or "").lower()
        row={"index":idx,"type":str(s.get("type","")),"kind":str(s.get("kind","")),"external":bool(s.get("external",False)),"host":host,"provider_table":provider_for(host,keys),"url_path_extension":Path(u.path).suffix.lower()}
        headers={"Referer":f"{u.scheme}://{u.netloc}/"} if u.scheme and u.netloc else {}
        try:
            status,final,resp_headers,data=get(raw,headers=headers)
            fu=urllib.parse.urlsplit(final);ctype=resp_headers.get("Content-Type","").split(";",1)[0].lower()
            row.update({"http":status,"final_host":(fu.hostname or "").lower(),"content_type":ctype,"body":classify_body(data,ctype)})
        except urllib.error.HTTPError as e:
            row.update({"http":e.code,"error":"http"})
        except Exception as e:
            row.update({"http":None,"error":type(e).__name__})
        report["sources"].append(row)
    Path("diagnostics").mkdir(exist_ok=True)
    Path("diagnostics/episode14-source-probe.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__": main()
