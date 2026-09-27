import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;

/** Makes the host update policy permanently optional without touching content authentication. */
public final class ProHostPolicy {
    private static Method disabled(Method method){
        return new ImmutableMethod(method.getDefiningClass(),method.getName(),method.getParameters(),method.getReturnType(),
                method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),
                new ImmutableMethodImplementation(1,List.of(new BuilderInstruction11n(Opcode.CONST_4,0,0),
                        new BuilderInstruction11x(Opcode.RETURN,0)),List.of(),List.of()));
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[1]);Files.createDirectories(out);int patched=0,dexes=0;
        try(ZipFile zip=new ZipFile(args[0])){
            for(ZipEntry entry:Collections.list(zip.entries()))if(entry.getName().matches("classes\\d*\\.dex")){
                DexBackedDexFile dex;try(InputStream in=new BufferedInputStream(zip.getInputStream(entry))){dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in);}
                List<ClassDef> classes=new ArrayList<>();boolean changed=false;
                for(ClassDef cls:dex.getClasses()){
                    if(!cls.getType().equals("Lcom/drama/mp4/data/model/AppUpdate;")){classes.add(cls);continue;}
                    List<Method> methods=new ArrayList<>();
                    for(Method method:cls.getMethods()){
                        if((method.getName().equals("getUpdateAvailable")||method.getName().equals("isMandatory"))&&method.getParameterTypes().isEmpty()&&method.getReturnType().equals("Z")){
                            methods.add(disabled(method));patched++;changed=true;
                        }else methods.add(method);
                    }
                    classes.add(new ImmutableClassDef(cls.getType(),cls.getAccessFlags(),cls.getSuperclass(),cls.getInterfaces(),cls.getSourceFile(),cls.getAnnotations(),cls.getFields(),methods));
                }
                if(changed)DexPool.writeTo(out.resolve(entry.getName()).toString(),new ImmutableDexFile(dex.getOpcodes(),classes));
                dexes++;
            }
        }
        if(patched!=2)throw new IllegalStateException("Expected two update-policy methods, got "+patched);
        System.out.println("{\"dex_files\":"+dexes+",\"disabled_update_policy_methods\":"+patched+"}");
    }
}
