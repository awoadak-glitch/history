package com.atheer.shell;
import android.app.*;
import android.content.*;
import android.os.Bundle;
/** Keeps the supplied startup superclass intact; routes only bundled feature activities. */
public final class MergeFactory extends com.pandora.core.AppFactory {
    @Override public Application instantiateApplication(ClassLoader cl,String name)
            throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        Application app=super.instantiateApplication(cl,name);
        ModuleRuntime.host=app;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){if(a.getClass().getName().equals(ModuleRuntime.MAIN))Host.attach(a);}
            public void onActivityResumed(Activity a){if(a.getClass().getName().equals(ModuleRuntime.MAIN))Host.attach(a);}
            public void onActivityStarted(Activity a){}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){Host.detach(a);}
        });
        return app;
    }
    @Override public Activity instantiateActivity(ClassLoader cl,String name,Intent intent)
            throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        if(ModuleRuntime.owns(name)){
            try {ModuleRuntime.prepare();ModuleRuntime.boot();if(intent!=null)intent.setExtrasClassLoader(ModuleRuntime.loader);return super.instantiateActivity(ModuleRuntime.loader,name,intent);}
            catch(Exception e){InstantiationException failure=new InstantiationException("Source World: "+e);failure.initCause(e);throw failure;}
        }
        return super.instantiateActivity(cl,name,intent);
    }
}
