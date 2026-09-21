package com.atheer.shell;
import android.app.*;import android.content.*;import java.lang.reflect.*;
/** Manifest components stay in one Android application; only feature activities use its isolated loader. */
public final class MergeFactory extends AppComponentFactory {
    private AppComponentFactory original(ClassLoader cl){try{return (AppComponentFactory)cl.loadClass("androidx.core.app.CoreComponentFactory").getConstructor().newInstance();}catch(Exception e){return new AppComponentFactory();}}
    @Override public Application instantiateApplication(ClassLoader cl,String name)throws InstantiationException,IllegalAccessException,ClassNotFoundException {Application a=original(cl).instantiateApplication(cl,name);ModuleRuntime.host=a;return a;}
    @Override public Activity instantiateActivity(ClassLoader cl,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        if(ModuleRuntime.owns(name))try {ModuleRuntime.prepare();ModuleRuntime.boot();return super.instantiateActivity(ModuleRuntime.loader,name,intent);}catch(Exception e){throw new InstantiationException("Cannot load Source World: "+e.getClass().getSimpleName());}
        return original(cl).instantiateActivity(cl,name,intent);
    }
    @Override public Service instantiateService(ClassLoader cl,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return original(cl).instantiateService(cl,name,intent);}
    @Override public BroadcastReceiver instantiateReceiver(ClassLoader cl,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return original(cl).instantiateReceiver(cl,name,intent);}
    @Override public ContentProvider instantiateProvider(ClassLoader cl,String name)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return original(cl).instantiateProvider(cl,name);}
}
