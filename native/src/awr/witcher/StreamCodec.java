package awr.witcher;

/** DW's transport envelope is not a playable URI. Decode only when explicitly flagged. */
public final class StreamCodec {
    private StreamCodec(){}
    public static String unwrap(String value,boolean encoded){
        if(value==null)throw new IllegalArgumentException("Missing URL");
        if(!encoded){validate(value);return value;}
        if(value.length()<=17)throw new IllegalArgumentException("Incomplete DW envelope");
        String payload=new StringBuilder(value.substring(0,value.length()-17)).reverse().toString();
        String url=new String(android.util.Base64.decode(payload,android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8);
        validate(url);return url;
    }
    public static void validate(String value){
        if(!(value.startsWith("https://")||value.startsWith("http://")))throw new IllegalArgumentException("Not a web stream URL");
        try{java.net.URI uri=new java.net.URI(value);if(uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Invalid stream authority");}
        catch(java.net.URISyntaxException e){throw new IllegalArgumentException("Invalid stream URI",e);}
    }
}
