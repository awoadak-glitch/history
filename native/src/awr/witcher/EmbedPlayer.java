package awr.witcher;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;

/** Exact playback path for Drama World's `embed` source type: a JavaScript WebView, not DW Player. */
final class EmbedPlayer {
    private EmbedPlayer(){}

    static void open(Activity activity,String url,String referer,Map<String,String> inherited){
        if(activity.isFinishing()||activity.isDestroyed())return;
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root=new FrameLayout(activity);root.setBackgroundColor(Color.BLACK);dialog.setContentView(root);
        WebView web=new WebView(activity);root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout custom=new FrameLayout(activity);custom.setBackgroundColor(Color.BLACK);custom.setVisibility(View.GONE);root.addView(custom,new FrameLayout.LayoutParams(-1,-1));
        final WebChromeClient.CustomViewCallback[] customCallback={null};

        CookieManager cm=CookieManager.getInstance();cm.setAcceptCookie(true);if(Build.VERSION.SDK_INT>=21)cm.setAcceptThirdPartyCookies(web,true);
        String cookie=value(inherited,"Cookie");if(!cookie.isEmpty())try{for(String p:cookie.split(";"))cm.setCookie(url,p.trim());cm.flush();}catch(Exception ignored){}

        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setBuiltInZoomControls(false);s.setSaveFormData(true);s.setAllowFileAccess(true);s.setJavaScriptCanOpenWindowsAutomatically(true);s.setSupportMultipleWindows(true);
        if(Build.VERSION.SDK_INT>=21)s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        try{if(Build.VERSION.SDK_INT>=26)s.setSafeBrowsingEnabled(true);}catch(Exception ignored){}

        class Retry {
            @JavascriptInterface public void retryLoad(){activity.runOnUiThread(()->load(web,url,referer));}
        }
        web.addJavascriptInterface(new Retry(),"AndroidRetry");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String next){return next!=null&&next.startsWith("intent:");}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){String next=req.getUrl().toString();return next.startsWith("intent:");}
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error){if(Build.VERSION.SDK_INT>=23&&req.isForMainFrame())errorPage(view,"تعذر تحميل الفيديو. تحقق من الاتصال ثم أعد المحاولة.");}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,WebResourceResponse response){if(Build.VERSION.SDK_INT>=21&&req.isForMainFrame()&&response.getStatusCode()>=400)errorPage(view,"تعذر تحميل الفيديو الآن ("+response.getStatusCode()+").");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(customCallback[0]!=null){callback.onCustomViewHidden();return;}customCallback[0]=callback;web.setVisibility(View.GONE);custom.removeAllViews();custom.addView(view,new FrameLayout.LayoutParams(-1,-1));custom.setVisibility(View.VISIBLE);
            }
            @Override public void onHideCustomView(){
                if(customCallback[0]==null)return;custom.setVisibility(View.GONE);custom.removeAllViews();web.setVisibility(View.VISIBLE);customCallback[0].onCustomViewHidden();customCallback[0]=null;
            }
            @Override public View getVideoLoadingProgressView(){ProgressBar p=new ProgressBar(activity);p.setIndeterminate(true);return p;}
        });

        dialog.setOnDismissListener(d->{try{web.stopLoading();web.loadUrl("about:blank");web.removeAllViews();web.destroy();}catch(Exception ignored){}});
        dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){if(customCallback[0]!=null){web.getWebChromeClient().onHideCustomView();return true;}dialog.dismiss();return true;}return false;});
        dialog.show();load(web,url,referer);
    }

    private static void load(WebView web,String url,String referer){Map<String,String> h=new HashMap<>();if(referer!=null&&referer.startsWith("http"))h.put("Referer",referer);web.loadUrl(url,h);}
    private static String value(Map<String,String> h,String key){if(h!=null)for(Map.Entry<String,String> e:h.entrySet())if(key.equalsIgnoreCase(e.getKey()))return e.getValue()==null?"":e.getValue();return "";}
    private static void errorPage(WebView web,String text){String html="<html dir='rtl'><body style='background:#111;color:white;text-align:center;font-family:sans-serif;margin-top:50px'><h2>Oops!</h2><p>"+text+"</p><button onclick=\"AndroidRetry.retryLoad()\" style='font-size:16px;padding:10px 20px;margin-top:20px'>إعادة المحاولة</button></body></html>";web.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);}
}
