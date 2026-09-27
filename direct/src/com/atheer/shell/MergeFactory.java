package com.atheer.shell;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Preserves the supplied Pro AppFactory and only attaches a host navigation observer. */
public final class MergeFactory extends com.pandora.core.AppFactory {
    @Override public Application instantiateApplication(ClassLoader loader,String name)
            throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        Application app=super.instantiateApplication(loader,name);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity activity,Bundle state) { if(DirectSources.MAIN.equals(activity.getClass().getName()))Host.attach(activity); }
            public void onActivityResumed(Activity activity) { if(DirectSources.MAIN.equals(activity.getClass().getName()))Host.attach(activity); }
            public void onActivityStarted(Activity activity) {}
            public void onActivityPaused(Activity activity) {}
            public void onActivityStopped(Activity activity) {}
            public void onActivitySaveInstanceState(Activity activity,Bundle state) {}
            public void onActivityDestroyed(Activity activity) { Host.detach(activity); }
        });
        return app;
    }
}
