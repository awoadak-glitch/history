package com.atheer.shell;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Isolated, bundled feature module; never installs or launches a second APK. */
public final class ModuleRuntime {
    static Application host, guest;
    static ClassLoader loader;
    static AssetManager assets;
    static File module;
    static boolean opening;
    static boolean booting;
    static final String HOME="com.example.animewitcher.HomeActivity";
    static final String MAIN="com.drama.mp4.ui.main.MainActivity";
    static final int MARKER_THEME=android.R.style.Theme_Material_NoActionBar;
    private static final java.util.concurrent.ExecutorService IO=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final Map<String,Integer> THEMES=new HashMap<>();
    static { for(String row:ModuleConfig.ACTIVITIES){String[] p=row.split("=");THEMES.put(p[0],Integer.parseUnsignedInt(p[1],16));} }
    public static boolean owns(String name){return THEMES.containsKey(name);}
    public static int theme(Activity activity,int requested){return requested==MARKER_THEME&&THEMES.containsKey(activity.getClass().getName())?THEMES.get(activity.getClass().getName()):requested;}
    public static void open(Activity activity){
        if(opening)return;
        if(Build.VERSION.SDK_INT<28){error(activity,"تحتاج هذه النسخة إلى Android 9 أو أحدث.");return;}
        opening=true;
        ProgressDialog wait=new ProgressDialog(activity);wait.setMessage("جاري فتح عالم المصادر…");wait.setCancelable(false);wait.show();
        IO.execute(()->{
            try { prepare(); activity.runOnUiThread(()->{
                try { boot();activity.startActivity(new Intent().setClassName(activity.getPackageName(),HOME)); }
                catch(Throwable e){error(activity,failure(e));}
                finally {opening=false;if(!activity.isFinishing())wait.dismiss();}
            }); } catch(Throwable e){activity.runOnUiThread(()->{opening=false;if(!activity.isFinishing()){wait.dismiss();error(activity,failure(e));}});}
        });
    }
    private static String failure(Throwable e){
        android.util.Log.e("Atheer", "Source World",e);
        Throwable root=e;for(int i=0;i<8&&root.getCause()!=null;i++)root=root.getCause();
        StringBuilder detail=new StringBuilder("تعذر فتح عالم المصادر.\n").append(root.getClass().getSimpleName());
        StackTraceElement[] stack=root.getStackTrace();for(int i=0;i<Math.min(4,stack.length);i++)detail.append("\n").append(stack[i].toString());
        return detail.toString();
    }
    private static void error(Activity a,String message){if(!a.isFinishing())new AlertDialog.Builder(a).setTitle("عالم المصادر").setMessage(message).setPositiveButton("حسنًا",null).show();}
    static synchronized void prepare()throws Exception{
        if(loader!=null)return;
        if(host==null)throw new IllegalStateException("Host application unavailable");
        File root=new File(host.getCodeCacheDir(),"atheer-"+ModuleConfig.SHA256.substring(0,16));
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Module directory");
        module=extract(root,"source-code.jar",ModuleConfig.SHA256);
        File resourcePack=extract(root,"source-resources.pack",ModuleConfig.RESOURCE_SHA256);
        File nativeDir=new File(root,"lib");if(!nativeDir.isDirectory()&&!nativeDir.mkdirs())throw new IOException("Native directory");
        try(ZipFile zip=new ZipFile(resourcePack)){
            String abi=null;for(String candidate:Build.SUPPORTED_ABIS)if(zip.getEntry("lib/"+candidate+"/libTheeInvisibleMan.so")!=null){abi=candidate;break;}
            if(abi==null)throw new IOException("Unsupported module ABI");
            String prefix="lib/"+abi+"/";
            for(ZipEntry e:Collections.list(zip.entries()))if(e.getName().startsWith(prefix)&&e.getName().endsWith(".so")){
                String leaf=e.getName().substring(prefix.length());if(leaf.contains("/"))throw new IOException("Library path");
                File library=new File(nativeDir,leaf);
                if(!library.exists())try(InputStream in=zip.getInputStream(e);FileOutputStream out=new FileOutputStream(library)){if(!library.setReadOnly())throw new IOException("Read-only library");copy(in,out);}
            }
        }
        AssetManager nextAssets=AssetManager.class.getConstructor().newInstance();
        int cookie=(Integer)AssetManager.class.getMethod("addAssetPath",String.class).invoke(nextAssets,resourcePack.getAbsolutePath());
        if(cookie==0)throw new IOException("Module resources");
        assets=nextAssets;
        loader=new FeatureLoader(module.getAbsolutePath(),root.getAbsolutePath(),nativeDir.getAbsolutePath(),ModuleRuntime.class.getClassLoader());
    }
    private static File extract(File root,String name,String checksum)throws Exception{
        File dest=new File(root,name);
        if(!dest.isFile()||!digest(dest).equals(checksum)){
            if(dest.exists()&&!dest.delete())throw new IOException("Stale feature file");
            try(InputStream in=host.getAssets().open("atheer/"+name);FileOutputStream out=new FileOutputStream(dest)){
                if(!dest.setReadOnly())throw new IOException("Read-only feature file");copy(in,out);
            }
            if(!digest(dest).equals(checksum)){dest.delete();throw new IOException("Feature checksum");}
        }
        return dest;
    }
    static synchronized void boot()throws Exception{
        prepare();if(guest!=null||booting)return;booting=true;
        try {
            android.content.SharedPreferences prefs=new Scope(host,null).getSharedPreferences("my_pref",0);
            if(!prefs.contains("default_appearance"))prefs.edit().putString("default_appearance","ليلي").apply();
            guest=new Instrumentation().newApplication(loader, "com.example.animewitcher.ApplicationClass",new Scope(host,null));
            guest.onCreate();
        } catch(Exception|LinkageError e){guest=null;throw e;} finally {booting=false;}
    }
    public static Context context(Context base){try{prepare();return base instanceof Scope?base:new Scope(base,null);}catch(Exception e){throw new IllegalStateException("Module context",e);}}
    public static void returnToMain(Context c){
        Intent intent=new Intent().setClassName(c.getPackageName(),MAIN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(!(c instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(intent);
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    private static String digest(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    static final class FeatureLoader extends DexClassLoader{
        FeatureLoader(String dex,String dir,String libs,ClassLoader parent){super(dex,dir,libs,parent);}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(name.startsWith("java.")||name.startsWith("javax.")||name.startsWith("android.")||name.startsWith("dalvik.")||name.startsWith("sun.")||name.startsWith("org.json.")||name.startsWith("org.xml.")||name.startsWith("org.w3c.")||name.startsWith("com.atheer.shell."))return super.loadClass(name,resolve);
            synchronized(this){Class<?> c=findLoadedClass(name);if(c==null)try{c=findClass(name);}catch(ClassNotFoundException missing){c=getParent().loadClass(name);}if(resolve)resolveClass(c);return c;}
        }
    }
    static final class ModuleResources extends Resources{
        ModuleResources(Context base,Configuration config){super(assets,base.getResources().getDisplayMetrics(),config==null?base.getResources().getConfiguration():config);}
        @Override public int getIdentifier(String name,String type,String pkg){int id=super.getIdentifier(name,type,pkg);return id!=0?id:super.getIdentifier(name,type,"com.anime.witcher");}
    }
    public static final class Scope extends ContextWrapper{
        final Resources resources;Resources.Theme theme;
        Scope(Context base,Configuration config){super(base);resources=new ModuleResources(base,config);}
        @Override public Resources getResources(){return resources;}
        @Override public AssetManager getAssets(){return assets;}
        @Override public ClassLoader getClassLoader(){return loader;}
        @Override public Context getApplicationContext(){return guest==null?this:guest;}
        @Override public Resources.Theme getTheme(){if(theme==null){theme=resources.newTheme();theme.applyStyle(ModuleConfig.DEFAULT_THEME,true);}return theme;}
        @Override public void setTheme(int id){getTheme().applyStyle(id,true);}
        @Override public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name))return LayoutInflater.from(getBaseContext()).cloneInContext(this);return super.getSystemService(name);}
        @Override public Context createConfigurationContext(Configuration config){return new Scope(getBaseContext(),config);}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("atheer_sources_"+name,mode);}
    }
}
