package awr.witcher;

/** DW's transport envelope is not a playable URI. Always hand external players a clean HTTP(S) URI. */
public final class StreamCodec {
    private StreamCodec(){}

    /**
     * Decodes Drama World's player transport envelope when it is flagged, and also safely detects
     * an accidentally wrapped value when the flag was lost. A normal HTTP(S) URL is left intact.
     */
    public static String unwrap(String value,boolean encoded){
        if(value==null)throw new IllegalArgumentException("Missing URL");
        String clean=value.trim();
        if(!encoded&&(clean.startsWith("https://")||clean.startsWith("http://"))){validate(clean);return clean;}
        return decodeEnvelope(clean);
    }

    /** Final guard used immediately before MX/download handoff. */
    public static String forExternalPlayer(String value){
        if(value==null)throw new IllegalArgumentException("Missing URL");
        String clean=value.trim();
        if(clean.startsWith("https://")||clean.startsWith("http://")){validate(clean);return clean;}
        return decodeEnvelope(clean);
    }

    private static String decodeEnvelope(String value){
        if(value.length()<=17)throw new IllegalArgumentException("Incomplete DW envelope");
        try{
            String payload=new StringBuilder(value.substring(0,value.length()-17)).reverse().toString();
            String url=new String(android.util.Base64.decode(payload,android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8).trim();
            validate(url);return url;
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("Invalid DW envelope",e);}
    }

    public static void validate(String value){
        if(!(value.startsWith("https://")||value.startsWith("http://")))throw new IllegalArgumentException("Not a web stream URL");
        try{java.net.URI uri=new java.net.URI(value);if(uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Invalid stream authority");}
        catch(java.net.URISyntaxException e){throw new IllegalArgumentException("Invalid stream URI",e);}
    }
}
