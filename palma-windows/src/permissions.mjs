export function allowAppPermission(permission,url,isMainFrame=true){
  if(!['fullscreen'].includes(permission)||isMainFrame===false)return false;
  try{const parsed=new URL(url);return parsed.protocol==='palma:'&&parsed.host==='app';}catch{return false;}
}
