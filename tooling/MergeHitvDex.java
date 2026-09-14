import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Keep approved integration bytecode; replace only HiTV and its tab/image hooks. */
public class MergeHitvDex {
    static boolean changed(String type) {
        return type.startsWith("Lawr/witcher/Hitv") || type.equals("Lawr/witcher/Ui;") ||
            type.startsWith("Lawr/witcher/Ui$") || type.equals("Lawr/witcher/WitcherTabs;") ||
            type.startsWith("Lawr/witcher/WitcherTabs$");
    }
    static DexFile read(InputStream in) throws IOException {
        try(InputStream stream=new BufferedInputStream(in)) {
            return DexBackedDexFile.fromInputStream(Opcodes.getDefault(),stream);
        }
    }
    static void write(Path path,Collection<? extends ClassDef> classes)throws IOException {
        DexPool.writeTo(path.toString(),new ImmutableDexFile(Opcodes.getDefault(),classes));
    }
    static String sha(byte[] bytes)throws Exception {
        StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("approved.apk compiled.dex output.dex report.json");
        SortedMap<String,ClassDef> original=new TreeMap<>(), retained=new TreeMap<>(), merged=new TreeMap<>();
        try(ZipFile apk=new ZipFile(args[0])) {
            ZipEntry entry=apk.getEntry("classes29.dex");
            if(entry==null)throw new IllegalArgumentException("Approved APK lacks classes29.dex");
            for(ClassDef c:read(apk.getInputStream(entry)).getClasses())original.put(c.getType(),c);
        }
        if(!original.containsKey("Lawr/witcher/DramaActivity;")||!original.containsKey("Lawr/witcher/Media;"))throw new IllegalArgumentException("Unexpected integration DEX");
        for(ClassDef c:original.values())if(!changed(c.getType()))retained.put(c.getType(),c);
        merged.putAll(retained);int added=0;
        for(ClassDef c:read(Files.newInputStream(Paths.get(args[1]))).getClasses())if(changed(c.getType())){merged.put(c.getType(),c);added++;}
        if(!merged.containsKey("Lawr/witcher/HitvExperience;")||!merged.containsKey("Lawr/witcher/HitvPlayer;"))throw new IllegalArgumentException("Missing HiTV implementation");
        Path output=Paths.get(args[2]);write(output,merged.values());
        SortedMap<String,ClassDef> restored=new TreeMap<>();
        for(ClassDef c:read(Files.newInputStream(output)).getClasses())if(!changed(c.getType()))restored.put(c.getType(),c);
        Path before=Files.createTempFile("awr-approved-", ".dex"), after=Files.createTempFile("awr-preserved-", ".dex");
        String canonical;
        try {
            write(before,retained.values());write(after,restored.values());
            byte[] oldBytes=Files.readAllBytes(before), newBytes=Files.readAllBytes(after);
            if(!Arrays.equals(oldBytes,newBytes))throw new IllegalStateException("Existing integration bytecode changed");
            canonical=sha(oldBytes);
        } finally {Files.deleteIfExists(before);Files.deleteIfExists(after);}
        String report="{\n  \"preserved_integration_classes\": "+retained.size()+",\n  \"new_or_replaced_hitv_and_hook_classes\": "+added+",\n  \"preserved_classes_canonical_dex_sha256\": \""+canonical+"\",\n  \"preserved_classes_canonical_dex_identical\": true,\n  \"merged_dex_sha256\": \""+sha(Files.readAllBytes(output))+"\"\n}\n";
        Files.write(Paths.get(args[3]),report.getBytes("UTF-8"));System.out.print(report);
    }
}
