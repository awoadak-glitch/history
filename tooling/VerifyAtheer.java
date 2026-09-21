import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Structural checks only: these do not establish Android runtime or server compatibility. */
public final class VerifyAtheer {
 static Map<String,ClassDef> classes(String path)throws Exception {
  Map<String,ClassDef> out=new TreeMap<>();
  try(ZipFile z=new ZipFile(path)) {
   for(ZipEntry e:Collections.list(z.entries()))if(e.getName().matches("classes\\d*\\.dex")) {
    DexBackedDexFile d;try(InputStream in=new BufferedInputStream(z.getInputStream(e))){d=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in);}
    for(ClassDef c:d.getClasses())if(out.put(c.getType(),c)!=null)throw new IllegalStateException("Duplicate class: "+c.getType());
   }
  }
  return out;
 }
 static byte[] serialized(ClassDef c)throws Exception {
  MemoryDataStore out=new MemoryDataStore();DexPool.writeTo(out,new ImmutableDexFile(Opcodes.getDefault(),List.of(c)));return out.getData();
 }
 static boolean updateCaller(ClassDef c) {
  for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())
   if(i instanceof ReferenceInstruction && ((ReferenceInstruction)i).getReference() instanceof MethodReference) {
    MethodReference r=(MethodReference)((ReferenceInstruction)i).getReference();
    if(r.getDefiningClass().equals("Lcom/drama/mp4/data/api/ApiService;")&&r.getName().equals("checkAppUpdate"))return true;
   }
  return false;
 }
 public static void main(String[] args)throws Exception {
  Map<String,ClassDef> before=classes(args[0]),after=classes(args[1]);
  Set<String> missing=new TreeSet<>(before.keySet());missing.removeAll(after.keySet());
  if(!missing.isEmpty())throw new IllegalStateException("Missing host classes: "+missing);
  Set<String> allowed=new TreeSet<>(List.of("Lcom/drama/mp4/ui/main/MainActivity;","Lcom/drama/mp4/data/model/AppUpdate;"));
  for(ClassDef c:before.values())if(updateCaller(c))allowed.add(c.getType());
  int checked=0;List<String> changed=new ArrayList<>();
  for(String name:before.keySet()) {
   if(!name.startsWith("Lcom/drama/mp4/"))continue;
   boolean same=Arrays.equals(serialized(before.get(name)),serialized(after.get(name)));
   if(!same){changed.add(name);if(!allowed.contains(name))throw new IllegalStateException("Unexpected host change: "+name);}
   checked++;
  }
  for(ClassDef c:after.values())if(updateCaller(c))throw new IllegalStateException("Remaining host update request: "+c.getType());
  Map<String,ClassDef> module=classes(args[2]);int activities=0;
  for(String row:Files.readAllLines(Path.of(args[3]))) {
   String type="L"+row.split("=")[0].replace('.','/')+";";
   ClassDef c=module.get(type);if(c==null)throw new IllegalStateException("Missing module activity: "+type);
   for(String method:List.of("attachBaseContext","setTheme")) {
    boolean own=false;for(Method m:c.getMethods())if(m.getName().equals(method)&&m.getImplementation()!=null)own=true;
    if(!own)throw new IllegalStateException("Missing activity adapter: "+type+" "+method);
    for(ClassDef parent=module.get(c.getSuperclass());parent!=null;parent=module.get(parent.getSuperclass()))
     for(Method m:parent.getMethods())if(m.getName().equals(method)&&m.getParameterTypes().size()==1&&AccessFlags.FINAL.isSet(m.getAccessFlags()))throw new IllegalStateException("Final override: "+type+" "+method);
   }
   activities++;
  }
  System.out.println("{\"original_host_classes_present\":"+before.size()+",\"host_application_classes_compared\":"+checked+",\"changed_host_classes\":\""+changed+"\",\"host_update_calls_remaining\":0,\"module_classes\":"+module.size()+",\"module_activity_adapters_verified\":"+activities+",\"device_tested\":false}");
 }
}
