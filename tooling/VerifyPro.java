import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
/** Independently audits merged class boundaries, native entry points and Android API regression. */
public class VerifyPro {
 static Map<String,ClassDef> read(String path)throws Exception{
  Map<String,ClassDef> result=new TreeMap<>();
  try(ZipFile z=new ZipFile(path)){for(ZipEntry e:Collections.list(z.entries()))if(e.getName().matches("classes\\d*\\.dex")){
   DexBackedDexFile dex;try(InputStream s=new BufferedInputStream(z.getInputStream(e))){dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),s);}
   for(ClassDef c:dex.getClasses())if(result.put(c.getType(),c)!=null)throw new IllegalStateException("Duplicate class "+c.getType());
  }}return result;
 }
 static byte[] dump(ClassDef c)throws Exception{MemoryDataStore o=new MemoryDataStore();DexPool.writeTo(o,new ImmutableDexFile(Opcodes.getDefault(),List.of(c)));return o.getData();}
 public static void main(String[] a)throws Exception{
  Map<String,ClassDef> before=read(a[0]),after=read(a[1]),guest=read(a[2]),feature=read(a[3]);
  try(ZipFile old=new ZipFile(a[0]);ZipFile next=new ZipFile(a[1])){
   for(ZipEntry e:Collections.list(old.entries()))if(e.getName().matches("classes\\d*\\.dex"))
    if(!Arrays.equals(old.getInputStream(e).readAllBytes(),next.getInputStream(next.getEntry(e.getName())).readAllBytes()))throw new IllegalStateException("Host DEX changed");
  }
  if(!after.keySet().containsAll(before.keySet()))throw new IllegalStateException("Missing host classes");
  Set<String> changedDexClasses=new HashSet<>();int identicalDex=0;
  try(ZipFile old=new ZipFile(a[2]);ZipFile next=new ZipFile(a[3])){
   for(ZipEntry e:Collections.list(old.entries()))if(e.getName().matches("classes\\d*\\.dex")){
    byte[] b=old.getInputStream(e).readAllBytes(),n=next.getInputStream(next.getEntry(e.getName())).readAllBytes();
    if(Arrays.equals(b,n)){identicalDex++;continue;}
    DexBackedDexFile d=new DexBackedDexFile(Opcodes.getDefault(),b);
    for(ClassDef c:d.getClasses())changedDexClasses.add(c.getType());
   }
  }
  if(!feature.keySet().equals(guest.keySet()))throw new IllegalStateException("Feature classes lost");
  int brandChanged=0,natives=0;
  for(String name:guest.keySet()){
   boolean changed=changedDexClasses.contains(name)&&!Arrays.equals(dump(guest.get(name)),dump(feature.get(name)));
   if(changed&&!name.startsWith("Lawr/witcher/"))throw new IllegalStateException("Non-UI feature edit "+name);
   if(changed)brandChanged++;
   for(Method m:feature.get(name).getMethods())if(AccessFlags.NATIVE.isSet(m.getAccessFlags()))natives++;
  }
  if(!after.get("Lcom/atheer/shell/MergeFactory;").getSuperclass().equals("Lcom/pandora/core/AppFactory;"))throw new IllegalStateException("Startup superclass changed");
  int adapters=0;
  for(String row:Files.readAllLines(Path.of(a[4]))){
   String name="L"+row.split("=")[0].replace('.','/')+";";
   ClassDef c=feature.get(name);if(c==null||before.containsKey(name))throw new IllegalStateException("Missing/colliding activity "+name);
   for(String method:List.of("attachBaseContext","setTheme")){
    boolean found=false;for(Method m:c.getMethods())if(m.getName().equals(method)&&m.getImplementation()!=null)found=true;
    if(!found)throw new IllegalStateException("Missing context adapter "+name);
    for(ClassDef parent=feature.get(c.getSuperclass());parent!=null;parent=feature.get(parent.getSuperclass()))
     for(Method m:parent.getMethods())if(m.getName().equals(method)&&m.getParameterTypes().size()==1&&AccessFlags.FINAL.isSet(m.getAccessFlags()))throw new IllegalStateException("Final override "+name);
   }adapters++;
  }
  int shell=0;
  for(ClassDef c:after.values())if(c.getType().startsWith("Lcom/atheer/shell/")){
   shell++;
   for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){
    Reference r=((ReferenceInstruction)i).getReference();
    if(r instanceof MethodReference&&((MethodReference)r).getName().equals("getClassLoadingLock"))throw new IllegalStateException("Java SE-only API regression");
    if(r instanceof StringReference&&((StringReference)r).getString().endsWith(".apk"))throw new IllegalStateException("Feature still loads an APK");
   }
  }
  ClassDef tabs=feature.get("Lawr/witcher/WitcherTabs;");boolean returns=false;
  for(ClassDef c:feature.values())if(c.getType().startsWith("Lawr/witcher/WitcherTabs"))for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction&&((ReferenceInstruction)i).getReference() instanceof MethodReference){
   MethodReference r=(MethodReference)((ReferenceInstruction)i).getReference();
   if(r.getDefiningClass().equals("Lcom/atheer/shell/ModuleRuntime;")&&r.getName().equals("returnToMain"))returns=true;
   if(r.getDefiningClass().equals("Lawr/witcher/OscarExperience;")&&r.getName().equals("open"))throw new IllegalStateException("Old Oscar bridge still linked");
  }
  if(!returns)throw new IllegalStateException("No internal return route");
  System.out.println("{\"host_classes_unchanged\":"+before.size()+",\"feature_classes_preserved\":"+feature.size()+",\"feature_dex_unchanged\":"+identicalDex+",\"presentation_classes_changed\":"+brandChanged+",\"original_feature_native_methods\":"+natives+",\"activity_adapters\":"+adapters+",\"shell_classes\":"+shell+",\"internal_return_route\":true,\"android_runtime_tested\":false}");
 }
}
