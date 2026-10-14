import {createHash} from 'node:crypto';
import {gunzipSync} from 'node:zlib';
import {mkdir,readFile,writeFile} from 'node:fs/promises';

const packages=[
  {name:'mpegts.js',version:'1.8.2',integrity:'cZYMa5muASH55wrS6JK5IGkUqnCA96c0f+WdnW91jIK9s/N6NzjmTQDJNYg9A5u/G2Q5nj2b3w1Zwzze/9guxA==',files:{'package/dist/mpegts.js':'mpegts.min.js','package/LICENSE':'mpegts.LICENSE'}},
  {name:'dashjs',version:'5.2.1',integrity:'axcJmLeJCTJMLUfQRErUJB1KOiU5iHF+DMLBc3FKeox1HPYtGUQlC6Xm5Mm6vhBUR3CHHLIPOqw0JDIqMtP+Dw==',files:{'package/dist/modern/umd/dash.all.min.js':'dash.all.min.js','package/LICENSE.md':'dash.LICENSE'}},
];
export async function prepareVendors(){
  const root=new URL('../public/assets/',import.meta.url),cache=new URL('../.vendor-cache/',import.meta.url);
  await mkdir(root,{recursive:true});await mkdir(cache,{recursive:true});
  for(const pkg of packages){
    const filename=new URL(`${pkg.name}-${pkg.version}.tgz`,cache);
    let archive;try{archive=await readFile(filename);}catch{}
    const valid=value=>value&&createHash('sha512').update(value).digest('base64')===pkg.integrity;
    if(!valid(archive)){
      const response=await fetch(`https://registry.npmjs.org/${pkg.name}/-/${pkg.name}-${pkg.version}.tgz`,{signal:AbortSignal.timeout(90000)});
      if(!response.ok)throw Error(`Could not download ${pkg.name}: ${response.status}`);
      archive=Buffer.from(await response.arrayBuffer());if(!valid(archive))throw Error(`Integrity mismatch: ${pkg.name}`);
      await writeFile(filename,archive);
    }
    const tar=gunzipSync(archive),found=new Set();
    for(let offset=0;offset+512<=tar.length;){
      const header=tar.subarray(offset,offset+512),name=header.subarray(0,100).toString().replace(/\0.*$/s,''),size=parseInt(header.subarray(124,136).toString().replace(/\0.*$/s,'').trim(),8)||0;
      if(!name)break;
      if(pkg.files[name]){await writeFile(new URL(pkg.files[name],root),tar.subarray(offset+512,offset+512+size));found.add(name);}
      offset+=512+Math.ceil(size/512)*512;
    }
    if(found.size!==Object.keys(pkg.files).length)throw Error(`Missing media engine files: ${pkg.name}`);
  }
}
