import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Relocate original bytecode types, including dependencies, without recompiling sources. */
public class RelocateDex {
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("drama.apk output-dir providers.tsv");
        Map<String,ClassDef> owned=new HashMap<>();List<DexFile> dexes=new ArrayList<>();
        try(ZipFile zip=new ZipFile(args[0])){
            List<? extends ZipEntry> entries=Collections.list(zip.entries());entries.sort(Comparator.comparing(ZipEntry::getName));
            for(ZipEntry e:entries)if(e.getName().matches("classes[0-9]*\\.dex")){
                DexFile dex;try(InputStream in=new BufferedInputStream(zip.getInputStream(e))){dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in);}
                dexes.add(dex);for(ClassDef c:dex.getClasses())if(owned.put(c.getType(),c)!=null)throw new IllegalStateException("Duplicate original type");
            }
        }
        for(String row:Files.readAllLines(Paths.get(args[2]))){
            String[] p=row.split("\t");ClassDef c=owned.get("L"+p[1].replace('.','/')+";");if(c==null)throw new IllegalStateException("Missing provider "+p[0]);
            boolean found=false;
            for(Method m:c.getMethods())if(m.getName().equals(p[2])&&m.getParameterTypes().toString().equals(p[3].equals("1")?"[Ljava/lang/String;, LQ0/b$b;, Landroid/content/Context;]":"[Ljava/lang/String;, LQ0/b$b;]"))found=true;
            if(!found)throw new IllegalStateException("Wrong extractor method signature: "+row);
        }
        DexRewriter rewriter=new DexRewriter(new RewriterModule(){
            @Override public Rewriter<String> getTypeRewriter(Rewriters ignored){return type->{
                int n=0;while(n<type.length()&&type.charAt(n)=='[')n++;
                String component=type.substring(n);return owned.containsKey(component)?type.substring(0,n)+"Lawr/legacy/"+component.substring(1):type;
            };}
        });
        Path output=Paths.get(args[1]);Files.createDirectories(output);int i=0;
        for(DexFile dex:dexes)DexPool.writeTo(output.resolve("legacy"+(++i)+".dex").toString(),rewriter.getDexFileRewriter().rewrite(dex));
        System.out.println("Validated 68 entry points; relocated "+owned.size()+" classes in "+dexes.size()+" DEX files");
    }
}
