import {mkdir,readFile,writeFile,rename,lstat} from 'node:fs/promises';
import {createHash,randomBytes} from 'node:crypto';
import path from 'node:path';

export function createSourceStore({root,origin,safeStorage}) {
  const file=path.join(root,createHash('sha256').update(origin).digest('hex')+'.bin');
  let writes=Promise.resolve();
  const directory=async()=>{
    await mkdir(root,{recursive:true,mode:0o700});
    if(!(await lstat(root)).isDirectory())throw Error('مجلد جلسة المشاهدة غير صالح.');
    try {if(!(await lstat(file)).isFile())throw Error('ملف جلسة المشاهدة غير صالح.');}catch(error){if(error.code!=='ENOENT')throw error;}
    if(!safeStorage.isEncryptionAvailable())throw Error('حماية Windows لبيانات المشاهدة غير متاحة.');
  };
  return {
    async load() {
      await writes;await directory();
      let bytes;try {bytes=await readFile(file);}catch(error){if(error.code==='ENOENT')return null;throw error;}
      try {return JSON.parse(safeStorage.decryptString(bytes));}
      catch {throw Error('تعذر فتح جلسة المشاهدة المحمية. لم تُحذف بيانات الجهاز.');}
    },
    save(value) {
      const serialized=JSON.stringify(value);
      const task=writes.then(async()=>{
        await directory();
        const bytes=safeStorage.encryptString(serialized);
        const temp=file+'.'+randomBytes(8).toString('hex')+'.tmp';
        await writeFile(temp,bytes,{mode:0o600,flag:'wx'});await rename(temp,file);
      });
      writes=task.catch(()=>{});return task;
    },
  };
}
