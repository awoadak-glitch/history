"""Offline call/bytecode inventory for the owner's supplied OscarTV APK.

Requires androguard. No networking, app execution, patching, or credentials.
Usage: python trace-oscar-playback.py --apk app.apk --output trace-directory
"""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX
from androguard.core.analysis.analysis import Analysis
from androguard.decompiler.decompiler import DecompilerDAD

parser = argparse.ArgumentParser()
parser.add_argument('--apk', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
apk = APK(str(args.apk))
analysis = Analysis()
with ZipFile(args.apk) as archive:
    dexes = [DEX(archive.read(name)) for name in archive.namelist()
             if name.startswith('classes') and name.endswith('.dex') and '/' not in name]
for dex in dexes:
    analysis.add(dex)
analysis.create_xref()

def method_id(method):
    return method.class_name + '->' + method.name + method.descriptor

seeds = {'Lga5;', 'Lqq5;', 'Lq95;', 'Lj8;', 'Lt94;', 'Lea6;', 'Ltf3;',
         'Lgi1;', 'Lbf0;', 'Li63;', 'Lea2;', 'Lvq5;', 'Lbr5;', 'Laa2;',
         'Luf3;', 'Lf9;', 'Ll4;', 'Lda5;', 'Lm85;', 'Lty;'}
selected_methods = {'Ln66;': {'R', 'V', 'j0', 'N'}, 'Lh32;': {'L', 'M', 'Y', 'Z'},
                    'Lgl0;': {'B', 'I'}, 'Lc21;': {'K', 'Q', 'X'},
                    'Lfi1;': {'a', 'invoke'}, 'Lj44;': {'K', 'b0'},
                    'Lg32;': {'K', 'I'}}
references = ('Lga5;->', 'Lcom/drama/mp4/security/V3ResolveData;->getTdm_guard',
              'Lcom/drama/mp4/security/V3ResolveData;->getTamper_guard',
              'Lcom/drama/mp4/security/TdmGuardInfo;->getAction',
              'Lbr5;->f(', 'Lea6;->j(', 'Ltf3;-><init>',
              'Lcom/drama/mp4/security/V3ResolveData;->getDeep_link',
              'Ln66;->R(', 'Ln66;->V(', 'Lh32;->L(', 'Lh32;->M(',
              'Lgl0;->B(', 'Lgl0;->I(', 'Ln66;->d Lda5;',
              'Lf9;-><init>', 'Ll4;-><init>')
strings = ['X-D-Sig', 'X-TE', 'X-TG', 'X-AB', 'hot_token', 'tdm://',
           'com.tdm.manager', 'tdm_guard', 'ot=', 'oscar-te-v1|']
string_refs = {}
for value in strings:
    item = analysis.get_strings_analysis().get(value)
    if item:
        refs = sorted(method_id(m) for c, m in item.get_xref_from())
        string_refs[value] = refs

selected = []
for dex in dexes:
    decompiler = DecompilerDAD(dex, analysis)
    dex.set_decompiler(decompiler)
    for cls in dex.get_classes():
        for method in cls.get_methods():
            instructions = list(method.get_instructions())
            targeted = (cls.get_name() in seeds or
                        method.get_name() in selected_methods.get(cls.get_name(), set()))
            related = targeted or any(
                any(ref in ins.get_output() for ref in references) for ins in instructions)
            if not related:
                continue
            ident = method_id(method)
            filename = (cls.get_name()[1:-1].replace('/', '_') + '__' +
                        method.get_name().replace('<', '').replace('>', '') + '__' +
                        hashlib.sha256(ident.encode()).hexdigest()[:8])
            offset = 0
            lines = [ident, 'Byte offsets are relative to the Dalvik method body.']
            for ins in instructions:
                lines.append(f'{offset:04x}  {ins.get_name()} {ins.get_output()}')
                offset += ins.get_length()
            (args.output / (filename + '.dalvik.txt')).write_text('\n'.join(lines) + '\n')
            indexed = analysis.get_method(method)
            selected.append({'method': ident, 'file': filename + '.dalvik.txt',
                             'callers': sorted(method_id(m) for c, m, off in indexed.get_xref_from()),
                             'calls': sorted(set(method_id(m) for c, m, off in indexed.get_xref_to()))})
            if targeted:
                try:
                    source = decompiler.get_source_method(method)
                    (args.output / (filename + '.java')).write_text(source)
                except Exception:
                    pass  # Dalvik remains authoritative if decompilation fails.

report = {'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
          'package': apk.get_package(), 'versionName': apk.get_androidversion_name(),
          'versionCode': apk.get_androidversion_code(),
          'strings': string_refs, 'methods': selected}
(args.output / 'index.json').write_text(json.dumps(report, indent=2))
print(json.dumps({'apk_sha256': report['apk_sha256'], 'methods': len(selected),
                  'string_references': string_refs}, indent=2))
