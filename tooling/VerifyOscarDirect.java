import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Structural proof for the direct-Dex Oscar/AWR integration. */
public class VerifyOscarDirect {
    static final class DexIndex {
        final Map<String,String> owners=new TreeMap<>();
        final Map<String,ClassDef> classes=new TreeMap<>();
        final Map<String,List<ClassDef>> byEntry=new TreeMap<>();
    }

    static DexIndex read(String apk) throws Exception {
        DexIndex index=new DexIndex();
        try(ZipFile zip=new ZipFile(apk)) {
            for(ZipEntry entry:Collections.list(zip.entries())) {
                if(!entry.getName().matches("classes\\d*\\.dex"))continue;
                DexBackedDexFile dex;
                try(InputStream in=new BufferedInputStream(zip.getInputStream(entry))) {
                    dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in);
                }
                List<ClassDef> current=new ArrayList<>();
                for(ClassDef item:dex.getClasses()) {
                    String previous=index.owners.put(item.getType(),entry.getName());
                    if(previous!=null)throw new IllegalStateException("Duplicate class "+item.getType()+" in "+previous+" and "+entry.getName());
                    index.classes.put(item.getType(),item);
                    current.add(item);
                }
                index.byEntry.put(entry.getName(),current);
            }
        }
        return index;
    }

    static String hash(byte[] value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder out=new StringBuilder();
        for(byte b:digest)out.append(String.format("%02x",b));
        return out.toString();
    }

    static boolean signature(String name) {
        return name.startsWith("META-INF/")&&name.matches("(?i).+\\.(RSA|DSA|EC|SF|MF)$");
    }

    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("original.apk direct.apk");
        DexIndex original=read(args[0]),direct=read(args[1]);
        if(direct.byEntry.size()!=10)throw new IllegalStateException("Expected 10 outer DEX files, got "+direct.byEntry.size());
        try(ZipFile before=new ZipFile(args[0]);ZipFile after=new ZipFile(args[1])) {
            for(String name:List.of("classes.dex","classes2.dex","classes3.dex")) {
                if(!Arrays.equals(before.getInputStream(before.getEntry(name)).readAllBytes(),after.getInputStream(after.getEntry(name)).readAllBytes()))
                    throw new IllegalStateException("Original host DEX changed: "+name);
            }
            byte[] oldBase=before.getInputStream(before.getEntry("assets/base.apk")).readAllBytes();
            byte[] newBase=after.getInputStream(after.getEntry("assets/base.apk")).readAllBytes();
            if(!Arrays.equals(oldBase,newBase))throw new IllegalStateException("assets/base.apk changed");
            if(!hash(newBase).equals("e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7"))
                throw new IllegalStateException("Unexpected assets/base.apk");
            for(ZipEntry entry:Collections.list(after.entries())) {
                String name=entry.getName().toLowerCase();
                if(name.endsWith(".apk")&&!name.equals("assets/base.apk"))throw new IllegalStateException("Added nested APK: "+entry.getName());
                if(name.startsWith("assets/atheer/"))throw new IllegalStateException("Runtime feature archive remains: "+entry.getName());
            }
        }
        for(String required:List.of(
                "Lcom/atheer/shell/MergeFactory;",
                "Lcom/atheer/shell/DirectSources;",
                "Lcom/atheer/shell/Host;",
                "Lawr/witcher/AnimeActivity;",
                "Lawr/witcher/AnimeApi;",
                "Lawr/witcher/DramaActivity;",
                "Lawr/witcher/Media;",
                "Lawr/witcher/EmbedPlayer;",
                "Lawr/witcher/DownloadFlow;",
                "Lawr/legacy/a1/a;"
        ))if(!direct.classes.containsKey(required))throw new IllegalStateException("Missing "+required);
        for(String rejected:List.of(
                "Lcom/atheer/shell/ModuleRuntime;",
                "Lcom/atheer/shell/SourcesFileProvider;",
                "Lcom/atheer/shell/SourceDiscoveryService;",
                "Lawr/witcher/HitvExperience;",
                "Lawr/witcher/OscarExperience;"
        ))if(direct.classes.containsKey(rejected))throw new IllegalStateException("Rejected architecture class remains: "+rejected);
        if(!"classes4.dex".equals(direct.owners.get("Lawr/witcher/DramaActivity;")))
            throw new IllegalStateException("Direct UI is not in classes4.dex");
        int legacy=0;
        for(String type:direct.classes.keySet())if(type.startsWith("Lawr/legacy/"))legacy++;
        if(legacy<1000)throw new IllegalStateException("AWR extractor graph is incomplete: "+legacy);
        boolean mx=false,animeGateway=false;
        Set<String> forbiddenTypes=new HashSet<>(List.of("Ldalvik/system/DexClassLoader;","Ldalvik/system/PathClassLoader;"));
        for(ClassDef item:direct.byEntry.get("classes4.dex")) {
            String lower=item.getType().toLowerCase();
            if(lower.contains("hitv")||lower.contains("oscarexperience"))throw new IllegalStateException("Unwanted source class "+item.getType());
            for(Method method:item.getMethods())if(method.getImplementation()!=null)for(Instruction instruction:method.getImplementation().getInstructions()) {
                if(!(instruction instanceof ReferenceInstruction))continue;
                Reference reference=((ReferenceInstruction)instruction).getReference();
                if(reference instanceof TypeReference&&forbiddenTypes.contains(((TypeReference)reference).getType()))
                    throw new IllegalStateException("Runtime DEX loader reference in "+item.getType());
                String owner=null;
                if(reference instanceof MethodReference)owner=((MethodReference)reference).getDefiningClass();
                if(reference instanceof FieldReference)owner=((FieldReference)reference).getDefiningClass();
                if(owner!=null&&(owner.startsWith("Lawr/")||owner.startsWith("Lcom/atheer/")||owner.startsWith("Lcom/pandora/"))
                        &&!direct.classes.containsKey(owner))
                    throw new IllegalStateException("Unresolved internal owner "+owner+" from "+item.getType());
                if(reference instanceof StringReference) {
                    String value=((StringReference)reference).getString();
                    if(value.equals("com.mxtech.videoplayer.ad"))mx=true;
                    if(value.equals("https://awr-stream-web.vercel.app/api/"))animeGateway=true;
                    if(value.endsWith(".apk")||value.contains("source-code.jar")||value.contains("source-resources.pack"))
                        throw new IllegalStateException("Runtime feature payload reference: "+value);
                }
            }
        }
        if(!mx)throw new IllegalStateException("MX Player package is absent");
        if(!animeGateway)throw new IllegalStateException("Anime Witcher gateway is absent");
        System.out.println("{\"outer_dex_files\":"+direct.byEntry.size()
                +",\"original_host_classes\":"+original.classes.size()
                +",\"direct_feature_classes\":"+direct.byEntry.get("classes4.dex").size()
                +",\"relocated_awr_classes\":"+legacy
                +",\"assets_base_apk_unchanged\":true"
                +",\"no_added_nested_apk\":true"
                +",\"no_runtime_dex_loader\":true"
                +",\"mx_player\":true"
                +",\"anime_witcher_gateway\":true}");
    }
}
