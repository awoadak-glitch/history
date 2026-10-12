"""Build the portable ZIP from an already built EXE and the verified mpv archive."""
import argparse,hashlib,io,json,shutil,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MPV_SHA256='a49811c0752c108b8260636f9c6f6fcb97406641c98b30f1e7b500dfb20177de'
MPV_URL='https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-x86_64-w64-mingw32.zip'
def extract_safe(archive,destination):
 with zipfile.ZipFile(archive) as z:
  for i in z.infolist():
   if not (destination/i.filename).resolve().is_relative_to(destination.resolve()):raise ValueError('Invalid archive path')
  z.extractall(destination)
def package(exe,archive,output):
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=MPV_SHA256:raise ValueError('mpv checksum mismatch')
 if exe.read_bytes()[:2]!=b'MZ':raise ValueError('AWR MX is not a Windows executable')
 with tempfile.TemporaryDirectory(prefix='awr-package-') as tmp:
  stage=Path(tmp)/'AWR-MX-Desktop';stage.mkdir()
  unpack=Path(tmp)/'upstream';unpack.mkdir();extract_safe(archive,unpack)
  engines=list(unpack.rglob('mpv.exe'))
  if not engines:
   for inner in list(unpack.rglob('*.zip'))[:3]:extract_safe(inner,inner.with_suffix(''))
   engines=list(unpack.rglob('mpv.exe'))
  if len(engines)!=1:raise ValueError('mpv.exe not uniquely identified')
  shutil.copytree(engines[0].parent,stage/'player')
  shutil.copy2(exe,stage/'AWR-MX-Desktop.exe')
  for name in ['Setup-MPV.cmd','Setup-MPV.ps1','Connect-PALMA.cmd','README-AR.txt','THIRD-PARTY-NOTICES.txt','LICENSE-GPL-2.0.txt']:
   shutil.copy2(ROOT/name,stage/name)
  shutil.copytree(ROOT/'source',stage/'source',ignore=shutil.ignore_patterns('*.exe','*.test','__pycache__'))
  manifest={'version':'7.0.0','source_repository':'https://github.com/awoadak-glitch/history/tree/main/awr-mx-desktop','mpv_archive':MPV_URL,'mpv_archive_sha256':MPV_SHA256,'exe_sha256':hashlib.sha256(exe.read_bytes()).hexdigest()}
  (stage/'build-info.json').write_text(json.dumps(manifest,indent=2)+'\n')
  output.parent.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
   for file in sorted(stage.rglob('*')):
    if file.is_file():z.write(file,file.relative_to(stage))
 print(json.dumps({'file':str(output),'bytes':output.stat().st_size,'sha256':hashlib.sha256(output.read_bytes()).hexdigest()}))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--exe',type=Path,required=True);p.add_argument('--engine',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();package(a.exe,a.engine,a.output)
