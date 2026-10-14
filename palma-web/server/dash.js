import {mediaURL,SourceError} from './http.js';
const decode=s=>s.replaceAll('&amp;','&').replaceAll('&quot;','"').replaceAll('&apos;',"'").replaceAll('&lt;','<').replaceAll('&gt;','>');
const encode=s=>s.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;');

export function dashTemplate(raw,base,headers={}) {
  const url=new URL(decode(raw),base).href;
  const variables=[...new Set(url.match(/\$(?:RepresentationID|Bandwidth|Number(?:%0\d+d)?|Time|SubNumber)\$/g)||[])];
  return mediaURL(url,headers,{variables})+variables.map((token,i)=>`&v${i}=${token}`).join('');
}
export function expandDashTicket(payload,params) {
  let url=payload.u;
  for(const [index,token] of (payload.v||[]).entries()) {
    const value=params.get('v'+index);
    if(!value||value.length>160||!/^[-\w.]+$/.test(value)||value==='..')throw new SourceError('جزء فيديو غير صالح.',400);
    url=url.replaceAll(token,value);
  }
  if(new URL(url).origin!==new URL(payload.u).origin)throw new SourceError('جزء فيديو غير صالح.',400);
  return url;
}
export function rewriteDASH(xml,base,headers={}) {
  if(/<!DOCTYPE|<!ENTITY/i.test(xml))throw new SourceError('قائمة DASH غير صالحة.',422);
  const root={children:[]},stack=[root];
  for(const token of xml.match(/<!--[\s\S]*?-->|<\?[\s\S]*?\?>|<\/?[\w:][^>]*>|[^<]+/g)||[]) {
    if(token.startsWith('</')) { if(stack.length<2)throw new SourceError('قائمة DASH غير صالحة.',422);stack.pop().close=token; }
    else if(/^<[\w:]/.test(token)) { const node={open:token,tag:/^<([\w:]+)/.exec(token)[1].split(':').pop(),children:[]};stack.at(-1).children.push(node);if(!token.endsWith('/>'))stack.push(node); }
    else stack.at(-1).children.push(token);
  }
  if(stack.length!==1)throw new SourceError('قائمة DASH غير مكتملة.',422);
  function render(node,parent) {
    if(typeof node==='string')return node;
    const local=node.children.find(x=>typeof x==='object'&&x.tag==='BaseURL');
    const current=local?new URL(decode(local.children.filter(x=>typeof x==='string').join('').trim()),parent).href:parent;
    let open=node.open||'';
    open=open.replace(/\b(media|initialization|sourceURL)=("[^"]*"|'[^']*')/g,(_m,key,value)=>`${key}="${encode(dashTemplate(value.slice(1,-1),current,headers))}"`);
    const contents=node.children.map(child=>typeof child==='object'&&child.tag==='BaseURL'?child.open+encode(dashTemplate(child.children.join('').trim(),parent,headers))+(child.close||''):render(child,current)).join('');
    return open+contents+(node.close||'');
  }
  return render(root,base);
}
