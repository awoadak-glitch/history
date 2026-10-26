"""Observe unmodified, hash-pinned original APKs in a fresh Android emulator.

No hooks, APK patches, certificate substitutions, or copied device secrets.
Artifacts contain UI observations and sanitized media identifiers only.
"""
import hashlib
import json
import pathlib
import re
import subprocess
import time
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'probe-output' / 'original-android'
OUT.mkdir(parents=True, exist_ok=True)
REPORT = {'environment': 'fresh Android API 35 x86_64 emulator',
          'appModified': False, 'runtimeHooks': False,
          'requestedContentPlaybackVerified': False, 'snapshots': [], 'media': []}


def adb(*args, timeout=45, check=True, binary=False):
    return subprocess.run(['adb', *args], capture_output=True, text=not binary,
                          check=check, timeout=timeout).stdout


def clean(text):
    text = re.sub(r'(?i)(?:https?|tdmvideo|oscartv)://[^\s<>"\']+', '[URL]', text)
    return re.sub(r'\b[A-Za-z0-9_-]{60,}\b', '[long-value]', text)


def snapshot(name):
    observed = adb('shell', 'CLASSPATH=/data/local/tmp/palma-network.dex:/system/framework/uiautomator.jar', 'app_process', '/system/bin', 'AndroidUiProbe', check=False)
    nodes = []
    try:
        nodes = [{k: clean(str(v)) for k,v in node.items()} for node in json.loads(observed)]
    except (ValueError, TypeError):
        pass
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    (OUT / (name + '.json')).write_text(json.dumps(nodes, ensure_ascii=False, indent=2))
    REPORT['snapshots'].append({'name': name, 'nodes': nodes})
    print(json.dumps({'snapshot': name, 'nodes': nodes}, ensure_ascii=False), flush=True)
    return nodes


def collect_media():
    raw = adb('logcat', '-d', '-v', 'brief', 'VideoPlayerActivity:D', '*:S')
    seen = set()
    for line in raw.splitlines():
        match = re.search(r'(?:Deep link parsed - URL: |Playing video from URL: )(https?://\S+?)(?:, Title:|$)', line)
        if not match:
            continue
        u = urllib.parse.urlsplit(match.group(1))
        if u.scheme not in ['https', 'http'] or u.username or u.password:
            continue
        path_hash = hashlib.sha256(u.path.encode()).hexdigest()
        if path_hash in seen:
            continue
        seen.add(path_hash)
        obj = re.search(r'/b2/(files/[A-Za-z0-9_./% -]+\.(?:css|js|m3u8|mp4))$', u.path)
        flags = dict(re.findall(r'(UA-override|Referer|Cookies): (true|false)', line))
        REPORT['media'].append({'origin': u.scheme + '://' + u.netloc,
                                'storageObject': obj.group(1) if obj else None,
                                'pathSha256': path_hash,
                                'queryNames': sorted(set(urllib.parse.parse_qs(u.query))),
                                'headerPresence': {k: v == 'true' for k, v in flags.items()}})


try:
    adb('shell', 'wm', 'size', '1080x1920')
    adb('shell', 'wm', 'density', '420')
    adb('shell', 'settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
    adb('shell', 'svc', 'wifi', 'enable')
    time.sleep(8)
    REPORT['wifiStatus'] = clean(adb('shell', 'cmd', 'wifi', 'status', check=False))
    REPORT['shellGroups'] = adb('shell', 'id')
    adb('push', str(ROOT / 'probe-input' / 'dex' / 'classes.dex'), '/data/local/tmp/palma-network.dex')
    network = adb('shell', 'CLASSPATH=/data/local/tmp/palma-network.dex', 'app_process', '/system/bin', 'AndroidNetworkProbe', timeout=90)
    REPORT['androidNetwork'] = network.strip().splitlines()
    print(json.dumps({'androidNetwork': REPORT['androidNetwork']}), flush=True)
    for name, sha in [('OscarTV.apk', 'b6b23d4f667d4662281bb2a8363d3821d36aa37ecc13e06384227dd96a45326b'),
                      ('TDM.apk', 'a0f8ee9a8eebc2ecbabf304f95c475a94c82fb2b33653aa93c629b25896db994')]:
        file = ROOT / 'probe-input' / name
        assert hashlib.sha256(file.read_bytes()).hexdigest() == sha, 'APK digest mismatch'
        adb('install', '-r', '-g', str(file), timeout=180)
    adb('logcat', '-c')
    adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', 'oscartv://w/movie/4655', '-n', 'com.drama.mp4/com.drama.mp4.ui.splash.SplashActivity')
    for delay, name in [(10, 'startup-10s'), (20, 'startup-30s'), (30, 'startup-60s')]:
        time.sleep(delay)
        snapshot(name)
    for node in REPORT['snapshots'][-1]['nodes']:
        if node.get('text') == 'إعادة المحاولة' and node.get('enabled') == 'true':
            coords = list(map(int, re.findall(r'-?\d+', node['bounds'])))
            if len(coords) == 4:
                adb('shell', 'input', 'tap', str((coords[0]+coords[2])//2), str((coords[1]+coords[3])//2))
                time.sleep(30)
                snapshot('after-retry')
            break
    collect_media()
    REPORT['sourceProcessRunning'] = bool(adb('shell', 'pidof', 'com.drama.mp4', check=False).strip())
    runtime = adb('logcat', '-d', '-v', 'brief', 'AndroidRuntime:E', '*:S')
    REPORT['crashLines'] = [clean(x) for x in runtime.splitlines() if re.search(r'FATAL EXCEPTION|Process:|Exception:|Caused by:', x)]
    REPORT['domainResolverLog'] = clean(adb('logcat', '-d', '-v', 'brief', 'DomainResolver:V', '*:S')).strip().splitlines()
except Exception as error:
    REPORT['errorType'] = type(error).__name__
    print('Diagnostic stopped: ' + type(error).__name__, flush=True)
finally:
    (OUT / 'report.json').write_text(json.dumps(REPORT, ensure_ascii=False, indent=2))
    print(json.dumps({k: v for k, v in REPORT.items() if k != 'snapshots'}, ensure_ascii=False), flush=True)
