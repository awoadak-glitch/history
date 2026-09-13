import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Verify the reflection contract against the actual user APK, not Maven substitutes. */
public class VerifyHitvContracts {
    static final Map<String,ClassDef> classes=new HashMap<>();
    static final String P="Lcom/google/android/exoplayer2/";
    static void method(String owner,String name,String... params){
        ClassDef c=classes.get(owner);if(c==null)throw new IllegalStateException("Missing class "+owner);
        boolean found=false;
        for(Method m:c.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().toString().equals(Arrays.asList(params).toString())){
            if((m.getAccessFlags()&1)==0)throw new IllegalStateException("Method is not public: "+owner+" "+name);
            found=true;break;
        }
        if(!found)throw new IllegalStateException("Missing method "+owner+" "+name+Arrays.toString(params));
    }
    public static void main(String[] args)throws Exception{
        try(ZipFile z=new ZipFile(args[0])){for(ZipEntry e:Collections.list(z.entries()))if(e.getName().matches("classes\\d*\\.dex")){
            DexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));
            for(ClassDef c:dex.getClasses())if(classes.put(c.getType(),c)!=null)throw new IllegalStateException("Duplicate class "+c.getType());
        }}
        method(P+"ui/PlayerView;","<init>","Landroid/content/Context;");method(P+"ui/PlayerView;","setPlayer",P+"Player;");
        method(P+"ExoPlayer$Builder;","<init>","Landroid/content/Context;");method(P+"ExoPlayer$Builder;","build");
        method(P+"ExoPlayer$Builder;","setAudioAttributes",P+"audio/AudioAttributes;","Z");method(P+"ExoPlayer$Builder;","setHandleAudioBecomingNoisy","Z");
        method(P+"ExoPlayer$Builder;","setMediaSourceFactory",P+"source/MediaSource$Factory;");
        method(P+"source/DefaultMediaSourceFactory;","<init>",P+"upstream/DataSource$Factory;");
        method(P+"upstream/DefaultHttpDataSource$Factory;","<init>");method(P+"upstream/DefaultHttpDataSource$Factory;","setDefaultRequestProperties","Ljava/util/Map;");
        for(String m:new String[]{"prepare","release","getTrackSelectionParameters"})method(P+"Player;",m);
        method(P+"Player;","setMediaItem",P+"MediaItem;");method(P+"Player;","setPlayWhenReady","Z");method(P+"Player;","setPlaybackSpeed","F");
        method(P+"Player;","addListener",P+"Player$Listener;");method(P+"Player;","setTrackSelectionParameters",P+"trackselection/TrackSelectionParameters;");
        method(P+"MediaItem$Builder;","<init>");method(P+"MediaItem$Builder;","setUri","Ljava/lang/String;");method(P+"MediaItem$Builder;","setMimeType","Ljava/lang/String;");
        method(P+"MediaItem$Builder;","setSubtitleConfigurations","Ljava/util/List;");method(P+"MediaItem$Builder;","build");
        method(P+"MediaItem$SubtitleConfiguration$Builder;","<init>","Landroid/net/Uri;");
        for(String m:new String[]{"setLabel","setLanguage","setMimeType"})method(P+"MediaItem$SubtitleConfiguration$Builder;",m,"Ljava/lang/String;");
        method(P+"MediaItem$SubtitleConfiguration$Builder;","setSelectionFlags","I");method(P+"MediaItem$SubtitleConfiguration$Builder;","build");
        method(P+"trackselection/TrackSelectionParameters;","buildUpon");
        method(P+"trackselection/TrackSelectionParameters$Builder;","setTrackTypeDisabled","I","Z");method(P+"trackselection/TrackSelectionParameters$Builder;","setPreferredTextLanguage","Ljava/lang/String;");method(P+"trackselection/TrackSelectionParameters$Builder;","build");
        System.out.println("HiTV adapter reflection contracts verified against user APK; no duplicate DEX classes ("+classes.size()+" classes).");
    }
}
