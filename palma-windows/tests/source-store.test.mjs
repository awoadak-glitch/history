import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,readFile,readdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {createCipheriv,createDecipheriv,randomBytes} from 'node:crypto';
import {createSourceStore} from '../src/source-store.mjs';

test('device secrets persist encrypted, keep their origin, and fail closed on damaged storage',async()=>{
  const root=await mkdtemp(path.join(tmpdir(),'palma-v3-test-'));
  try {
    const key=randomBytes(32),safeStorage={isEncryptionAvailable:()=>true,encryptString:text=>{const iv=randomBytes(12),c=createCipheriv('aes-256-gcm',key,iv),data=Buffer.concat([c.update(text),c.final()]);return Buffer.concat([iv,c.getAuthTag(),data]);},decryptString:bytes=>{const c=createDecipheriv('aes-256-gcm',key,bytes.subarray(0,12));c.setAuthTag(bytes.subarray(12,28));return Buffer.concat([c.update(bytes.subarray(28)),c.final()]).toString();}};
    const store=createSourceStore({root,origin:'https://source.example',safeStorage});
    assert.equal(await store.load(),null);const value={deviceSecret:'synthetic-secret',deviceId:'one'};
    await store.save(value);const file=path.join(root,(await readdir(root))[0]);
    assert.equal((await readFile(file)).includes(Buffer.from('synthetic-secret')),false);
    assert.deepEqual(await createSourceStore({root,origin:'https://source.example',safeStorage}).load(),value);
    assert.equal(await createSourceStore({root,origin:'https://different.example',safeStorage}).load(),null);
    await writeFile(file,'damaged');await assert.rejects(store.load(),/لم تُحذف/);assert.equal(await readFile(file,'utf8'),'damaged');
    const unavailable=createSourceStore({root,origin:'https://source.example',safeStorage:{isEncryptionAvailable:()=>false}});
    await assert.rejects(unavailable.save(value),/غير متاحة/);
  }finally {await rm(root,{recursive:true,force:true});}
});
