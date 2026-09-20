package awr.witcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.net.*;
import com.sun.net.httpserver.HttpServer;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public class OscarFlowTest {
    OscarApi.Transport old;OscarAuth oldAuth;OscarApi.ConnectionFactory oldConnections;
    @Before public void stub(){old=OscarApi.transport;oldAuth=OscarApi.auth;oldConnections=OscarApi.connections;OscarApi.auth=null;OscarApi.transport=(route,query)->new JSONObject("{\"status\":\"success\",\"data\":[],\"pagination\":{\"page\":1,\"total_pages\":1}}");}
    @After public void restore(){OscarApi.transport=old;OscarApi.auth=oldAuth;OscarApi.connections=oldConnections;}
    static JSONObject json(String s)throws Exception{return new JSONObject(s);}
    @Test public void mainTabsRemoveHitvWithoutChangingDramaDestinations(){ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup();try{WitcherTabs tabs=new WitcherTabs(c.get());assertEquals(5,tabs.getChildCount());assertFalse(allText(tabs).contains("HiTV"));assertEquals("عالم المصادر",tabs.getChildAt(4).getContentDescription());List<Integer> selected=new ArrayList<>();tabs.bind(0,selected::add);for(int i=1;i<4;i++)tabs.getChildAt(i).performClick();assertEquals(Arrays.asList(1,2,3),selected);assertArrayEquals(new int[]{0,1,2,3,5},WitcherTabs.VISIBLE_TABS);}finally{c.destroy();}}
    @Test public void searchAndPaginationReachTheRealEndpoint()throws Exception{String url=OscarApi.url(OscarCatalog.listRoute("movie"),OscarCatalog.query("movie","ذا منتاليست & test",3,OscarApi.params("category","7")));assertTrue(url.contains("api/movies/?"));assertTrue(url.contains("search="));assertTrue(url.contains("%26+test"));assertTrue(url.contains("page=3"));assertTrue(url.contains("app_version=14"));assertFalse(url.contains("114"));OscarCatalog.Page page=new OscarCatalog.Page(json("{\"data\":[{\"id\":500,\"title_ar\":\"عمل خارج الرئيسية\"}],\"pagination\":{\"page\":3,\"total_pages\":18}}"),"movie",3);assertTrue(page.more());assertEquals("500",page.items.get(0).id);}
    @Test public void noMetadataOrBannerIdLeaksIntoTitles()throws Exception{JSONObject data=json("{\"banners\":[{\"id\":91,\"item_type\":\"movie\",\"item_id\":15,\"title_ar\":\"فيلم\"}],\"actors\":[{\"id\":5,\"name\":\"Actor\"}]}");assertEquals("15",OscarCatalog.banners(data).get(0).id);assertEquals("movie",OscarCatalog.banners(data).get(0).kind);assertEquals("",OscarCatalog.sectionKind("actors_spotlight"));OscarCatalog.Section section=new OscarCatalog.Section(json("{\"section_type\":\"movies\",\"title_ar\":\"أفلام\",\"items\":[{\"id\":10,\"title_ar\":\"فيلم\"}],\"content_filters\":{\"category_id\":3,\"genre_id\":9},\"sort_by\":\"rating\"}"));assertEquals("3",section.filters.get("category"));assertEquals("9",section.filters.get("genre_id"));assertEquals(1,section.items.size());}
    @Test public void animeEpisodesUseTheirOwnRouteAndSearchKey()throws Exception{assertEquals("api/anime/episodes/show.php",OscarCatalog.detailRoute("anime_episode"));Map<String,String> p=OscarCatalog.query("anime_episode","14",2,OscarApi.params("season_id","81","sort","desc"));assertEquals("14",p.get("q"));assertFalse(p.containsKey("search"));assertEquals("24",p.get("per_page"));assertEquals("81",p.get("season_id"));}
    @Test public void upstreamDenialIsNotAnEmptySearch()throws Exception{assertTrue(OscarApi.httpError(403).contains("403"));assertEquals("غير متاح",OscarApi.apiError(json("{\"status\":\"error\",\"message\":\"غير متاح\"}")));assertNotNull(OscarApi.apiError(json("{\"is_blocked\":true}")));}
    static final class SigningFixture implements OscarAuth.Backend {
        boolean integrity=true;int signatures,diagnostics;String output=null;
        final List<String> paths=new ArrayList<>(),packages=new ArrayList<>(),nonces=new ArrayList<>();
        public boolean verify(Context c,String p){assertEquals(c.getPackageName(),p);return integrity;}
        public String sign(Context c,String p,String path,String ts,String nonce){signatures++;packages.add(p);paths.add(path);nonces.add(nonce);return output==null?"fixture-"+nonce:output;}
        public String certificate(Context c){diagnostics++;return "fixture-certificate";}
    }
    static final class ApiConnection extends HttpURLConnection {
        int code=200;String location;long serverDate=1700000120000L;boolean closed;
        ApiConnection(URL u){super(u);}
        public int getResponseCode(){return code;}
        public String getHeaderField(String name){return "Location".equals(name)?location:null;}
        public long getHeaderFieldDate(String name,long fallback){return "Date".equals(name)?serverDate:fallback;}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream("{\"status\":\"success\",\"data\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}
    }
    @Test public void sourceRequestsUseNativeContractAndActualPackageWithCorrectedTime()throws Exception {
        SigningFixture nativeApi=new SigningFixture();
        OscarApi.auth=new OscarAuth(RuntimeEnvironment.getApplication(),nativeApi,()->1700000000000L);
        List<ApiConnection> sent=new ArrayList<>();
        OscarApi.connections=u->{ApiConnection c=new ApiConnection(u);sent.add(c);return c;};
        OscarApi.request("api/movies/",OscarApi.params("page","1"));
        OscarApi.request("api/movies/",OscarApi.params("page","2"));
        assertEquals(2,nativeApi.signatures);assertEquals(1,nativeApi.diagnostics);
        assertEquals(Arrays.asList("/api/movies/","/api/movies/"),nativeApi.paths);
        assertEquals(RuntimeEnvironment.getApplication().getPackageName(),nativeApi.packages.get(0));
        assertNotEquals(nativeApi.nonces.get(0),nativeApi.nonces.get(1));
        assertTrue(nativeApi.nonces.get(0).matches("[a-f0-9]{8}"));
        assertEquals("1700000000",sent.get(0).getRequestProperty("X-Iron-Ts"));
        assertEquals("1700000120",sent.get(1).getRequestProperty("X-Iron-Ts"));
        assertEquals("fixture-"+nativeApi.nonces.get(0),sent.get(0).getRequestProperty("X-Iron-Sig"));
        assertEquals("fixture-certificate",sent.get(0).getRequestProperty("X-Iron-Diag"));
        assertFalse(sent.get(0).getInstanceFollowRedirects());assertTrue(sent.get(0).closed);
    }
    @Test public void integrityRejectionPreservesPublicHomeAndExplainsPrivateDenial()throws Exception {
        SigningFixture nativeApi=new SigningFixture();nativeApi.integrity=false;
        OscarApi.auth=new OscarAuth(RuntimeEnvironment.getApplication(),nativeApi,()->1700000000000L);
        List<ApiConnection> sent=new ArrayList<>();
        OscarApi.connections=u->{ApiConnection c=new ApiConnection(u);c.code=u.getPath().contains("home")?200:403;sent.add(c);return c;};
        assertNotNull(OscarApi.request("api/v2/home.php",Collections.emptyMap()));
        try{OscarApi.request("api/series/",Collections.emptyMap());fail("Server refusal must be surfaced");}
        catch(java.io.IOException e){assertTrue(e.getMessage().contains("403"));assertTrue(e.getMessage().contains("IRON_INTEGRITY"));}
        assertEquals(0,nativeApi.signatures);assertNull(sent.get(0).getRequestProperty("X-Iron-Sig"));
    }
    @Test public void signedRedirectsCannotForwardAuthenticationOutsideSource()throws Exception {
        SigningFixture nativeApi=new SigningFixture();
        OscarApi.auth=new OscarAuth(RuntimeEnvironment.getApplication(),nativeApi,()->1700000000000L);
        List<ApiConnection> sent=new ArrayList<>();
        OscarApi.connections=u->{ApiConnection c=new ApiConnection(u);c.code=302;c.location="https://other.example/api/movies/";sent.add(c);return c;};
        try{OscarApi.request("api/movies/",Collections.emptyMap());fail("Foreign redirect must not be followed");}
        catch(java.io.IOException e){assertTrue(e.getMessage().contains("إعادة التوجيه"));}
        assertEquals(1,sent.size());assertEquals(1,nativeApi.signatures);
        assertFalse(OscarAuth.accepts(new URL("https://ostvapp.cam@other.example/api/series/")));
        assertFalse(OscarAuth.accepts(new URL("http://ostvapp.cam/api/series/")));
    }
    @Test public void nativeLoadFailureAndInvalidHeaderAreExplicitAndDoNotCrash()throws Exception {
        OscarAuth.Backend unavailable=new OscarAuth.Backend(){
            public boolean verify(Context c,String p){throw new UnsatisfiedLinkError("fixture");}
            public String sign(Context c,String p,String path,String t,String n){throw new AssertionError();}
            public String certificate(Context c){throw new AssertionError();}
        };
        URL url=new URL("https://ostvapp.cam/api/series/");
        assertEquals("IRON_LIBRARY",new OscarAuth(RuntimeEnvironment.getApplication(),unavailable,System::currentTimeMillis).headers(url).status);
        SigningFixture malformed=new SigningFixture();malformed.output="bad\r\nInjected: header";
        OscarAuth.Result result=new OscarAuth(RuntimeEnvironment.getApplication(),malformed,System::currentTimeMillis).headers(url);
        assertEquals("IRON_EMPTY",result.status);assertTrue(result.headers.isEmpty());
    }
    @Test public void sourceDenialAndRetryStayInsideTheApp()throws Exception{
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        OscarApi.transport=(r,q)->{requests.incrementAndGet();throw new java.io.IOException(OscarApi.httpError(403));};
        ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup();
        try{
            OscarExperience.Screen screen=new OscarExperience.Screen(c.get());screen.show();
            waitForText(screen,"403");
            String error=allText(screen.content);
            assertFalse(error.contains("أوسكار"));assertFalse(error.contains("Oscar"));assertFalse(error.contains("لا توجد أقسام"));
            assertEquals("إعادة المحاولة",((TextView)screen.content.getChildAt(2)).getText().toString());
            assertEquals(1,requests.get());screen.content.getChildAt(2).performClick();waitForText(screen,"403");
            assertEquals(2,requests.get());assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
            assertTrue(screen.dialog.isShowing());assertEquals("home",screen.state.page);
            screen.select("settings");String settings=allText(screen.content);
            assertTrue(settings.contains("عالم AWR"));assertFalse(settings.contains("أوسكار"));assertFalse(settings.contains("Oscar"));
            screen.dialog.dismiss();
        }finally{c.destroy();}
    }
    static void waitForText(OscarExperience.Screen screen,String text)throws Exception{long deadline=System.currentTimeMillis()+3000;while(!allText(screen.content).contains(text)&&System.currentTimeMillis()<deadline){Thread.sleep(20);Shadows.shadowOf(Looper.getMainLooper()).idle();}assertTrue(allText(screen.content).contains(text));}

    @Test public void sourceImagesUseCorrectHost(){assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg",OscarCatalog.image("/abc.jpg"));assertEquals(OscarApi.BASE+"uploads/a.jpg",OscarCatalog.image("/uploads/a.jpg"));assertEquals(OscarApi.BASE+"storage/a.jpg",OscarCatalog.image("https://admin.dramaramadan.net/storage/a.jpg"));}
    @Test public void opaquePlayerLinksNeverGoToMx()throws Exception{assertEquals("",OscarMedia.sourceUrl(json("{\"deep_link\":\"tdm://protected-payload\"}")));assertFalse(OscarMedia.http("javascript:alert(1)"));assertFalse(OscarMedia.http("https://user:pass@example.org/a"));String signed="https://media.example.org/play?id=7&sig=a%2Bb";assertEquals(signed,OscarMedia.sourceUrl(new JSONObject().put("url",signed).put("deep_link","tdm://something")));}
    @Test public void masterPlaylistShowsAllQualitiesAndPreservesTokens()throws Exception{String base="https://media.example.org/hls/master.m3u8?token=x";List<OscarMedia.Stream> streams=OscarMedia.hls(base,"#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720\n720.m3u8?sig=a%2Bb\n#EXT-X-STREAM-INF:BANDWIDTH=2300000,RESOLUTION=1920x1080\n1080.m3u8?sig=z",OscarApi.params("Referer","https://publisher.example.org/"));assertEquals(3,streams.size());assertEquals(base,streams.get(0).url);assertEquals("720p",streams.get(1).label);assertEquals("https://media.example.org/hls/720.m3u8?sig=a%2Bb",streams.get(1).url);Intent mx=OscarMedia.mxIntent(streams.get(1),"الفيلم");assertEquals("com.mxtech.videoplayer.ad",mx.getPackage());assertEquals("application/x-mpegURL",mx.getType());assertEquals("Referer",mx.getStringArrayExtra("headers")[0]);}
    @Test public void actualHttpFlowResolvesHtmlAndRejects403()throws Exception{HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/page",e->{byte[] data="<video><source src='/movie.mp4?sig=a%2Bb' label='720p' type='video/mp4'></video>".getBytes("UTF-8");e.getResponseHeaders().set("Content-Type","text/html");e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();});server.createContext("/movie.mp4",e->{e.getResponseHeaders().set("Content-Type","video/mp4");e.sendResponseHeaders(200,1);e.getResponseBody().write(0);e.close();});server.createContext("/blocked",e->{e.sendResponseHeaders(403,-1);e.close();});server.createContext("/empty",e->{byte[] bytes="<html>Please sign in</html>".getBytes("UTF-8");e.getResponseHeaders().set("Content-Type","text/html");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();try{List<OscarMedia.Stream> r=OscarMedia.resolve(base+"/page",Collections.emptyMap(),0);assertEquals(1,r.size());assertEquals("720p",r.get(0).label);assertTrue(r.get(0).url.endsWith("?sig=a%2Bb"));assertTrue(OscarMedia.resolve(base+"/empty",Collections.emptyMap(),0).isEmpty());try{OscarMedia.resolve(base+"/blocked",Collections.emptyMap(),0);fail("403 must propagate");}catch(java.io.IOException e){assertTrue(e.getMessage().contains("403"));}}finally{server.stop(0);}}
    @Test public void crossHostMediaDoesNotLeakAccountHeaders()throws Exception{List<OscarMedia.Stream> streams=OscarMedia.htmlSources("https://site.example.org/page","<source src='https://cdn.example.org/stream.m3u8'>",OscarApi.params("Authorization","Bearer TEST","Cookie","test=1"));assertFalse(streams.get(0).headers.containsKey("Authorization"));assertFalse(streams.get(0).headers.containsKey("Cookie"));}
    @Test public void localFavoritesSurviveScreenRecreation()throws Exception{ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup();try{OscarExperience.Screen s=new OscarExperience.Screen(c.get());s.prefs.edit().clear().commit();OscarCatalog.Item movie=new OscarCatalog.Item("movie",json("{\"id\":81,\"title_ar\":\"اختبار\"}"));s.toggleFavorite(movie);assertTrue(new OscarExperience.Screen(c.get()).isFavorite(movie));s.toggleFavorite(movie);assertFalse(s.isFavorite(movie));}finally{c.destroy();}}
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="ar-rYE-w411dp-h891dp-420dpi")
    public void nestedSlidingTabsAndMainReturnRenderCorrectly()throws Exception{ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible();try{Activity a=c.get();a.getApplicationInfo().flags|=android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL;OscarExperience.Screen s=new OscarExperience.Screen(a);s.state.page="favorites";s.active="favorites";s.show();assertTrue(s.dialog.isShowing());assertTrue(s.root.getChildAt(s.root.getChildCount()-1) instanceof HorizontalScrollView);LinearLayout row=(LinearLayout)s.tabs.getChildAt(0);assertEquals(11,row.getChildCount());assertEquals("التبويبات الرئيسية",row.getChildAt(10).getContentDescription());assertEquals("عالم المصادر",WitcherTabs.LABELS[5]);
        s.content.removeAllViews();List<OscarCatalog.Item> items=new ArrayList<>();String[] names={"قصر المرجان","A Tale of Two Cities","عالم الأفلام","رحلة جديدة","مغامرة"};java.lang.reflect.Field cache=Ui.class.getDeclaredField("CACHE");cache.setAccessible(true);android.util.LruCache<String,Bitmap> images=(android.util.LruCache<String,Bitmap>)cache.get(null);for(int i=0;i<names.length;i++){String url="https://fixture.invalid/"+i;JSONObject o=new JSONObject().put("id",i+1).put("title_ar",names[i]).put("poster",url).put("banner",url).put("release_year",2026);Bitmap b=Bitmap.createBitmap(360,520,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);Paint p=new Paint();p.setColor(new int[]{0xff26394c,0xff40334a,0xff405249,0xff50493b,0xff254452}[i]);canvas.drawPaint(p);p.setColor(0xffeec60a);p.setTextSize(70);canvas.drawText("AWR",75,260,p);images.put(url,b);items.add(new OscarCatalog.Item("series",o));}s.hero(items);s.heading(s.content,"رائج اليوم — بيانات اختبار");s.grid(s.content,items);View root=s.root;int width=1080,height=2340;root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);Bitmap shot=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(shot));try(java.io.FileOutputStream out=new java.io.FileOutputStream("build/oscar-ui-review.png")){shot.compress(Bitmap.CompressFormat.PNG,100,out);}s.select("main");assertFalse(s.dialog.isShowing());}finally{c.pause().stop().destroy();}}
    @Test public void staleCatalogueCallbackCannotReplaceNewTab()throws Exception{CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);OscarApi.transport=(r,q)->{entered.countDown();release.await(2,TimeUnit.SECONDS);return json("{\"data\":{\"banners\":[{\"item_id\":99,\"item_type\":\"movie\",\"title_ar\":\"Stale result\"}]}}");};ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup();try{OscarExperience.Screen s=new OscarExperience.Screen(c.get());s.show();assertTrue(entered.await(2,TimeUnit.SECONDS));s.select("settings");release.countDown();Thread.sleep(80);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("settings",s.state.page);assertFalse(allText(s.content).contains("Stale result"));s.dialog.dismiss();}finally{release.countDown();c.destroy();}}
    static String allText(View v){if(v instanceof TextView)return ((TextView)v).getText().toString();StringBuilder s=new StringBuilder();if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++)s.append(allText(((android.view.ViewGroup)v).getChildAt(i)));return s.toString();}
}
