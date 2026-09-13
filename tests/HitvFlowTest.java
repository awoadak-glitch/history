package awr.witcher;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;
import android.app.Activity;
import android.graphics.*;
import android.view.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public class HitvFlowTest {
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="ar-rYE-w411dp-h891dp-420dpi")
    public void tabRemainsSelectedAndBackReturnsWithinHitv()throws Exception{
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            Activity a=controller.get();a.getApplicationInfo().flags|=android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL;
            HitvExperience.Screen screen=new HitvExperience.Screen(a);screen.state.kind="collection";screen.state.title="أفلام HiTV";
            java.nio.file.Path review=java.nio.file.Paths.get("build/hitv-review/items.json");
            JSONArray entries=java.nio.file.Files.exists(review)?new JSONArray(new String(java.nio.file.Files.readAllBytes(review),"UTF-8")):new JSONArray("[{\"id\":1,\"name\":\"فيلم اختبار\",\"category\":0}]");
            screen.state.collection=HitvExperience.items(entries);
            java.lang.reflect.Field cache=Ui.class.getDeclaredField("CACHE");cache.setAccessible(true);
            android.util.LruCache<String,Bitmap> images=(android.util.LruCache<String,Bitmap>)cache.get(null);
            for(int i=0;i<screen.state.collection.size();i++){
                HitvExperience.Item item=screen.state.collection.get(i);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;Bitmap image=BitmapFactory.decodeFile("build/hitv-review/"+i+".jpg",options);
                if(image!=null)images.put(item.vertical,image);else {item.vertical="";item.horizontal="";}
            }
            screen.show();assertTrue(screen.dialog.isShowing());
            WitcherTabs tabs=(WitcherTabs)screen.root.getChildAt(screen.root.getChildCount()-1);
            assertEquals(5,tabs.getChildCount());assertTrue(tabs.getChildAt(4).isSelected());tabs.getChildAt(4).performClick();assertTrue(screen.dialog.isShowing());
            HitvExperience.State next=new HitvExperience.State("collection");next.title="قائمة أخرى";next.collection=screen.state.collection;screen.navigate(next);
            screen.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));assertEquals("أفلام HiTV",screen.state.title);
            View root=screen.root;int width=1080,height=2340;root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
            try(java.io.FileOutputStream out=new java.io.FileOutputStream("build/hitv-review.png")){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            screen.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));assertFalse(screen.dialog.isShowing());
        }finally{controller.pause().stop().destroy();}
    }
    @Test public void episodeNumbersAndMovieCategorySurvivePartialDetails()throws Exception{
        HitvExperience.Item series=HitvExperience.normalize(new JSONObject("{\"id\":1,\"name\":\"Series\",\"category\":1,\"episodeVo\":[{\"seriesNo\":1},{\"seriesNo\":3},{\"seriesNo\":14}]}"));
        assertEquals(Arrays.asList(1,3,14),series.episodeNumbers);assertEquals(3,series.episodes);
        HitvExperience.Item movie=HitvExperience.normalize(new JSONObject("{\"id\":2,\"name\":\"Movie\",\"category\":0}"));
        assertEquals(0,HitvExperience.merge(movie,HitvExperience.normalize(new JSONObject("{\"introduction\":\"New synopsis\"}"))).category);
    }
    @Test public void castAndGenreMetadataAreNotAddedAsFilms()throws Exception{
        JSONObject response=new JSONObject("{\"items\":[{\"id\":1,\"name\":\"Film\",\"category\":0}],\"stars\":[{\"id\":2,\"name\":\"Actor\",\"avatar\":\"https://image.example.org/actor.jpg\"}],\"genres\":[{\"id\":3,\"name\":\"Comedy\"}]}");
        assertEquals(1,HitvExperience.items(response).size());
    }
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
