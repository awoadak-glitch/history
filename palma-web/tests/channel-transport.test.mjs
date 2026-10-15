import test from 'node:test';
import assert from 'node:assert/strict';
import dns from 'node:dns/promises';
import http from 'node:http';
import https from 'node:https';
import {EventEmitter} from 'node:events';
import {PassThrough} from 'node:stream';
import {fetchPublic,publicURL} from '../server/http.js';
import {bindSourceSession} from '../server/source-session.js';

test('live gateway redirects to public port 8080, retaining UA but dropping the source cookie',async t=>{
 const origin='https://still-dust-c9ae.manon-lol2000.workers.dev';
 const destination='http://live.example:8080/channel/stream.ts';
 const requests=[];
 t.mock.method(dns,'lookup',async()=>[{address:'93.184.216.34',family:4}]);
 const respond=(statusCode,headers,body)=>(url,options,callback)=>{
  requests.push({url:url.href,headers:options.headers});
  const request=new EventEmitter();request.setTimeout=()=>request;
  request.end=()=>{const response=new PassThrough();Object.assign(response,{statusCode,headers});callback(response);response.end(body);};
  return request;
 };
 t.mock.method(https,'request',respond(302,{location:destination},''));
 t.mock.method(http,'request',respond(200,{'content-type':'video/mp2t'},Buffer.from([0x47,0,0,0])));
 const headers=bindSourceSession({'User-Agent':'OSCARTV2021'},origin,'ot=synthetic-source-session');
 const result=await fetchPublic(origin+'/channel.css',{headers});
 assert.equal(result.url,destination);assert.equal(result.response.status,200);
 assert.equal((await result.response.arrayBuffer()).byteLength,4);
 assert.equal(requests.length,2);assert.equal(requests[0].headers.Cookie,'ot=synthetic-source-session');
 assert.equal(requests[1].headers.Cookie,undefined);assert.equal(requests[1].headers['User-Agent'],'OSCARTV2021');
 assert.equal(requests[1].headers['Accept-Encoding'],'identity');
});

test('channel ports still reject private DNS, credentials and unrelated service ports',async t=>{
 for(const host of ['127.0.0.1','169.254.169.254','10.0.0.1','[::1]'])await assert.rejects(publicURL(`http://${host}:8080/live.ts`),{code:'INVALID_URL'});
 await assert.rejects(publicURL('http://user:password@live.example:8080/live.ts'),{code:'INVALID_URL'});
 await assert.rejects(publicURL('http://live.example:22/live.ts'),{code:'INVALID_URL'});
 t.mock.method(dns,'lookup',async()=>[{address:'192.168.1.1',family:4}]);
 await assert.rejects(publicURL('http://live.example:8080/live.ts'),{code:'INVALID_URL'});
});
