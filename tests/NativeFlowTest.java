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
@Config(sdk=34,manifest=Config.NONE)
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
    @Test public void dwEnvelopeIsDecodedBeforeMxReceivesIt()throws Exception{
        String uri="https://media.example.org/movie.mp4?token=abc";
        String fixture=new StringBuilder(Base64.encodeToString(uri.getBytes("UTF-8"),Base64.NO_WRAP)).reverse()+"A1b2C3d4E5f6G7h8I";
        assertEquals(uri,StreamCodec.unwrap(fixture,true));assertEquals(uri,StreamCodec.unwrap(uri,false));
        try{StreamCodec.unwrap("not-a-url",false);fail("Invalid URI accepted");}catch(IllegalArgumentException expected){}
    }
}
