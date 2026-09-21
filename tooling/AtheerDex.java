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

/** Explicit UI, update-policy and module-context edits. No authentication edits. */
public final class AtheerDex {
 static final String R="Lcom/atheer/shell/ModuleRuntime;", H="Lcom/atheer/shell/Host;", CTX="Landroid/content/Context;", ACT="Landroid/app/Activity;";
 static final Set<String> seenActivities=new HashSet<>();
 static int wrapped=0,themed=0,updates=0,attached=0,returns=0,discovery=0;
 static ImmutableMethodReference ref(String owner,String name,String result,String... args){return new ImmutableMethodReference(owner,name,Arrays.asList(args),result);}
 static Method replace(Method m,MethodImplementation body){return new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),body);}
 static Method added(ClassDef c,String name,String arg,boolean theme){
  var instructions=new ArrayList<com.android.tools.smali.dexlib2.iface.instruction.Instruction>();
  if(theme){instructions.add(new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,0,2,ref(R,"theme","I",ACT,"I")));instructions.add(new BuilderInstruction11x(Opcode.MOVE_RESULT,1));}
  else {instructions.add(new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,1,1,ref(R,"context",CTX,CTX)));instructions.add(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,1));}
  instructions.add(new BuilderInstruction3rc(Opcode.INVOKE_SUPER_RANGE,0,2,ref(c.getSuperclass(),name,"V",arg)));instructions.add(new BuilderInstruction10x(Opcode.RETURN_VOID));
  return new ImmutableMethod(c.getType(),name,List.of(new ImmutableMethodParameter(arg,Set.of(),null)),"V",theme?AccessFlags.PUBLIC.getValue():AccessFlags.PROTECTED.getValue(),Set.of(),Set.of(),new ImmutableMethodImplementation(2,instructions,List.of(),List.of()));
 }
 static ClassDef edit(ClassDef c,boolean host,Set<String> activityTypes){
  boolean changed=false,hasAttach=false,hasTheme=false,activity=activityTypes.contains(c.getType());
  List<Method> methods=new ArrayList<>();
  for(Method m:c.getMethods()){
   boolean attachContext=m.getName().equals("attachBaseContext")&&m.getParameterTypes().toString().equals("["+CTX+"]");
   boolean setTheme=m.getName().equals("setTheme")&&m.getParameterTypes().toString().equals("[I]");
   hasAttach|=attachContext;hasTheme|=setTheme;
   if(m.getImplementation()==null){methods.add(m);continue;}
   MutableMethodImplementation body=new MutableMethodImplementation(m.getImplementation());boolean edit=false;
   if(host&&c.getType().equals("Lcom/drama/mp4/data/model/AppUpdate;")&&(m.getName().equals("getUpdateAvailable")||m.getName().equals("isMandatory"))){
    methods.add(replace(m,new ImmutableMethodImplementation(1,List.of(new BuilderInstruction11n(Opcode.CONST_4,0,0),new BuilderInstruction11x(Opcode.RETURN,0)),List.of(),List.of())));changed=true;continue;
   }
   for(int i=body.getInstructions().size()-1;i>=0;i--){
    Instruction inst=body.getInstructions().get(i);
    if(!host&&c.getType().equals("Lcom/google/firebase/FirebaseApp;")&&inst.getOpcode()==Opcode.CONST_CLASS&&inst instanceof ReferenceInstruction&&((ReferenceInstruction)inst).getReference() instanceof TypeReference&&((TypeReference)((ReferenceInstruction)inst).getReference()).getType().equals("Lcom/google/firebase/components/ComponentDiscoveryService;")){
     int reg=((OneRegisterInstruction)inst).getRegisterA();
     body.replaceInstruction(i,new BuilderInstruction21c(Opcode.CONST_CLASS,reg,new ImmutableTypeReference("Lcom/atheer/shell/SourceDiscoveryService;")));discovery++;edit=true;
    }
    if(host&&c.getType().equals("Lcom/drama/mp4/ui/main/MainActivity;")&&m.getName().equals("onCreate")&&inst.getOpcode()==Opcode.RETURN_VOID){
     int self=body.getRegisterCount()-2;body.addInstruction(i,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,self,1,ref(H,"attach","V",ACT)));attached++;edit=true;
    }
    if(inst instanceof ReferenceInstruction&&((ReferenceInstruction)inst).getReference() instanceof MethodReference){
     MethodReference target=(MethodReference)((ReferenceInstruction)inst).getReference();
     if(host&&target.getDefiningClass().equals("Lcom/drama/mp4/data/api/ApiService;")&&target.getName().equals("checkAppUpdate")){
      body.replaceInstruction(i,new BuilderInstruction35c(Opcode.INVOKE_STATIC,0,0,0,0,0,0,ref(H,"updatesDisabled","Ljava/lang/Object;")));updates++;edit=true;
     }
     if(!host&&c.getType().startsWith("Lawr/witcher/WitcherTabs")&&target.getDefiningClass().equals("Lawr/witcher/OscarExperience;")&&target.getName().equals("open")){
      if(inst instanceof FiveRegisterInstruction){var v=(FiveRegisterInstruction)inst;body.replaceInstruction(i,new BuilderInstruction35c(Opcode.INVOKE_STATIC,v.getRegisterCount(),v.getRegisterC(),v.getRegisterD(),v.getRegisterE(),v.getRegisterF(),v.getRegisterG(),ref(R,"returnToMain","V",CTX)));}
      else {var v=(RegisterRangeInstruction)inst;body.replaceInstruction(i,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,v.getStartRegister(),v.getRegisterCount(),ref(R,"returnToMain","V",CTX)));}returns++;edit=true;
     }
    }
    if(!host&&c.getType().equals("Lawr/witcher/WitcherTabs;")&&inst instanceof ReferenceInstruction&&((ReferenceInstruction)inst).getReference() instanceof StringReference&&((StringReference)((ReferenceInstruction)inst).getReference()).getString().equals("عالم المصادر")){
     int reg=((OneRegisterInstruction)inst).getRegisterA();
     body.replaceInstruction(i,new BuilderInstruction21c(Opcode.CONST_STRING,reg,new ImmutableStringReference("التبويبات الأساسية")));edit=true;
    }
   }
   if(!host&&activity&&(attachContext||setTheme)){
    int reg=body.getRegisterCount()-1;
    body.addInstruction(0,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,setTheme?reg-1:reg,setTheme?2:1,setTheme?ref(R,"theme","I",ACT,"I"):ref(R,"context",CTX,CTX)));
    body.addInstruction(1,new BuilderInstruction11x(setTheme?Opcode.MOVE_RESULT:Opcode.MOVE_RESULT_OBJECT,reg));edit=true;
   }
   methods.add(edit?replace(m,body):m);changed|=edit;
  }
  if(!host&&activity){seenActivities.add(c.getType());if(!hasAttach){methods.add(added(c,"attachBaseContext",CTX,false));}if(!hasTheme){methods.add(added(c,"setTheme","I",true));}wrapped++;themed++;changed=true;}
  return changed?new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods):c;
 }
 public static void main(String[] args)throws Exception {
  boolean host=args[0].equals("host");Set<String> types=new HashSet<>();if(!host)for(String s:Files.readAllLines(Path.of(args[3])))types.add("L"+s.split("=")[0].replace('.','/')+";");
  Path out=Path.of(args[2]);Files.createDirectories(out);int dexCount=0,classes=0;
  try(ZipFile z=new ZipFile(args[1])){for(ZipEntry e:Collections.list(z.entries()))if(e.getName().matches("classes\\d*\\.dex")){
   DexBackedDexFile d;try(InputStream in=new BufferedInputStream(z.getInputStream(e))){d=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in);}
   List<ClassDef> list=new ArrayList<>();boolean any=false;for(ClassDef c:d.getClasses()){ClassDef n=edit(c,host,types);list.add(n);any|=n!=c;classes++;}
   if(any)DexPool.writeTo(out.resolve(e.getName()).toString(),new ImmutableDexFile(d.getOpcodes(),list));dexCount++;
  }}
  if(host&&(attached!=2||updates!=3))throw new IllegalStateException("Unexpected host patch counts "+attached+" "+updates);
  if(!host){Set<String> missing=new TreeSet<>(types);missing.removeAll(seenActivities);System.err.println("Missing manifest classes: "+missing);}
  if(!host&&(wrapped!=types.size()||returns<1||discovery!=1))throw new IllegalStateException("Incomplete module patch: "+wrapped+" / "+types.size()+" discovery="+discovery);
  System.out.println("{\"dex_files\":"+dexCount+",\"classes\":"+classes+",\"activity_contexts\":"+wrapped+",\"activity_themes\":"+themed+",\"update_calls_removed\":"+updates+",\"host_tab_hooks\":"+attached+",\"internal_return_hooks\":"+returns+",\"isolated_firebase_discovery\":"+discovery+"}");
 }
}
