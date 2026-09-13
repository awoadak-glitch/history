package awr.witcher;

import android.content.Intent;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import android.util.Base64;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.*;
import java.util.*;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="ar-rYE-w411dp-h891dp-420dpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeFlowTest {
    private ActivityController<DramaActivity> controller;
    private DramaActivity activity;
    private static final String CHANNEL="{\"id\":701,\"title\":\"قناة الاختبار\",\"playas\":\"1\",\"sources\":[{\"title\":\"السيرفر الأول\",\"type\":\"mp4\",\"kind\":\"both\",\"premium\":\"1\",\"url\":\"https://media.example.org/video.mp4?token=abc\",\"host\":\"https://origin.example.org/\",\"cookie\":\"session=fixture\"}]}";
    private void cache(String route,String body)throws Exception{
        Field f=Api.class.getDeclaredField("CACHE");f.setAccessible(true);
        Class<?> entry=Class.forName("awr.witcher.Api$Entry");Constructor<?> ctor=entry.getDeclaredConstructor(String.class);ctor.setAccessible(true);
        Map map=(Map)f.get(null);synchronized(map){map.put(route,ctor.newInstance(body));}
    }
    private void start()throws Exception{
        RuntimeEnvironment.getApplication().getApplicationInfo().flags|=android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL;
        cache(Api.list(3,0,"created",0),"["+CHANNEL+"]");cache(Api.detail(true,701),CHANNEL);
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),DramaActivity.class).putExtra("tab",3);
        controller=Robolectric.buildActivity(DramaActivity.class,intent).setup().visible();activity=controller.get();
        waitFor("قناة الاختبار");
    }
    private TextView text(View v,String value){
        if(v instanceof TextView&&value.equals(((TextView)v).getText().toString()))return (TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=text(((ViewGroup)v).getChildAt(i),value);if(t!=null)return t;}return null;
    }
    private View desc(View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=desc(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    private void waitFor(String label)throws Exception{for(int i=0;i<200;i++){shadowOf(Looper.getMainLooper()).idle();if(text(activity.getWindow().getDecorView(),label)!=null)return;Thread.sleep(5);}fail("Missing UI text: "+label);}
    private void click(String label){TextView t=text(activity.getWindow().getDecorView(),label);assertNotNull(label,t);View v=t;while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();assertTrue(v.performClick());shadowOf(Looper.getMainLooper()).idle();}
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}

    @Test public void channelsDetailsServersAndMxHandoff()throws Exception{
        start();click("قناة الاختبار");waitFor("مشاهدة");click("مشاهدة");waitFor("السيرفر الأول");click("السيرفر الأول");
        Intent intent=shadowOf(activity).getNextStartedActivity();assertNotNull(intent);assertEquals("com.mxtech.videoplayer.ad",intent.getPackage());
        assertEquals("https://media.example.org/video.mp4?token=abc",intent.getDataString());assertFalse(intent.hasExtra("is_encoded"));assertFalse(intent.hasExtra("url"));
        List<String> headers=Arrays.asList(intent.getStringArrayExtra("headers"));assertTrue(headers.contains("Cookie"));assertTrue(headers.contains("session=fixture"));assertTrue(headers.contains("https://origin.example.org/"));
        activity.onBackPressed();waitFor("مشاهدة");activity.onBackPressed();waitFor("قناة الاختبار");
    }
    @Test public void searchUsesRemoteResultsAndEscapesQuery()throws Exception{
        start();String query="فيلم / 2026";cache(Api.search(query,0),"{\"posters\":[{\"id\":51,\"title\":\"نتيجة من البحث\",\"type\":\"movie\"}],\"channels\":[]}");
        desc(activity.getWindow().getDecorView(),"بحث في عالم الدراما").performClick();
        EditText field=findEdit(activity.getWindow().getDecorView());assertNotNull(field);field.setText("استعلام قديم");field.setText(query);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));waitFor("نتيجة من البحث");
        assertTrue(Api.search(query,0).contains("%2F"));assertTrue(Api.search(query,0).endsWith("/0/"));
    }
    private EditText findEdit(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText e=findEdit(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}return null;}
    @Test public void serverEnvelopePreservesOrder()throws Exception{
        String raw="[{\"id\":12,\"kind\":\"play\"},{\"id\":3,\"kind\":\"both\"},{\"id\":7,\"kind\":\"download\"}]";
        JSONArray decoded=(JSONArray)Api.decode("fixture-prefix:"+Base64.encodeToString(raw.getBytes("UTF-8"),Base64.NO_WRAP));
        assertEquals(12,decoded.getJSONObject(0).getInt("id"));assertEquals(3,decoded.getJSONObject(1).getInt("id"));assertEquals(7,decoded.getJSONObject(2).getInt("id"));
        assertFalse(Api.publicAccess("2"));assertFalse(Api.publicAccess("3"));assertTrue(Api.publicAccess("1"));
    }
    @Test public void seriesHomeUsesActualServerSections()throws Exception{
        start();cache("first/","{\"slides\":[],\"genres\":[{\"title\":\"آخر الحلقات المضافة\",\"posters\":[{\"id\":51,\"title\":\"مسلسل الاختبار\",\"type\":\"serie\"}]},{\"title\":\"آخر الأفلام المضافة\",\"posters\":[{\"id\":61,\"title\":\"فيلم الاختبار\",\"type\":\"movie\"}]}]}");
        click("المسلسلات");waitFor("آخر الحلقات المضافة");assertNull(text(activity.getWindow().getDecorView(),"آخر الأفلام المضافة"));assertNotNull(text(activity.getWindow().getDecorView(),"جميع المسلسلات"));
    }
    @Test public void dwEnvelopeIsDecodedBeforeMxReceivesIt()throws Exception{
        String uri="https://media.example.org/movie.mp4?token=abc";
        String fixture=new StringBuilder(Base64.encodeToString(uri.getBytes("UTF-8"),Base64.NO_WRAP)).reverse()+"A1b2C3d4E5f6G7h8I";
        assertEquals(uri,StreamCodec.unwrap(fixture,true));assertEquals(uri,StreamCodec.unwrap(uri,false));
        try{StreamCodec.unwrap("not-a-url",false);fail("Invalid URI accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void explicitPlaybackHeadersOverrideGuessedReferer()throws Exception{
        JSONObject source=new JSONObject("{\"headers\":{\"referer\":\"https://publisher.example.org/watch/42\",\"origin\":\"https://publisher.example.org\",\"user-agent\":\"Provider agent\"},\"host\":\"https://cdn.example.org/\"}");
        Map<String,String> headers=Media.headers(source,"https://cdn.example.org/video.mp4");
        assertEquals("https://publisher.example.org/watch/42",headers.get("Referer"));
        assertEquals("https://publisher.example.org",headers.get("Origin"));
        assertEquals("Provider agent",headers.get("User-Agent"));
        headers.put("Injected","bad\r\nextra: value");
        assertFalse(Arrays.asList(Media.mxIntent("https://cdn.example.org/video.mp4",headers,"Title").getStringArrayExtra("headers")).contains("Injected"));
    }
    @Test public void providerPagesAreNotMistakenForPlaylists(){
        assertTrue(Media.needsExtraction("https://provider.example.org/embed/42","m3u8",false,false));
        assertTrue(Media.needsExtraction("https://provider.example.org/embed/42","mov",false,false));
        assertTrue(Media.needsExtraction("https://provider.example.org/embed/42","mkv",true,false));
        assertFalse(Media.needsExtraction("https://cdn.example.org/master.M3U8?token=abc","m3u8",false,false));
        assertFalse(Media.needsExtraction("https://cdn.example.org/live?id=42","m3u8",false,true));
    }
    @Test public void catalogKeepsLoadedPagesAfterDetailsAndRecreation()throws Exception{
        start();String second=CHANNEL.replace("701","702").replace("قناة الاختبار","قناة الصفحة الثانية");
        cache(Api.list(3,0,"created",1),"["+second+"]");cache(Api.detail(true,702),second);
        click("عرض المزيد");waitFor("قناة الصفحة الثانية");click("قناة الصفحة الثانية");waitFor("مشاهدة");
        activity.onBackPressed();waitFor("قناة الصفحة الثانية");
        controller.recreate();activity=controller.get();waitFor("قناة الصفحة الثانية");
    }
    @Test public void searchKeepsSecondPageWhenReturningFromMovie()throws Exception{
        start();String query="مسلسل";
        String movie="{\"id\":55,\"title\":\"نتيجة الصفحة الثانية\",\"type\":\"movie\",\"playas\":\"1\"}";
        cache(Api.search(query,0),"{\"posters\":[{\"id\":54,\"title\":\"نتيجة أولى\",\"type\":\"movie\"}],\"channels\":[]}");
        cache(Api.search(query,1),"{\"posters\":["+movie+"],\"channels\":[]}");cache(Api.detail(false,55),movie);cache("role/by/poster/55/","[]");
        desc(activity.getWindow().getDecorView(),"بحث في عالم الدراما").performClick();findEdit(activity.getWindow().getDecorView()).setText(query);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));waitFor("نتيجة أولى");click("نتائج إضافية");waitFor("نتيجة الصفحة الثانية");
        click("نتيجة الصفحة الثانية");waitFor("مشاهدة");activity.onBackPressed();waitFor("نتيجة الصفحة الثانية");
        assertEquals(query,findEdit(activity.getWindow().getDecorView()).getText().toString());
    }
    @Test public void countryFilterUsesTheSelectedServerCountry()throws Exception{
        start();cache("country/all/","[{\"id\":21,\"title\":\"اليمن\"}]");
        cache(Api.list(3,0,21,"created",0),"["+CHANNEL.replace("قناة الاختبار","قناة اليمن")+"]");
        click("جميع الدول");android.app.AlertDialog dialog=null;
        for(int i=0;i<200;i++){shadowOf(Looper.getMainLooper()).idle();dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();if(dialog!=null&&dialog.isShowing())break;Thread.sleep(5);}
        assertNotNull(dialog);dialog.getListView().performItemClick(null,1,1);waitFor("قناة اليمن");
    }
    @Test public void seriesSeasonsEpisodeServersAndCastAreConnected()throws Exception{
        start();String series="{\"id\":51,\"title\":\"مسلسل الاختبار\",\"type\":\"serie\",\"playas\":\"1\",\"downloadas\":\"1\"}";
        cache("first/","{\"slides\":[],\"genres\":[{\"title\":\"جديد المسلسلات\",\"posters\":["+series+"]}]}");
        cache(Api.detail(false,51),series);cache("role/by/poster/51/","[{\"id\":81,\"name\":\"الممثل الأول\",\"role\":\"الدور الأول\"}]");cache("movie/by/actor/81/","["+series+"]");
        cache("season/by/serie/51/","[{\"title\":\"الموسم الأول\",\"episodes\":[{\"id\":901,\"title\":\"الحلقة الأولى\",\"playas\":\"1\"}]}]");
        cache(Api.sources(true,901),"[{\"title\":\"مشاهدة الحلقة\",\"kind\":\"play\",\"type\":\"mp4\",\"url\":\"https://media.example.org/episode.mp4\"},{\"title\":\"تنزيل فقط\",\"kind\":\"download\",\"type\":\"mp4\",\"url\":\"https://media.example.org/download.mp4\"}]");
        click("المسلسلات");waitFor("جديد المسلسلات");click("مسلسل الاختبار");waitFor("الممثل الأول");
        click("الممثل الأول");waitFor("مسلسل الاختبار");activity.onBackPressed();waitFor("عرض الحلقات");
        click("عرض الحلقات");waitFor("الحلقة الأولى");click("الحلقة الأولى");waitFor("مشاهدة الحلقة");assertNull(text(activity.getWindow().getDecorView(),"تنزيل فقط"));
        click("سيرفرات التنزيل");waitFor("تنزيل فقط");assertNull(text(activity.getWindow().getDecorView(),"مشاهدة الحلقة"));
        click("سيرفرات المشاهدة");waitFor("مشاهدة الحلقة");click("مشاهدة الحلقة");Intent intent=shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);assertEquals(Media.MX,intent.getPackage());assertEquals("https://media.example.org/episode.mp4",intent.getDataString());
    }
    @Test public void redirectedPlaylistKeepsEffectiveBaseURL()throws Exception{
        com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/start.m3u8",exchange->{exchange.getResponseHeaders().add("Location","/nested/master.m3u8");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/nested/master.m3u8",exchange->{byte[] bytes="#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=1280x720\n720.m3u8\n".getBytes("UTF-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.start();try{
            Api.Response response=Api.readResponse("http://127.0.0.1:"+server.getAddress().getPort()+"/start.m3u8",Collections.emptyMap(),4096);
            assertTrue(response.url.endsWith("/nested/master.m3u8"));assertTrue(new java.net.URL(new java.net.URL(response.url),"720.m3u8").toString().endsWith("/nested/720.m3u8"));
        }finally{server.stop(0);}
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderNativeScreensForReview()throws Exception{
        start();render("channels");click("قناة الاختبار");waitFor("مشاهدة");click("مشاهدة");waitFor("السيرفر الأول");render("servers");
    }
    private void render(String name)throws Exception{
        View view=activity.findViewById(android.R.id.content);int width=1080,height=2340;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));
        java.io.File file=new java.io.File("artifacts/qa-"+name+".png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}assertTrue(file.length()>1000);
    }
}
