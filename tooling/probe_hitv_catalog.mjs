#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
const BASE='https://web-api.hitvpro.com';
const PUBLIC_KEY_DER='MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCEZTSwZTZdBfQAm8oVroRdy6K7VVB0/7Jojx7e3UPa7NjjZ2A/mydH7QI9aHr3dNEJDrNLyZ/nEK6XzV+XTG3t3GVqsEjVOVO26K/VcDVwk/dFJs8bmK3AaIBPQATJQDztgSjg5H7u92JnyXvtj0XB0IH+GM2ui6sB69Y5T0LPAwIDAQAB';
const KEY=`-----BEGIN PUBLIC KEY-----\n${PUBLIC_KEY_DER.match(/.{1,64}/g).join('\n')}\n-----END PUBLIC KEY-----`;
function flatten(params,nested=false){const values=[];for(const k of Object.keys(params)){const v=params[k];if(v==null)continue;if(Array.isArray(v)){v.forEach((e,i)=>values.push(e&&typeof e==='object'?`${k}=${flatten(e,true)}`:`${k}[${i}]=${String(e)}`));}else if(typeof v==='object')values.push(`${k}=${flatten(v,true)}`);else values.push(`${k}=${String(v)}`);}if(nested)values.sort((a,b)=>a<b?1:a>b?-1:0);return Buffer.from(values.map(v=>v.slice(v.indexOf('=')+1)).join(''),'utf8').toString('base64');}
function makeHeaders(params,uuid){const currentTime=Date.now().toString();const payload=`${currentTime}${flatten(params,true)}`.replaceAll('+','-').replaceAll('/','_');const c=crypto.createCipheriv('aes-128-ecb',Buffer.from(uuid),null);const encrypted=Buffer.concat([c.update(payload,'utf8'),c.final()]).toString('base64');return {'Content-Type':'application/json','Accept':'application/json',lang:'ar',currentTime,sign:crypto.createHash('md5').update(encrypted).digest('hex'),aesKey:crypto.publicEncrypt({key:KEY,padding:crypto.constants.RSA_PKCS1_PADDING},Buffer.from(uuid)).toString('base64'),'User-Agent':'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36'};}
const routes=[
 ['home-first','GET','/cms/web/hitv/homePage/firstPage',{}],
 ['home-albums','GET','/cms/web/hitv/homePage/album/page',{sequence:0,type:0}],
 ['movies','GET','/cms/web/hitv/movieDrama/moviePage',{}],
 ['dramas','GET','/cms/web/hitv/movieDrama/dramaPage',{}],
 ['leaderboard','GET','/cms/web/hitv/search/leaderboard',{}],
 ['single-albums','GET','/cms/web/pc/homePage/singleAlbums',{type:0}],
 ['banners','GET','/cms/web/pc/homePage/banners',{type:0}],
 ['pc-leaderboard','GET','/cms/web/pc/search/searchLeaderboard',{type:0}],
 ['keyword-search','POST','/cms/web/hitv/movieDrama/searchWithKeyWord',{size:5,searchKeyWord:'love'}],
 ['filtered-search','POST','/cms/web/hitv/search/search',{page:0,size:5,searchKeyWord:'love'}],
 ['suggestions','POST','/cms/web/pc/search/searchLenovo',{size:5,searchKeyWord:'love'}]
];
async function call(name,method,path,params){const uuid=crypto.randomBytes(8).toString('hex');const u=new URL(path,BASE);if(method==='GET')for(const [k,v] of Object.entries(params))if(typeof v!=='object')u.searchParams.set(k,String(v));const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),15000);const start=Date.now();try{const r=await fetch(u,{method,headers:makeHeaders(params,uuid),body:method==='POST'?JSON.stringify(params):undefined,signal:controller.signal});const text=await r.text();let code='',hasData=false;try{const j=JSON.parse(text);code=String(j.code??'');hasData=j.data!=null;}catch{}return {name,method,path,http:r.status,code,has_data:hasData,bytes:Buffer.byteLength(text),elapsed_ms:Date.now()-start};}catch(e){return {name,method,path,http:null,error:e?.cause?.code||e?.name||'network',elapsed_ms:Date.now()-start};}finally{clearTimeout(timer);}}
const report={generated_at:new Date().toISOString(),routes:[]};for(const r of routes)report.routes.push(await call(...r));fs.mkdirSync('diagnostics',{recursive:true});fs.writeFileSync('diagnostics/hitv-catalog-probe.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
