package com.atheer.shell;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Metadata-only entry for the embedded Firebase SDK's own component registrars. */
public final class SourceDiscoveryService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
}
