import json,pathlib,subprocess,time,sys
root=pathlib.Path(__file__).resolve().parents[1];out=root/'test-output';out.mkdir(exist_ok=True)
def adb(*args,**kwargs):return subprocess.run(['adb',*args],check=True,**kwargs)
adb('shell','wm','size','1920x1080');adb('shell','wm','density','240')
adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','0')
adb('install','-r',str(root/'build/android/test.apk'));adb('logcat','-c')
adb('shell','am','start','-n','com.palma.vision.tv/.MainActivity','--ez','selfTest','true')
seen=set();finished=False;started=time.monotonic()
while time.monotonic()-started<750:
    logs=adb('logcat','-d','-s','PALMA-TV-TEST:I','PALMA-TV:E','PALMA-Node:I','AndroidRuntime:E','*:S',capture_output=True,text=True).stdout
    for line in logs.splitlines():
        if line in seen:continue
        seen.add(line)
        if 'PALMA-TV-TEST' not in line:continue
        try:event=json.loads(line[line.index('{'):])
        except (ValueError,json.JSONDecodeError):continue
        print(json.dumps(event,ensure_ascii=False),flush=True)
        if event.get('step')=='remote-ready':adb('shell','input','keyevent','20')
        if event.get('screenshot'):
            with (out/(event['screenshot']+'.png')).open('wb') as f:adb('exec-out','screencap','-p',stdout=f)
        if event.get('done'):
            (out/'verification.json').write_text(json.dumps(event['report'],indent=2,ensure_ascii=False));finished=True
            with (out/'final.png').open('wb') as f:adb('exec-out','screencap','-p',stdout=f)
            sys.exit(0 if event.get('passed') else 1)
    time.sleep(1)
with (out/'failure.png').open('wb') as f:adb('exec-out','screencap','-p',stdout=f)
# Diagnostic text can contain remote addresses. Keep only runtime/error names rather than source requests.
safe=[line for line in logs.splitlines() if 'PALMA-TV' in line or 'FATAL EXCEPTION' in line or 'java.lang.' in line or 'Cannot ' in line or 'Error:' in line]
(out/'startup-failure.txt').write_text('\n'.join(safe))
raise SystemExit('Android TV verification timed out; inspect screenshot and startup-failure.txt')
