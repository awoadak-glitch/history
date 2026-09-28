package awr.witcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.CookieManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Resolves Oscar player links to verified HTTP media before opening MX Player. */
final class OscarResolver {
    private static final String UA="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final int BODY_LIMIT=2*1024*1024;
    private static final int MAX_DEPTH=4;
    private static final int MAX_CANDIDATES=12;
    private static final String TDM_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String[] TDM_DIRECT_HOSTS={"tmfiles.net","seriesmp4.com","traidmod.cloud","tmdown.net","tmdownload.com"};
    private static final String[] TDM_DOWNLOAD_HOSTS={"yastatic.net","ya.ru","disk.yandex","downloader.disk.yandex","storage.yandexcloud","publicdisk.yandex.ru","dl.disk.yandex.ru","yandex.ru","yandex.net"};
    private static final String[] LINK_KEYS={"url","link","video","stream","src","source","file","media","play","playlist","hls","mp4","uri","data","browser_fallback_url"};
    private static final Pattern HTTP=Pattern.compile("https?:(?:\\\\/|/){2}[^\\s\\\"'<>]+",Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION=Pattern.compile("(?:window\\.)?location(?:\\.href)?\\s*=\\s*([\\\"'])(.*?)\\1",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private OscarResolver() {}

    static final class Stream {
        final String url,label,mime;
        final Map<String,String> headers;
        Stream(String url,String label,String mime,Map<String,String> headers){
            this.url=url;this.label=label;this.mime=mime;this.headers=copy(headers);
        }
        boolean segmented(){return isHls(mime,url)||"application/dash+xml".equals(mime);}
    }

    private static final class Candidate {
        final String url,type,label;
        final Map<String,String> headers;
        Candidate(String url,String type,Map<String,String> headers){this(url,type,"",headers);}
        Candidate(String url,String type,String label,Map<String,String> headers){this.url=url;this.type=type;this.label=label==null?"":label;this.headers=copy(headers);}
    }

    static void open(Activity activity,String raw,String deep,String type,String title) {
        if(activity.isFinishing()||activity.isDestroyed())return;
        ProgressDialog wait=new ProgressDialog(activity);
        wait.setMessage("جاري تجهيز رابط السيرفر لـ MX…");
        wait.setCancelable(true);
        wait.show();
        AtomicBoolean finished=new AtomicBoolean();
        Handler main=new Handler(Looper.getMainLooper());
        Runnable timeout=()->{
            if(!finished.compareAndSet(false,true))return;
            wait.dismiss();
            alert(activity,"انتهت مهلة تجهيز السيرفر. جرّب سيرفراً آخر.");
        };
        main.postDelayed(timeout,70000);
        wait.setOnCancelListener(dialog->finished.set(true));
        Api.IO.execute(()->{
            List<Stream> streams=Collections.emptyList();String problem=null;
            try{streams=resolve(raw,deep,type);}
            catch(IOException error){problem=message(error);}
            catch(RuntimeException error){problem="تعذر تحليل استجابة السيرفر.";}
            final List<Stream> ready=streams;final String issue=problem;
            main.post(()->{
                if(!finished.compareAndSet(false,true))return;
                main.removeCallbacks(timeout);wait.dismiss();
                if(activity.isFinishing()||activity.isDestroyed())return;
                if(ready.isEmpty()){
                    // Some Oscar/TDM links create their final HLS/MP4 request only after the
                    // provider page runs JavaScript. Observe that request inside this process,
                    // retain its cookies/referer, and only then hand it to MX.
                    OscarBrowserResolver.open(activity,raw,deep,type,title,issue);
                    return;
                }
                android.content.DialogInterface.OnClickListener select=(dialog,index)->{
                    Stream stream=ready.get(index);
                    Media.launchResolved(activity,stream.url,stream.headers,title,stream.segmented());
                };
                if(ready.size()==1)select.onClick(null,0);
                else{
                    String[] labels=new String[ready.size()];
                    for(int i=0;i<labels.length;i++)labels[i]=ready.get(i).label;
                    new AlertDialog.Builder(activity).setTitle("اختر جودة التشغيل").setItems(labels,select).setNegativeButton("إلغاء",null).show();
                }
            });
        });
    }

    /** Package-visible for deterministic local-server regression tests. */
    static List<Stream> resolve(String raw,String deep,String type)throws IOException {
        List<Candidate> candidates=candidates(raw,deep,type);IOException last=null;
        for(Candidate candidate:candidates){
            for(Map<String,String> headers:variants(candidate)){
                try{
                    List<Stream> streams=probe(candidate.url,candidate.type,headers,0,new HashSet<>());
                    streams=unique(streams);
                    if(!streams.isEmpty())return streams;
                }catch(IOException error){last=error;}
            }
            // TDM's working path deliberately accepts its known redirect/download hosts
            // even when they answer as application/octet-stream without a media suffix.
            // Keep this after the stricter probe, so ordinary links still receive full
            // MIME/HLS validation first.
            try{
                Stream tdm=resolveLikeTdm(candidate);
                if(tdm!=null)return Collections.singletonList(tdm);
            }catch(IOException error){last=error;}
        }
        if(last!=null)throw last;
        return Collections.emptyList();
    }

    private static Stream resolveLikeTdm(Candidate candidate)throws IOException {
        if(candidate==null||!http(candidate.url))return null;
        if(hostContains(candidate.url,TDM_DIRECT_HOSTS))return tdmStream(candidate.url,candidate,candidate.headers);
        HttpURLConnection connection=(HttpURLConnection)new URL(candidate.url).openConnection();
        connection.setConnectTimeout(30000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");connection.setRequestProperty("User-Agent",TDM_UA);
        try{
            int status=connection.getResponseCode();Map<String,String> headers=copy(candidate.headers);mergeCookies(headers,connection);
            String location=connection.getHeaderField("Location");
            if(location!=null&&!location.trim().isEmpty()){
                String target=new URL(new URL(candidate.url),location.trim()).toString();
                if(tdmDownload(target))return tdmStream(target,candidate,headers);
            }
            if(status<200||status>=400)return null;
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){
                int total=0;String line;
                while((line=reader.readLine())!=null){
                    total+=line.length();if(total>BODY_LIMIT)break;
                    Matcher links=HTTP.matcher(line);
                    while(links.find()){
                        String target=PageStreams.entities(PageStreams.jsUnescape(links.group())).replace("\\/","/").replace("&amp;","&");
                        if(tdmDownload(target))return tdmStream(target,candidate,headers);
                    }
                }
            }
            return null;
        }finally{connection.disconnect();}
    }

    private static Stream tdmStream(String url,Candidate candidate,Map<String,String> headers){
        return new Stream(url,label(candidate.type),mime(candidate.type,url),headers);
    }

    private static boolean tdmDownload(String url){
        if(!http(url))return false;
        String low=url.toLowerCase(Locale.ROOT);
        return hostContains(url,TDM_DOWNLOAD_HOSTS)||low.contains("disposition=attachment")||low.contains("filename=");
    }

    private static boolean hostContains(String url,String[] domains){
        try{
            String host=new URL(url).getHost().toLowerCase(Locale.ROOT);
            for(String domain:domains)if(host.equals(domain)||host.endsWith("."+domain)||host.contains(domain))return true;
        }catch(Exception ignored){}
        return false;
    }

    private static List<Candidate> candidates(String raw,String deep,String type) {
        List<Candidate> out=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();
        Map<String,String> base=baseHeaders();
        readHeaderHints(deep,base);readHeaderHints(raw,base);
        // This matches Oscar's original click path: deep_link first, raw URL only as fallback.
        collect(deep,type,base,null,out,seen,0);
        collect(raw,type,base,null,out,seen,0);
        return out;
    }

    private static void collect(String value,String type,Map<String,String> inherited,String parent,List<Candidate> out,Set<String> seen,int depth) {
        if(value==null||depth>4||out.size()>=MAX_CANDIDATES)return;
        String clean=value.trim();if(clean.isEmpty())return;
        List<String> forms=decodedForms(clean);
        for(String form:forms){
            Map<String,String> headers=copy(inherited);readHeaderHints(form,headers);
            for(Map.Entry<String,List<String>> entry:query(form).entrySet())if(linkKey(entry.getKey()))
                for(String nested:entry.getValue())collect(nested,type,headers,http(form)?form:parent,out,seen,depth+1);
            if(form.startsWith("intent:")){
                for(String part:form.split(";")){
                    int equals=part.indexOf('=');if(equals<0)continue;
                    String key=part.substring(0,equals).replace("S.","");
                    if(linkKey(key))collect(decode(part.substring(equals+1),false),type,headers,parent,out,seen,depth+1);
                }
            }
            Matcher embedded=HTTP.matcher(form);
            while(embedded.find()){
                String nested=PageStreams.jsUnescape(embedded.group());
                if(!nested.equals(form))collect(nested,type,headers,parent,out,seen,depth+1);
            }
            if(http(form)){
                if(parent!=null&&!has(headers,"Referer")){put(headers,"Referer",parent);putOrigin(headers,parent);}
                if(seen.add(form))out.add(new Candidate(form,type,headers));
            }
        }
    }

    private static List<String> decodedForms(String value) {
        LinkedHashSet<String> values=new LinkedHashSet<>();String current=value;
        for(int i=0;i<3;i++){
            if(current==null||current.isEmpty()||!values.add(current))break;
            String decoded=decode(current,false).trim();if(decoded.equals(current))break;current=decoded;
        }
        try{values.add(StreamCodec.forExternalPlayer(value));}catch(RuntimeException ignored){}
        if(value.length()>=16&&value.matches("[A-Za-z0-9_+/=\\-]+")){
            for(int flags:new int[]{Base64.DEFAULT,Base64.URL_SAFE|Base64.NO_WRAP})try{
                String decoded=new String(Base64.decode(value,flags),StandardCharsets.UTF_8).trim();
                if(decoded.contains("http")||decoded.contains("://"))values.add(decoded);
            }catch(IllegalArgumentException ignored){}
        }
        return new ArrayList<>(values);
    }

    private static boolean linkKey(String key){
        if(key==null)return false;String low=key.toLowerCase(Locale.ROOT).replace("s.","");
        for(String item:LINK_KEYS)if(item.equals(low))return true;
        return low.equals("play_url")||low.equals("playurl")||low.equals("stream_url")||low.equals("streamurl")
                ||low.equals("video_url")||low.equals("videourl")||low.equals("file_url")||low.equals("fileurl");
    }

    private static Map<String,String> baseHeaders(){
        Map<String,String> out=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("User-Agent",UA);out.put("Accept","*/*");out.put("Accept-Language","ar,en-US;q=0.8,en;q=0.6");
        out.put("X-Requested-With","com.drama.mp4");return out;
    }

    private static void readHeaderHints(String value,Map<String,String> headers) {
        if(value==null||value.isEmpty())return;
        try{
            for(Map.Entry<String,List<String>> entry:query(value).entrySet()){
                String name=entry.getKey();String low=name.toLowerCase(Locale.ROOT).replace('_','-');String v=entry.getValue().isEmpty()?"":entry.getValue().get(0);
                if(low.equals("referer")||low.equals("referrer"))put(headers,"Referer",v);
                else if(low.equals("origin"))put(headers,"Origin",v);
                else if(low.equals("cookie"))put(headers,"Cookie",v);
                else if(low.equals("user-agent")||low.equals("ua"))put(headers,"User-Agent",v);
                else if(low.equals("headers")||low.equals("http-headers"))readHeaderBlock(v,headers);
            }
        }catch(RuntimeException ignored){}
        String referer=get(headers,"Referer");if(referer!=null&&!has(headers,"Origin"))putOrigin(headers,referer);
    }

    private static void readHeaderBlock(String value,Map<String,String> headers) {
        if(value==null)return;String clean=decode(value,false).trim();
        for(String form:decodedForms(clean)){
            try{
                Object json=new JSONTokener(form).nextValue();
                if(json instanceof JSONObject){JSONObject object=(JSONObject)json;java.util.Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();put(headers,key,object.optString(key));}}
            }catch(JSONException ignored){}
            for(String line:form.split("[\\r\\n|]+")){
                int colon=line.indexOf(':');if(colon>0)put(headers,line.substring(0,colon).trim(),line.substring(colon+1).trim());
            }
        }
    }

    private static List<Map<String,String>> variants(Candidate candidate) {
        List<Map<String,String>> seeds=new ArrayList<>();seeds.add(copy(candidate.headers));
        if(!has(candidate.headers,"Referer")){
            Map<String,String> oscar=copy(candidate.headers);put(oscar,"Referer","https://ostvapp.cam/");put(oscar,"Origin","https://ostvapp.cam");seeds.add(oscar);
            String root=origin(candidate.url);
            if(!root.isEmpty()){Map<String,String> self=copy(candidate.headers);put(self,"Referer",root+"/");put(self,"Origin",root);seeds.add(self);}
        }
        List<Map<String,String>> values=new ArrayList<>();Set<String> signatures=new HashSet<>();
        for(Map<String,String> seed:seeds){addVariant(values,signatures,seed);Map<String,String> okhttp=copy(seed);put(okhttp,"User-Agent","okhttp/4.12.0");addVariant(values,signatures,okhttp);}
        return values;
    }

    private static void addVariant(List<Map<String,String>> out,Set<String> seen,Map<String,String> headers){
        Map<String,String> clean=copy(headers);addWebCookie(clean,get(clean,"Referer"));
        String signature=clean.toString();if(seen.add(signature))out.add(clean);
    }

    private static List<Stream> probe(String url,String declared,Map<String,String> headers,int depth,Set<String> visited)throws IOException {
        if(depth>MAX_DEPTH||!http(url)||!visited.add(url))return Collections.emptyList();
        Map<String,String> request=copy(headers);addWebCookie(request,url);
        boolean mediaHint=mediaExtension(url)||mediaType(declared);
        if(mediaHint){
            try{
                List<Stream> head=request(url,declared,request,depth,new HashSet<>(visited),true);
                if(head!=null&&!head.isEmpty())return head;
            }catch(HttpStatus error){if(error.code!=403&&error.code!=405&&error.code!=501)throw error;}
        }
        List<Stream> get=request(url,declared,request,depth,visited,false);
        return get==null?Collections.emptyList():get;
    }

    /** A null HEAD result means the server was reachable but GET is needed to inspect its body. */
    private static List<Stream> request(String url,String declared,Map<String,String> headers,int depth,Set<String> visited,boolean head)throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(25000);connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(head?"HEAD":"GET");
        for(Map.Entry<String,String> entry:headers.entrySet())if(validHeader(entry.getKey(),entry.getValue()))connection.setRequestProperty(entry.getKey(),entry.getValue());
        try{
            int status=connection.getResponseCode();Map<String,String> responseHeaders=copy(headers);mergeCookies(responseHeaders,connection);
            if(status>=300&&status<400){
                String location=connection.getHeaderField("Location");if(location==null||location.trim().isEmpty())throw new IOException("أعاد السيرفر تحويلاً بلا رابط.");
                String next=new URL(new URL(url),location).toString();
                if(!sameOrigin(url,next)){remove(responseHeaders,"Cookie");remove(responseHeaders,"Authorization");addWebCookie(responseHeaders,next);}
                return probe(next,declared,responseHeaders,depth+1,visited);
            }
            if(status<200||status>=300)throw new HttpStatus(status);
            String mime=mime(connection.getContentType());String finalUrl=connection.getURL().toString();
            if(directMime(mime)||(mime.equals("application/octet-stream")&&(mediaExtension(finalUrl)||mediaType(declared))))
                return Collections.singletonList(new Stream(finalUrl,label(declared),mime.isEmpty()?mime(declared,finalUrl):mime,responseHeaders));
            if(head){
                if(isHls(mime,finalUrl)||isDash(mime,finalUrl))return Collections.singletonList(new Stream(finalUrl,"تلقائي",mime(declared,finalUrl),responseHeaders));
                return null;
            }
            long length=connection.getContentLengthLong();
            if(length>BODY_LIMIT&&mediaType(declared)&&(mime.isEmpty()||mime.equals("application/octet-stream")))return Collections.singletonList(new Stream(finalUrl,label(declared),mime(declared,finalUrl),responseHeaders));
            String body=readBody(connection,BODY_LIMIT).trim();if(body.startsWith("\ufeff"))body=body.substring(1).trim();
            if(body.startsWith("#EXTM3U"))return hls(finalUrl,body,responseHeaders);
            if(isDash(mime,finalUrl)&&body.startsWith("<"))return Collections.singletonList(new Stream(finalUrl,"تلقائي • DASH","application/dash+xml",responseHeaders));
            List<Candidate> children=pageCandidates(body,finalUrl,declared,responseHeaders);
            List<Stream> ready=new ArrayList<>();
            for(Candidate child:children){
                if(ready.size()>=12)break;
                try{
                    List<Stream> found=probe(child.url,child.type,child.headers,depth+1,visited);
                    for(Stream stream:found){
                        String childLabel=child.label.isEmpty()?stream.label:child.label;
                        ready.add(new Stream(stream.url,childLabel,stream.mime,stream.headers));
                    }
                }catch(IOException ignored){}
            }
            return ready;
        }finally{connection.disconnect();}
    }

    private static List<Candidate> pageCandidates(String body,String page,String declared,Map<String,String> headers) {
        List<Candidate> out=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();
        Map<String,String> childHeaders=copy(headers);put(childHeaders,"Referer",page);putOrigin(childHeaders,page);
        PageStreams.Result parsed=PageStreams.parse(body,page);
        for(Legacy.Stream stream:parsed.streams){
            Map<String,String> h=childHeaders(stream.url,page,childHeaders);if(stream.cookie!=null)put(h,"Cookie",stream.cookie);
            if(seen.add(stream.url))out.add(new Candidate(stream.url,declared,stream.quality,h));
        }
        Matcher location=LOCATION.matcher(body);while(location.find())addPageCandidate(out,seen,location.group(2),page,declared,childHeaders);
        Matcher refresh=Pattern.compile("<meta[^>]+http-equiv\\s*=\\s*([\\\"'])?refresh\\1?[^>]+content\\s*=\\s*([\\\"'])(.*?)\\2",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(body);
        while(refresh.find()){Matcher url=Pattern.compile("url\\s*=\\s*(.+)",Pattern.CASE_INSENSITIVE).matcher(refresh.group(3));if(url.find())addPageCandidate(out,seen,url.group(1).trim(),page,declared,childHeaders);}
        collectJson(body,page,declared,childHeaders,out,seen);
        return out;
    }

    private static void addPageCandidate(List<Candidate> out,Set<String> seen,String value,String page,String label,Map<String,String> headers) {
        if(value==null||value.isEmpty()||out.size()>=MAX_CANDIDATES)return;
        try{
            String clean=PageStreams.entities(PageStreams.jsUnescape(value)).replace("\\/","/");
            String url=new URL(new URL(page),clean).toString();StreamCodec.validate(url);
            if(seen.add(url))out.add(new Candidate(url,label,"",childHeaders(url,page,headers)));
        }catch(Exception ignored){}
    }

    private static void collectJson(String body,String page,String declared,Map<String,String> headers,List<Candidate> out,Set<String> seen) {
        String clean=body.trim();if(!(clean.startsWith("{")||clean.startsWith("[")))return;
        try{collectJsonValue(new JSONTokener(clean).nextValue(),page,declared,headers,out,seen,0);}catch(JSONException ignored){}
    }

    private static void collectJsonValue(Object value,String page,String declared,Map<String,String> headers,List<Candidate> out,Set<String> seen,int depth) {
        if(value==null||depth>5||out.size()>=MAX_CANDIDATES)return;
        if(value instanceof JSONObject){
            JSONObject object=(JSONObject)value;java.util.Iterator<String> keys=object.keys();
            while(keys.hasNext()){String key=keys.next();Object child=object.opt(key);if(child instanceof String&&linkKey(key))addPageCandidate(out,seen,(String)child,page,declared,headers);else collectJsonValue(child,page,declared,headers,out,seen,depth+1);}
        }else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)collectJsonValue(array.opt(i),page,declared,headers,out,seen,depth+1);}
        else if(value instanceof String&&((String)value).contains("http"))addPageCandidate(out,seen,(String)value,page,declared,headers);
    }

    private static Map<String,String> childHeaders(String child,String parent,Map<String,String> headers) {
        Map<String,String> out=copy(headers);
        if(!sameOriginQuiet(parent,child)){remove(out,"Cookie");remove(out,"Authorization");addWebCookie(out,child);}
        put(out,"Referer",parent);putOrigin(out,parent);return out;
    }

    private static List<Stream> hls(String base,String body,Map<String,String> headers)throws IOException {
        List<Stream> out=new ArrayList<>();Set<String> seen=new HashSet<>();String pending=null;
        for(String raw:body.split("\\r?\\n")){
            String line=raw.trim();
            if(line.startsWith("#EXT-X-STREAM-INF:")){
                Matcher resolution=Pattern.compile("RESOLUTION=\\d+x(\\d+)",Pattern.CASE_INSENSITIVE).matcher(line);
                Matcher bandwidth=Pattern.compile("BANDWIDTH=(\\d+)",Pattern.CASE_INSENSITIVE).matcher(line);
                pending=resolution.find()?resolution.group(1)+"p":bandwidth.find()?(Long.parseLong(bandwidth.group(1))/1000)+" kb/s":"جودة إضافية";
            }else if(pending!=null&&!line.isEmpty()&&!line.startsWith("#")){
                String url=new URL(new URL(base),line).toString();if(http(url)&&seen.add(url))out.add(new Stream(url,pending,"application/x-mpegURL",headers));pending=null;
            }
        }
        if(out.isEmpty())out.add(new Stream(base,"تلقائي","application/x-mpegURL",headers));
        else out.add(0,new Stream(base,"تلقائي • جميع الجودات","application/x-mpegURL",headers));
        return out;
    }

    private static String readBody(HttpURLConnection connection,int limit)throws IOException {
        InputStream raw=connection.getInputStream();String encoding=connection.getHeaderField("Content-Encoding");
        InputStream input="gzip".equalsIgnoreCase(encoding)?new GZIPInputStream(raw):raw;
        try(InputStream in=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] block=new byte[16384];int count;
            while((count=in.read(block))!=-1){bytes.write(block,0,count);if(bytes.size()>limit)throw new IOException("استجابة السيرفر أكبر من الحد الآمن للتحليل.");}
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
    }

    private static void mergeCookies(Map<String,String> headers,HttpURLConnection connection) {
        Map<String,String> jar=new LinkedHashMap<>();parseCookies(get(headers,"Cookie"),jar);
        Map<String,List<String>> fields=connection.getHeaderFields();if(fields!=null)for(Map.Entry<String,List<String>> field:fields.entrySet())if(field.getKey()!=null&&field.getKey().equalsIgnoreCase("Set-Cookie"))for(String value:field.getValue())parseCookies(value,jar);
        if(!jar.isEmpty()){StringBuilder value=new StringBuilder();for(Map.Entry<String,String> cookie:jar.entrySet()){if(value.length()>0)value.append("; ");value.append(cookie.getKey()).append('=').append(cookie.getValue());}put(headers,"Cookie",value.toString());}
    }

    private static void parseCookies(String value,Map<String,String> out) {
        if(value==null)return;for(String item:value.split(";")){String part=item.trim();int equals=part.indexOf('=');if(equals<=0)continue;String name=part.substring(0,equals).trim();String low=name.toLowerCase(Locale.ROOT);if(low.equals("path")||low.equals("domain")||low.equals("expires")||low.equals("max-age")||low.equals("samesite")||low.equals("secure")||low.equals("httponly"))continue;out.put(name,part.substring(equals+1).trim());}
    }

    private static void addWebCookie(Map<String,String> headers,String url) {
        if(url==null||!http(url))return;
        try{String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null&&!cookie.isEmpty()){Map<String,String> jar=new LinkedHashMap<>();parseCookies(get(headers,"Cookie"),jar);parseCookies(cookie,jar);StringBuilder value=new StringBuilder();for(Map.Entry<String,String> entry:jar.entrySet()){if(value.length()>0)value.append("; ");value.append(entry.getKey()).append('=').append(entry.getValue());}put(headers,"Cookie",value.toString());}}
        catch(RuntimeException|LinkageError ignored){}
    }

    private static List<Stream> unique(List<Stream> streams) {
        List<Stream> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Stream stream:streams)if(stream!=null&&http(stream.url)&&seen.add(stream.url))out.add(stream);
        return out;
    }

    private static boolean http(String value){
        try{URI uri=new URI(value);String scheme=uri.getScheme();return scheme!=null&&(scheme.equalsIgnoreCase("http")||scheme.equalsIgnoreCase("https"))&&uri.getHost()!=null&&uri.getUserInfo()==null;}
        catch(Exception ignored){return false;}
    }
    private static Map<String,List<String>> query(String value){
        Map<String,List<String>> out=new LinkedHashMap<>();if(value==null)return out;
        int question=value.indexOf('?');if(question<0)return out;int fragment=value.indexOf('#',question+1);
        String raw=value.substring(question+1,fragment<0?value.length():fragment);
        for(String part:raw.split("&")){if(part.isEmpty())continue;int equals=part.indexOf('=');String key=decode(equals<0?part:part.substring(0,equals),true);String item=decode(equals<0?"":part.substring(equals+1),true);out.computeIfAbsent(key,k->new ArrayList<>()).add(item);}
        return out;
    }
    private static String decode(String value,boolean plusAsSpace){
        if(value==null)return "";
        try{return URLDecoder.decode(plusAsSpace?value:value.replace("+","%2B"),"UTF-8");}
        catch(Exception ignored){return value;}
    }
    private static boolean mediaType(String type){String value=type==null?"":type.toLowerCase(Locale.ROOT);return value.equals("mp4")||value.equals("mkv")||value.equals("mov")||value.equals("webm")||value.equals("m3u8")||value.equals("mpd")||value.startsWith("video/");}
    private static boolean mediaExtension(String url){try{String path=new URL(url).getPath().toLowerCase(Locale.ROOT);return path.matches(".*\\.(mp4|mkv|mov|webm|m4v|avi|ts|m3u8|mpd)$");}catch(Exception ignored){return false;}}
    private static boolean directMime(String mime){return mime.startsWith("video/")&&!mime.contains("mpegurl")&&!mime.contains("m3u");}
    private static boolean isHls(String mime,String url){String value=mime==null?"":mime.toLowerCase(Locale.ROOT);return value.contains("mpegurl")||pathEnds(url,".m3u8");}
    private static boolean isDash(String mime,String url){return "application/dash+xml".equals(mime)||pathEnds(url,".mpd");}
    private static boolean pathEnds(String url,String suffix){try{return new URL(url).getPath().toLowerCase(Locale.ROOT).endsWith(suffix);}catch(Exception ignored){return false;}}
    private static String mime(String declared,String url){if(isHls(declared,url)||"m3u8".equalsIgnoreCase(declared))return "application/x-mpegURL";if(isDash(declared,url)||"mpd".equalsIgnoreCase(declared))return "application/dash+xml";return "video/*";}
    private static String mime(String value){if(value==null)return "";int semicolon=value.indexOf(';');return (semicolon<0?value:value.substring(0,semicolon)).trim().toLowerCase(Locale.ROOT);}
    private static String label(String declared){if(declared==null||declared.trim().isEmpty())return "الجودة الأصلية";String value=declared.trim();return value.matches("\\d+")?value+"p":value.toUpperCase(Locale.ROOT);}

    private static String origin(String url){try{URL value=new URL(url);int port=value.getPort();return value.getProtocol()+"://"+value.getHost()+(port<0?"":":"+port);}catch(Exception ignored){return "";}}
    private static void putOrigin(Map<String,String> headers,String referer){String value=origin(referer);if(!value.isEmpty())put(headers,"Origin",value);}
    private static boolean sameOriginQuiet(String first,String second){try{return sameOrigin(first,second);}catch(IOException ignored){return false;}}
    private static boolean sameOrigin(String first,String second)throws IOException {
        try{URI a=new URI(first),b=new URI(second);return equals(a.getScheme(),b.getScheme())&&equals(a.getHost(),b.getHost())&&port(a)==port(b);}
        catch(URISyntaxException error){throw new IOException(error);}
    }
    private static int port(URI uri){if(uri.getPort()>=0)return uri.getPort();return "https".equalsIgnoreCase(uri.getScheme())?443:80;}
    private static boolean equals(String a,String b){return a==null?b==null:a.equalsIgnoreCase(b);}

    private static Map<String,String> copy(Map<String,String> value){Map<String,String> out=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);if(value!=null)out.putAll(value);return out;}
    private static boolean has(Map<String,String> headers,String key){return get(headers,key)!=null;}
    private static String get(Map<String,String> headers,String key){for(Map.Entry<String,String> entry:headers.entrySet())if(entry.getKey().equalsIgnoreCase(key))return entry.getValue();return null;}
    private static void remove(Map<String,String> headers,String key){String found=null;for(String name:headers.keySet())if(name.equalsIgnoreCase(key)){found=name;break;}if(found!=null)headers.remove(found);}
    private static void put(Map<String,String> headers,String key,String value){if(validHeader(key,value))headers.put(key.trim(),value.trim());}
    private static boolean validHeader(String key,String value){return key!=null&&key.trim().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")&&value!=null&&!value.trim().isEmpty()&&!value.contains("\r")&&!value.contains("\n");}
    private static String message(IOException error){if(error instanceof HttpStatus){int code=((HttpStatus)error).code;return code==403?"رفض السيرفر تجهيز رابط المشاهدة (403). جرّب سيرفراً آخر.":"تعذر تجهيز رابط السيرفر (HTTP "+code+").";}String value=error.getMessage();return value==null||value.trim().isEmpty()?"تعذر الاتصال بسيرفر المشاهدة.":value;}
    private static void alert(Activity activity,String message){if(!activity.isFinishing()&&!activity.isDestroyed())new AlertDialog.Builder(activity).setMessage(message).setPositiveButton("حسناً",null).show();}
    private static final class HttpStatus extends IOException {final int code;HttpStatus(int code){super("HTTP "+code);this.code=code;}}
}
