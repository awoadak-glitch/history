import java.net.*;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

// Runs as an independent Android shell process; never attaches to either app.
// The four public read-only URLs contain no session or user credentials.
public final class AndroidNetworkProbe {
    public static void main(String[] args) {
        String[] urls = {
            "https://www.google.com/generate_204",
            "https://raw.githubusercontent.com/MBoshkash/ostv-config/main/domains.json",
            "https://api.ossccaarr111.online/api/ping.php",
            "https://api.myychann.site/api/ping.php"
        };
        for (String value : urls) {
            HttpsURLConnection c = null;
            String result = "unstarted";
            long start = System.currentTimeMillis();
            try {
                c = (HttpsURLConnection)new URL(value).openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", "okhttp/4.12.0");
                int status = c.getResponseCode();
                InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                if (in != null) {
                    for (int n; (n = in.read(buffer)) != -1 && data.size() < 16384; ) data.write(buffer, 0, n);
                    in.close();
                }
                String body = new String(data.toByteArray(), "UTF-8");
                result = "status=" + status + " bytes=" + data.size()
                    + " expectedPing=" + (body.contains("\"status\"") && body.contains("\"ok\""));
            } catch (Exception e) {
                result = "error=" + e.getClass().getSimpleName();
                Throwable cause = e.getCause();
                if (cause != null) result += " cause=" + cause.getClass().getSimpleName()
                    + " causeMessage=" + String.valueOf(cause.getMessage()).replace('\n',' ');
            } finally {
                if (c != null) c.disconnect();
            }
            System.out.println(new URIBuilder(value).host + " " + result + " elapsedMs=" + (System.currentTimeMillis()-start));
        }
    }
    private static class URIBuilder {
        String host;
        URIBuilder(String value) { try { host = new URI(value).getHost(); } catch (Exception e) { host = "invalid"; } }
    }
}
