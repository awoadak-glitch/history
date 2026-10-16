package com.palma.vision.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;

public class MainActivity extends Activity {
  private static final int PORT=39841;
  private static volatile boolean started=false,ready=false,failed=false;
  private static String token;
  private static native int startNode(String[] arguments);
  private final Handler ui=new Handler(Looper.getMainLooper());
  private WebView web;private FrameLayout frame;private View fullView;private WebChromeClient.CustomViewCallback fullCallback;
  private boolean tests=false;
  private ValueCallback<Uri[]> fileCallback;private String backup;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);tests=getIntent().getBooleanExtra("selfTest",false);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);immersive();
    frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(12,20,34));setContentView(frame);
    TextView loading=new TextView(this);loading.setText("PALMA TV\nجاري تجهيز التطبيق…");loading.setTextColor(Color.rgb(216,186,120));loading.setTextSize(26);loading.setGravity(17);frame.addView(loading,new FrameLayout.LayoutParams(-1,-1));
    synchronized(MainActivity.class){if(!started){started=true;byte[] random=new byte[32];new SecureRandom().nextBytes(random);StringBuilder hex=new StringBuilder();for(byte b:random)hex.append(String.format("%02x",b&255));token=hex.toString();new Thread(()->startRuntime(),"palma-runtime").start();}}
    waitUntilReady(0);
  }
  private void startRuntime(){
    try{
      File root=new File(getFilesDir(),"runtime");copyAssets("runtime",root);
      System.loadLibrary("node");System.loadLibrary("palma_node");
      startNode(new String[]{"node",new File(root,"main.mjs").getAbsolutePath(),Integer.toString(PORT),token,new File(getFilesDir(),"saved-media").getAbsolutePath(),tests?"test":"app"});
      failed=true;
    }catch(Throwable error){failed=true;android.util.Log.e("PALMA-TV","Runtime initialization failed: "+error.getClass().getSimpleName());}
  }
  private void copyAssets(String asset,File target)throws IOException{
    String[] children=getAssets().list(asset);
    if(children!=null&&children.length>0){if(!target.exists()&&!target.mkdirs())throw new IOException("directory");for(String name:children)copyAssets(asset+"/"+name,new File(target,name));}
    else {try(InputStream input=getAssets().open(asset);OutputStream output=new FileOutputStream(target)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))>0)output.write(buffer,0,n);}}
  }
  private void waitUntilReady(int attempts){
    if(isFinishing())return;
    if(failed||attempts>100){failure("تعذر بدء التطبيق. أغلقه وافتحه مجددًا. يحتاج PALMA TV إلى Android 7 أو أحدث وAndroid System WebView.");return;}
    new Thread(()->{
      boolean ok=false;try{HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+PORT+"/health").openConnection();c.setConnectTimeout(300);c.setReadTimeout(300);c.setRequestProperty("Authorization","Bearer "+token);ok=c.getResponseCode()==200;c.disconnect();}catch(Exception ignored){}
      final boolean connected=ok;ui.post(()->{if(connected){ready=true;openWeb();}else ui.postDelayed(()->waitUntilReady(attempts+1),250);});
    },"palma-ready").start();
  }
  private boolean local(Uri url){return "http".equals(url.getScheme())&&"127.0.0.1".equals(url.getHost())&&url.getPort()==PORT;}
  private void openWeb(){
    if(isFinishing()||web!=null)return;
    try{web=new WebView(this);}catch(Throwable e){failure("حدّث Android System WebView من متجر جهازك ثم أعد فتح بالما.");return;}
    WebView.setWebContentsDebuggingEnabled(false);
    WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setUserAgentString(settings.getUserAgentString()+" PALMA-TV/1.0.0");settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setTextZoom(100);
    CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
    web.setBackgroundColor(Color.rgb(12,20,34));
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri uri=request.getUrl();if(local(uri))return false;if(request.isForMainFrame()&&request.hasGesture()&&"https".equals(uri.getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){Toast.makeText(MainActivity.this,"لا يوجد تطبيق لفتح هذا الرابط",Toast.LENGTH_SHORT).show();}}return true;}
      @Override public void onPageFinished(WebView view,String url){if(local(Uri.parse(url))){CookieManager.getInstance().flush();view.requestFocus();}}
      @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){view.destroy();web=null;failure("توقفت واجهة التلفاز. أغلق التطبيق وافتحه مجددًا.");return true;}
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullView!=null){callback.onCustomViewHidden();return;}fullView=view;fullCallback=callback;frame.addView(view,new FrameLayout.LayoutParams(-1,-1));immersive();}
      @Override public void onHideCustomView(){if(fullView!=null){frame.removeView(fullView);fullView=null;fullCallback.onCustomViewHidden();fullCallback=null;immersive();}}
      @Override public boolean onConsoleMessage(ConsoleMessage message){if(tests&&message.message().startsWith("PALMA_TV_TEST "))android.util.Log.i("PALMA-TV-TEST",message.message().substring(14));return true;}
      @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
      @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");startActivityForResult(intent,21);}catch(Exception error){fileCallback.onReceiveValue(null);fileCallback=null;Toast.makeText(MainActivity.this,"لا يوجد منتقي ملفات على هذا الجهاز",Toast.LENGTH_LONG).show();}return true;}
    });
    web.addJavascriptInterface(new Object(){
      @JavascriptInterface public void exit(){ui.post(()->{if(web!=null&&local(Uri.parse(web.getUrl())))finish();});}
      @JavascriptInterface public void exportLibrary(String data){if(data==null||data.length()>4*1024*1024)return;ui.post(()->{if(web==null||!local(Uri.parse(web.getUrl())))return;backup=data;try{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"palma-tv-library.json");startActivityForResult(intent,22);}catch(Exception error){backup=null;Toast.makeText(MainActivity.this,"لا يوجد منتقي ملفات على هذا الجهاز",Toast.LENGTH_LONG).show();}});}
    },"PalmaTVNative");
    frame.removeAllViews();frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
    web.loadUrl("http://127.0.0.1:"+PORT+"/?session="+token+"#/home");
  }
  private void failure(String text){frame.removeAllViews();TextView message=new TextView(this);message.setText(text);message.setTextColor(Color.WHITE);message.setTextSize(22);message.setGravity(17);message.setPadding(40,30,40,30);frame.addView(message,new FrameLayout.LayoutParams(-1,-1));}
  private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
  @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
  @Override public void onBackPressed(){if(fullView!=null){frame.removeView(fullView);fullView=null;if(fullCallback!=null)fullCallback.onCustomViewHidden();return;}if(web!=null){web.evaluateJavascript("window.PalmaTV && window.PalmaTV.back()",null);}else finish();}
  @Override public boolean dispatchKeyEvent(KeyEvent event){
    int key=event.getKeyCode();
    if(web!=null&&(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND)){
      if(event.getAction()==KeyEvent.ACTION_DOWN)web.evaluateJavascript("window.PalmaTV && window.PalmaTV.mediaKey("+key+")",null);return true;
    }
    return super.dispatchKeyEvent(event);
  }
  @Override protected void onPause(){if(web!=null){web.evaluateJavascript("document.querySelector('#player-dialog video')?.pause()",null);CookieManager.getInstance().flush();}super.onPause();}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==21&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileCallback=null;}if(request==22){String value=backup;backup=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&value!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(value.getBytes("UTF-8"));Toast.makeText(this,"تم حفظ بيانات بالما",Toast.LENGTH_SHORT).show();}catch(Exception error){Toast.makeText(this,"تعذر حفظ الملف",Toast.LENGTH_SHORT).show();}}}}
  @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
