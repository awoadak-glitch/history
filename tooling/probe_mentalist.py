#!/usr/bin/env python3
import base64, json, urllib.parse, urllib.request

BASE='https://dwapp.arabypros.com/api/'
SUFFIX='4F5A9C3D9A86FA54EACEDDD635185/d506abfd-9fe2-4b71-b979-feff21bcad13/'

def get(route):
    req=urllib.request.Request(BASE+route+SUFFIX,headers={'User-Agent':'okhttp/4.12.0'})
    with urllib.request.urlopen(req,timeout=30) as r:
        raw=r.read().decode('utf-8','replace').strip()
    if raw.startswith('{') or raw.startswith('['):
        return json.loads(raw)
    i=raw.find('W3s')
    if i>=0:
        return json.loads(base64.b64decode(raw[i:]).decode('utf-8'))
    raise RuntimeError('unknown envelope '+raw[:80])

def brief_source(s):
    u=s.get('url') or ''
    try:
        p=urllib.parse.urlparse(u)
        loc=(p.hostname or '')+(p.path[-50:] if p.path else '')
    except Exception:
        loc='<parse-error>'
    return {k:s.get(k) for k in ('id','title','quality','size','kind','premium','external','type')} | {'url_shape':loc}

search=get('search/'+urllib.parse.quote('The Mentalist',safe='')+'/0/')
print('SEARCH_TYPE',type(search).__name__)
print('SEARCH',json.dumps(search,ensure_ascii=False)[:4000])
posters=search.get('posters',[]) if isinstance(search,dict) else []
series=None
for p in posters:
    if 'mentalist' in (p.get('title') or '').lower():
        series=p;break
if not series:
    raise SystemExit('The Mentalist not found in posters')
print('SERIES',json.dumps({k:series.get(k) for k in series.keys() if k in ('id','title','type','playas','downloadas','label','sublabel')},ensure_ascii=False))
seasons=get(f"season/by/serie/{series['id']}/")
print('SEASON_COUNT',len(seasons))
chosen=None
for si,season in enumerate(seasons):
    eps=season.get('episodes') or []
    print('SEASON',si,season.get('id'),season.get('title'),'episodes',len(eps))
    for ei,ep in enumerate(eps):
        if ei in (0,5,13) or '14' in str(ep.get('title','')):
            print('EP',si,ei,json.dumps({k:ep.get(k) for k in ('id','title','playas','downloadas','duration')},ensure_ascii=False))
        title=str(ep.get('title',''))
        if title.strip() in ('14','الحلقة 14','Episode 14') or title.endswith('14'):
            chosen=ep
    if chosen: break
if chosen is None:
    for season in seasons:
        eps=season.get('episodes') or []
        if len(eps)>=14:
            chosen=eps[13];break
if chosen is None: raise SystemExit('episode 14 not found')
print('CHOSEN_EP',json.dumps({k:chosen.get(k) for k in chosen.keys() if k in ('id','title','playas','downloadas','duration')},ensure_ascii=False))
sources=get(f"episode/source/by/{chosen['id']}/")
print('SOURCE_COUNT',len(sources))
for i,s in enumerate(sources,1):
    print('SOURCE',i,json.dumps(brief_source(s),ensure_ascii=False))
