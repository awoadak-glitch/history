package awr.witcher;

import java.net.URL;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Reads media declarations from public player pages; never executes remote JavaScript. */
final class PageStreams {
    private PageStreams(){}
    static final class Result {
        final List<Legacy.Stream> streams=new ArrayList<>();boolean choice;
    }
    static Result parse(String html,String base){
        Result result=new Result();Set<String> seen=new HashSet<>();
        Matcher tags=Pattern.compile("<source\\b[^>]*>",Pattern.CASE_INSENSITIVE).matcher(html);
        while(tags.find()){
            String tag=tags.group();String label=attribute(tag,"size");if(label.isEmpty())label=attribute(tag,"label");
            if(label.matches("\\d+"))label+="p";
            add(result,seen,attribute(tag,"src"),label,base,true);result.choice=true;
        }
        Matcher page=Pattern.compile("data-page=([\"'])(.*?)\\1",Pattern.DOTALL).matcher(html);
        if(page.find())try{
            JSONObject data=new JSONObject(entities(page.group(2)));JSONObject props=data.optJSONObject("props");
            if("Video/Embed".equals(data.optString("component"))&&props!=null&&props.optString("mime").startsWith("video/"))
                add(result,seen,props.optString("url"),"Original",base,true);
        }catch(JSONException ignored){}
        String expanded=unpack(html);
        Matcher arrays=Pattern.compile("\\bsources\\s*[:=]\\s*\\[(.*?)\\]",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(expanded);
        while(arrays.find()){
            String array=arrays.group(1);
            Matcher objects=Pattern.compile("\\{([^{}]+)\\}",Pattern.DOTALL).matcher(array);boolean object=false;
            while(objects.find()){
                object=true;String entry=objects.group(1);String url=jsField(entry,"file");if(url.isEmpty())url=jsField(entry,"src");
                add(result,seen,url,jsField(entry,"label"),base,true);
            }
            if(!object){Matcher values=Pattern.compile("[\"']((?:\\\\.|[^\"'\\\\])++)[\"']").matcher(array);while(values.find())add(result,seen,jsUnescape(values.group(1)),"Normal",base,false);}
        }
        // Many quick-play/HD pages expose a single JWPlayer/Clappr file or hls field rather than
        // a sources[] array. These are media-specific fields, so URLs without a filename extension
        // are still valid candidates (signed CDN endpoints often look like /stream?id=...).
        Matcher strong=Pattern.compile("(?:^|[,\\s{;(])(?:[\"']?(?:file|hls|video)[\"']?)\\s*[:=]\\s*([\"'])((?:\\\\.|(?!\\1).)*+)\\1",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(expanded);
        while(strong.find())add(result,seen,jsUnescape(strong.group(2)),"Normal",base,true);
        // src outside a declared sources[] can also be media, but require a media-looking URL to
        // avoid accidentally selecting scripts/images.
        Matcher src=Pattern.compile("(?:^|[,\\s{;(])[\"']?src[\"']?\\s*[:=]\\s*([\"'])((?:\\\\.|(?!\\1).)*+)\\1",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(expanded);
        while(src.find())add(result,seen,jsUnescape(src.group(2)),"Normal",base,false);
        // Last compatibility pass: direct quoted media URLs, including signed URLs where the media
        // extension appears in a query value rather than the path.
        Matcher direct=Pattern.compile("https?:(?:\\\\/|/){2}[^\"'<>\\s]+",Pattern.CASE_INSENSITIVE).matcher(expanded);
        while(direct.find())add(result,seen,jsUnescape(direct.group()),"Normal",base,false);
        if(result.streams.size()>1)result.choice=true;
        return result;
    }
    private static String attribute(String tag,String key){Matcher m=Pattern.compile("\\b"+key+"\\s*=\\s*([\"'])(.*?)\\1",Pattern.CASE_INSENSITIVE).matcher(tag);return m.find()?entities(m.group(2)):"";}
    private static String jsField(String text,String key){Matcher m=Pattern.compile("(?:^|[,\\s])[\"']?"+key+"[\"']?\\s*:\\s*([\"'])((?:\\\\.|(?!\\1).)*+)\\1",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(text);return m.find()?jsUnescape(m.group(2)):"";}
    private static boolean mediaLooking(String url){
        String low=url.toLowerCase(Locale.ROOT);
        try{String path=new URL(url).getPath().toLowerCase(Locale.ROOT);if(path.endsWith(".mp4")||path.endsWith(".m3u8")||path.endsWith(".mpd")||path.endsWith(".mkv")||path.endsWith(".webm")||path.endsWith(".mov")||path.endsWith(".ts"))return true;}catch(Exception ignored){}
        return low.contains(".m3u8")||low.contains(".mp4")||low.contains(".mpd")||low.contains(".mkv")||low.contains(".webm")||low.contains(".mov")||low.contains(".ts?");
    }
    private static void add(Result result,Set<String> seen,String value,String label,String base,boolean trustedMediaField){
        if(value==null||value.isEmpty())return;
        try{
            String decoded=entities(jsUnescape(value)).trim();String url=new URL(new URL(base),decoded).toString();StreamCodec.validate(url);
            if(!trustedMediaField&&!mediaLooking(url))return;
            if(seen.add(url))result.streams.add(new Legacy.Stream(url,label==null||label.isEmpty()?"Normal":label,null));
        }catch(Exception ignored){}
    }
    static String entities(String s){return s.replace("&quot;","\"").replace("&#039;","'").replace("&#39;","'").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&");}
    static String jsUnescape(String s){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\'&&i+1<s.length()){
            char n=s.charAt(++i);if((n=='x'||n=='u')&&i+(n=='x'?2:4)<s.length())try{int count=n=='x'?2:4;out.append((char)Integer.parseInt(s.substring(i+1,i+count+1),16));i+=count;continue;}catch(NumberFormatException ignored){}
            if(n=='n')out.append('\n');else if(n=='r')out.append('\r');else if(n=='t')out.append('\t');else out.append(n);
        }else out.append(c);}return out.toString();
    }
    static String unpack(String html){
        Matcher m=Pattern.compile("\\}\\s*\\(\\s*'((?:\\\\.|[^'\\\\])*+)'\\s*,\\s*(\\d+)\\s*,\\s*\\d+\\s*,\\s*'((?:\\\\.|[^'\\\\])*+)'\\.split\\('\\|'\\)",Pattern.DOTALL).matcher(html);
        StringBuilder all=new StringBuilder(html);
        while(m.find())try{
            String payload=jsUnescape(m.group(1));int radix=Integer.parseInt(m.group(2));if(radix<2||radix>62)continue;
            String[] words=jsUnescape(m.group(3)).split("\\|",-1);Matcher tokens=Pattern.compile("\\b[0-9A-Za-z]+\\b").matcher(payload);StringBuffer out=new StringBuffer();
            while(tokens.find()){
                String token=tokens.group();long index=0;for(int i=0;i<token.length();i++){int digit="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(token.charAt(i));if(digit>=radix){index=words.length;break;}index=index*radix+digit;if(index>=words.length)break;}
                String replacement=index<words.length&&!words[(int)index].isEmpty()?words[(int)index]:token;tokens.appendReplacement(out,Matcher.quoteReplacement(replacement));
            }tokens.appendTail(out);all.append('\n').append(out);
        }catch(RuntimeException ignored){}
        return all.toString();
    }
}
