package awr.witcher;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public class HitvFlowTest {
    @Test public void unavailableContentRetainsTheProviderExplanation()throws Exception{
        String message="هذا المحتوى غير متاح للعرض بناءً على طلب حامل حقوق النشر";
        assertEquals(message,HitvApi.apiMessage(new JSONObject().put("code","A0001").put("msg",message)));
    }
    @Test public void trailersAndApplicationInstallersAreNeverMovieQualities()throws Exception{
        JSONArray payload=new JSONArray("[{\"source\":\"previewInfo\",\"data\":{\"url\":\"https://cdn.example.org/trailer.mp4\"}},{\"source\":\"downloadUrls\",\"data\":{\"googlePlay\":\"https://cdn.example.org/app.apk\",\"officialPCClient\":\"https://cdn.example.org/setup.exe\"}}]");
        assertTrue(HitvExperience.sources(payload).isEmpty());
        for(String ext:new String[]{"apk","apks","exe","msi","dmg","html","vtt","srt"})assertFalse(HitvExperience.mediaUrl("https://cdn.example.org/file."+ext+"?token=1"));
    }
    @Test public void fullVideoQualitiesRetainSignedUrlsHeadersAndCaptions()throws Exception{
        JSONObject payload=new JSONObject("{\"headers\":{\"Referer\":\"https://publisher.example.org/\"},\"sources\":[{\"playUrl\":\"https://cdn.example.org/watch?id=17&sig=a%2Bb\",\"quality\":\"1080p\",\"type\":\"hls\"},{\"url\":\"https://cdn.example.org/720.mp4\",\"definition\":\"720p\"}],\"subtitles\":[{\"url\":\"https://cdn.example.org/caption?id=17\",\"languageCode\":\"ar\",\"label\":\"العربية\",\"default\":true}]}");
        ArrayList<HitvExperience.Source> streams=HitvExperience.sources(payload);assertEquals(2,streams.size());
        HitvExperience.Source first=streams.get(0);assertEquals("1080p",first.label);assertEquals("https://cdn.example.org/watch?id=17&sig=a%2Bb",first.url);
        assertEquals("https://publisher.example.org/",first.headers.get("referer"));assertEquals(1,first.captions.size());assertEquals("ar",first.captions.get(0).language);assertTrue(first.captions.get(0).selected);
        assertEquals("application/x-mpegURL",HitvPlayer.mime(first.type,first.url));
    }
    @Test public void subtitleObjectsAndArtworkDoNotBecomePlaybackStreams()throws Exception{
        JSONObject payload=new JSONObject("{\"poster\":{\"url\":\"https://cdn.example.org/image?id=17\"},\"subtitleList\":[{\"url\":\"https://cdn.example.org/text?id=17\"}],\"sources\":[{\"file\":\"https://cdn.example.org/movie.mp4\"}]}");
        ArrayList<HitvExperience.Source> sources=HitvExperience.sources(payload);assertEquals(1,sources.size());assertTrue(sources.get(0).url.endsWith("movie.mp4"));assertEquals(1,sources.get(0).captions.size());
    }
}
