#!/usr/bin/env python3
"""Recover provider -> exact original class/method table from a JADX analysis."""
import ast,pathlib,re,sys
root=pathlib.Path(sys.argv[1]);source=(root/'Q0/b.java').read_text()
def number(s):
 s=s.strip()
 if s=='oa.f45548S':return ord('=')
 if s.startswith("'"):return ord(ast.literal_eval(s))
 return int(s)
keys={}
for m in re.finditer(r'strA2\.equals\("([^\"]+)"\).*?c7 = ([^;]+);',source,re.S):
 try:keys[m.group(1)]=number(m.group(2))
 except (ValueError,SyntaxError):pass
aliases={}
for f in (root/'S0').glob('*.java'):
 renamed=re.search(r'/\* renamed from: (S0\.\w+)',f.read_text())
 aliases[f.stem]=renamed.group(1) if renamed else 'S0.'+f.stem
methods={}
for m in re.finditer(r'case (.+?):\s+(\w+)\.(\w+)\((.*?)\);',source,re.S):
 if 'b.this.f3384b' not in m.group(4):continue
 try:methods[number(m.group(1))]=(aliases[m.group(2)],m.group(3),'1' if 'b.this.f3383a' in m.group(4) else '0')
 except (ValueError,SyntaxError,KeyError):pass
fixes={'liiivideo':('S0.H','b','0'),'fembed':('S0.l','d','0'),'gounlimited':('S0.v','c','0'),'videoBIN':('S0.m0','b','0'),'vidspeed':('S0.x0','i','0')}
rows=[]
for key,n in keys.items():
 target=fixes.get(key,methods.get(n))
 if target is None:raise SystemExit('Unresolved provider '+key)
 rows.append('\t'.join((key,*target)))
if len(rows)!=68:raise SystemExit('Expected 68 providers, got '+str(len(rows)))
path=pathlib.Path(__file__).resolve().parent/'providers.tsv';path.write_text('\n'.join(rows)+'\n');print('Mapped',len(rows),'original provider entry points')
