import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
/** Changes only AWR presentation literals, leaving providers and original native methods intact. */
public class BrandSourceDex {
 static final Map<Integer,Integer> COLORS=Map.of(0xffeec60a,0xffd8b56a,0xff101116,0xff0d111b,0xff1b1d25,0xff171f2c,0xfff5f5f7,0xfff6f0e5,0xffacb1c0,0xffacb7c9,0x18eec60a,0x28d8b56a,0x30eec60a,0x30d8b56a);
 public static void main(String[] a)throws Exception{
  Path out=Path.of(a[1]);Files.createDirectories(out);int literals=0,count=0;
  try(ZipFile z=new ZipFile(a[0])){for(ZipEntry e:Collections.list(z.entries()))if(e.getName().matches("classes\\d*\\.dex")){
   DexBackedDexFile d;try(InputStream in=new BufferedInputStream(z.getInputStream(e))){d=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in);}
   List<ClassDef> classes=new ArrayList<>();boolean changed=false;
   for(ClassDef c:d.getClasses()){
    if(!c.getType().startsWith("Lawr/witcher/")){classes.add(c);continue;}
    List<Method> methods=new ArrayList<>();boolean editClass=false;
    for(Method m:c.getMethods()){
     if(m.getImplementation()==null){methods.add(m);continue;}
     MutableMethodImplementation body=new MutableMethodImplementation(m.getImplementation());boolean edit=false;
     for(int i=0;i<body.getInstructions().size();i++){
      Instruction ins=body.getInstructions().get(i);
      if(ins instanceof NarrowLiteralInstruction&&ins instanceof OneRegisterInstruction&&ins.getOpcode().name().startsWith("CONST")){
       Integer color=COLORS.get(((NarrowLiteralInstruction)ins).getNarrowLiteral());
       if(color!=null){body.replaceInstruction(i,new BuilderInstruction31i(Opcode.CONST,((OneRegisterInstruction)ins).getRegisterA(),color));edit=true;literals++;}
      }
      if(ins instanceof ReferenceInstruction&&((ReferenceInstruction)ins).getReference() instanceof StringReference){
       String val=((StringReference)((ReferenceInstruction)ins).getReference()).getString();
       if(val.equals("AWR WORLD / عالم المصادر")){
        body.replaceInstruction(i,new BuilderInstruction21c(Opcode.CONST_STRING,((OneRegisterInstruction)ins).getRegisterA(),new ImmutableStringReference("أثير • عالم المصادر")));edit=true;literals++;
       }
      }
     }
     methods.add(edit?new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),body):m);editClass|=edit;
    }
    classes.add(editClass?new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods):c);changed|=editClass;
   }
   if(changed)DexPool.writeTo(out.resolve(e.getName()).toString(),new ImmutableDexFile(d.getOpcodes(),classes));count++;
  }}
  System.out.println("{\"dex_files\":"+count+",\"presentation_literals\":"+literals+"}");
 }
}
