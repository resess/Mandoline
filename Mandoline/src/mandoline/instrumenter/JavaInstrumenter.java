package mandoline.instrumenter;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.LongType;
import soot.Modifier;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Trap;
import soot.Type;
import soot.Unit;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.IfStmt;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.SwitchStmt;
import soot.options.Options;
import soot.toolkits.scalar.Pair;
import soot.util.Chain;
import soot.util.MultiMap;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.Value;
import soot.VoidType;
import soot.jimple.ThrowStmt;
import soot.IntType;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fj.P;
import mandoline.slicer.Slicer;
import mandoline.utils.AnalysisLogger;

public class JavaInstrumenter extends Instrumenter {
    Set<String> threadMethods = new HashSet<>();
    private boolean fieldTracking = false;
    private boolean threadTracking = false;
    private boolean timeTracking = false;
    private boolean isOriginal = false;
    private Long appSize = 0L;
    JSONObject staticLog = new JSONObject();
    GlobalCounter globalLineCounter = new GlobalCounter();
    Chain<SootClass> libClasses = null;
    String jarName;

    public JavaInstrumenter(String jarName) {
        // this.threadMethods.addAll(tc.values());
        this.jarName = new File(jarName).getAbsolutePath();
    }


    void initialize(String apkPath, String mandolineJar) {
        AnalysisLogger.log(true, "Initializing Instrumenter");
        createInstrumentationPackagesList();
        Scene.v().addBasicClass("java.io.PrintStream",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System",SootClass.SIGNATURES);
        Scene.v().addBasicClass("MandolineLogger", SootClass.BODIES);
        Scene.v().addBasicClass("MandolineWriter", SootClass.BODIES);
        Scene.v().addBasicClass("MandolineShutdown", SootClass.BODIES);
        Options.v().set_prepend_classpath(true);
        // Options.v().set_soot_classpath("VIRTUAL_FS_FOR_JDK");
        String [] excList = {"org.slf4j.impl.*", "org.mozilla.*"};
        List<String> excludePackagesList = Arrays.asList(excList);
        Options.v().set_exclude(excludePackagesList);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_dir(Arrays.asList(apkPath, mandolineJar));
        Options.v().set_output_format(Options.output_format_class);
        Options.v().set_output_dir(Slicer.SOOT_OUTPUT_STRING);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        libClasses = Scene.v().getLibraryClasses();
        AnalysisLogger.log(true, "Initialization done");
    }

    void runMethodTransformationPack() {
        PackManager.v().getPack("wjpp").add(new Transform("wjpp.classadder", new SceneTransformer(){
            @Override
            protected void internalTransform(String phaseName, Map<String, String> options) {
                Scene.v().getSootClass("MandolineLogger").setApplicationClass();
                Scene.v().getSootClass("MandolineWriter").setApplicationClass();
                Scene.v().getSootClass("MandolineShutdown").setApplicationClass();
            }
        }));


        PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", new BodyTransformer() {
            @Override
            protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {
                SootClass cls = b.getMethod().getDeclaringClass();
                if (cls.getName().contains("MandolineLogger")) {
                    return;
                }
                if (cls.getName().contains("MandolineWriter")) {
                    return;
                }
                if (cls.getName().contains("MandolineShutdown")) {
                    return;
                }

                boolean skip = !JavaInstrumenter.this.instrumentationPackagesList.isEmpty();
                for (String includedPkg: JavaInstrumenter.this.instrumentationPackagesList) {
                    if (cls.getPackageName().startsWith(includedPkg) ) {
                        skip = false;
                    }
                }
                if (skip) {
                    return;
                }

                
                Long methodSize = 0L;
                SootMethod mtd = b.getMethod();

                if (cls.getName().contains("OutputStream") && mtd.getName().contains("write")) {
                    return;
                }


                boolean isOnDestroy = false;
                if (mtd.getName().equals("onDestroy")) {
                    isOnDestroy = true;
                }
                StmtSwitch stmtSwitch = new StmtSwitch();
                
                stmtSwitch.setOriginal(isOriginal);
                AddedLocals addedLocals = new AddedLocals();
                Flags flags = new Flags(timeTracking, threadTracking, fieldTracking, false, false, isOriginal);
                if (mtd.getName().contains("<init>")) {
                    flags.superCalled = false;
                } else {
                    flags.superCalled = true;
                }
                if (b.getTraps().isEmpty()) {
                    stmtSwitch.setTimeTracking(timeTracking);
                } else {
                    flags.timeTracking = false;
                    flags.threadTracking = false;
                }


                
                List<String> traps = new ArrayList<>();
                for (Trap trap: mtd.getActiveBody().getTraps()) {
                    traps.add(trap.getBeginUnit().toString());
                }
                // AnalysisLogger.log(true, "Traps of {} are {}", mtd, traps); 

                final PatchingChain<Unit> units = b.getUnits();
                Set<Unit> instrumentedUnits = new HashSet<>();
                boolean instrumentedFirst = false;
                LinkedHashMap<Unit, Long> unitNumMap = new LinkedHashMap<>();
                Map<Unit, Long> taggedUnits = new HashMap<>();
                // AnalysisLogger.log(true, "In method: {}", mtd);
                for(Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
                    final Unit u = (Unit) iter.next();
                    if (!(u instanceof IdentityStmt)) {
                        instrumentedFirst = basicBlockInstrument(b, cls, mtd, isOnDestroy, addedLocals, flags, units,
                                                                instrumentedUnits, instrumentedFirst, unitNumMap, taggedUnits, u, traps);
                        methodSize += 1;
                    }
                    
                }
                synchronized (appSize) {
                    appSize += methodSize;
                }
                JSONObject job = new JSONObject();
                String key = "";
                JSONArray jArray = new JSONArray();
                Unit prevU = null;
                for (Unit u : unitNumMap.keySet()) {
                    if (u instanceof IdentityStmt) {
                        continue;
                    }
                    if (taggedUnits.containsKey(u)) {
                        if (!key.equals("")){
                            job.put(key, jArray);
                            jArray = new JSONArray();
                        }
                        key = taggedUnits.get(u).toString();
                    } else if (prevU != null && (prevU instanceof ReturnStmt || prevU instanceof ReturnVoidStmt || prevU instanceof ThrowStmt)) {
                        if (!key.equals("")){
                            job.put(key, jArray);
                            jArray = new JSONArray();
                        }
                        if (taggedUnits.get(u) == null) {
                            key = "";
                        } else {
                            key = taggedUnits.get(u).toString();
                        }
                    }
                    jArray.add(u.toString());
                    prevU = u;
                }
                if (!key.equals("")) {
                    job.put(key, jArray);
                }
                
                synchronized(staticLog){
                    staticLog.put(b.getMethod().getSignature(), job);
                }
            }

            private boolean basicBlockInstrument(final Body b, SootClass cls, SootMethod mtd, boolean isOnDestroy,
                    AddedLocals addedLocals, Flags flags, final PatchingChain<Unit> units, Set<Unit> instrumentedUnits,
                    boolean instrumentedFirst, LinkedHashMap<Unit, Long> unitNumMap, Map<Unit, Long> taggedUnits,
                    final Unit u, List<String> traps) {
                unitNumMap.put(u, -1L);
                if (threadMethods.contains(b.getMethod().getSubSignature())) {
                        flags.isCallbackOrThread = true;
                }
                
                if (isOnDestroy && !instrumentedFirst &&!(u instanceof IdentityStmt)) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addFlush(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                    }
                    instrumentedFirst = true;
                }
                // AnalysisLogger.log(true, "Inspecting: {} whee traps are {}", u, traps);
                if (!instrumentedFirst) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                    }
                    instrumentedFirst = true;
                }
                if (u instanceof IfStmt) {
                    if (!instrumentedFirst ) {
                        if (!instrumentedUnits.contains(u)) {
                            InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                            instrumentedUnits.add(u);
                        }
                        instrumentedFirst = true;
                    }
                    Unit fallThrough = b.getUnits().getSuccOf(u);
                    if (!instrumentedUnits.contains(fallThrough)) {
                        InstrumenterUtils.addPrint(fallThrough, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(fallThrough);
                    }
                    Unit target = ((IfStmt) u).getTarget();
                    if (!instrumentedUnits.contains(target)) {
                        InstrumenterUtils.addPrint(target, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(target);
                    }
                } 
                if (u instanceof GotoStmt) {
                    if (!instrumentedFirst ) {
                        if (!instrumentedUnits.contains(u)) {
                            InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                            instrumentedUnits.add(u);
                        }
                        instrumentedFirst = true;
                    }
                    Unit target = ((GotoStmt) u).getTarget();
                    if (!instrumentedUnits.contains(target)) {
                        InstrumenterUtils.addPrint(target, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(target);
                    }
                    Unit after = units.getSuccOf(u);
                    if (after instanceof IdentityStmt) {
                        after = units.getSuccOf(after);
                    }
                    if (after != null && !instrumentedUnits.contains(after)) {
                        InstrumenterUtils.addPrint(after, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(after);
                    }
                }
                if (u instanceof InvokeStmt) {
                    if (!instrumentedFirst) {
                        if (!instrumentedUnits.contains(u)) {
                            InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                            instrumentedUnits.add(u);
                        }
                        instrumentedFirst = true;
                    }
                    InvokeStmt invokeStmt = (InvokeStmt) u;
                    if (invokeStmt.getInvokeExpr() instanceof SpecialInvokeExpr) {
                        SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) invokeStmt.getInvokeExpr();
                        boolean isSuperClass = specialInvokeExpr.getMethod().getDeclaringClass().equals(b.getMethod().getDeclaringClass().getSuperclass());
                        boolean isConstructor = specialInvokeExpr.getMethod().getName().contains("<init>");
                        if (isSuperClass && isConstructor) {
                            flags.superCalled = true;
                        }
                    }
                    if (libClasses.contains(invokeStmt.getInvokeExpr().getMethod().getDeclaringClass())) {
                        return instrumentedFirst;
                    }
                    
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                    }
                    Unit ret = b.getUnits().getSuccOf(u);
                    if (!instrumentedUnits.contains(ret)) {
                        InstrumenterUtils.addPrint(ret, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(ret);
                    }
                } 
                if ((u instanceof AssignStmt) && ((AssignStmt) u).containsInvokeExpr()) {
                    if (!instrumentedFirst) {
                        if (!instrumentedUnits.contains(u)) {
                            InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                            instrumentedUnits.add(u);
                        }
                        instrumentedFirst = true;
                    }
                    InvokeExpr invokeExpr = ((AssignStmt) u).getInvokeExpr();
                    if (libClasses.contains(invokeExpr.getMethod().getDeclaringClass())) {
                        return instrumentedFirst;
                    }
                    
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                    }
                    Unit ret = b.getUnits().getSuccOf(u);
                    if (!instrumentedUnits.contains(ret)) {
                        InstrumenterUtils.addPrint(ret, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(ret);
                    }
                } 
                if (u instanceof ReturnVoidStmt) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                        InstrumenterUtils.insertEndTimeTracking(u, units, b, addedLocals, flags, instrumentedUnits, taggedUnits);
                    }
                } 
                if (u instanceof ReturnStmt) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                        InstrumenterUtils.insertEndTimeTracking(u, units, b, addedLocals, flags, instrumentedUnits, taggedUnits);
                    }
                }
                if (u instanceof ThrowStmt) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                        InstrumenterUtils.insertEndTimeTracking(u, units, b, addedLocals, flags, instrumentedUnits, taggedUnits);
                    }
                }
                if (u instanceof AssignStmt && !(u instanceof IdentityStmt)){
                    if (((AssignStmt) u).containsFieldRef()) {
                        if(flags.fieldTracking) {
                            if (!instrumentedUnits.contains(u)) {
                                InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                                instrumentedUnits.add(u);
                            }
                        }
                    }
                }
                if (u instanceof Stmt && !(u instanceof IdentityStmt)){
                    if (!instrumentedFirst ) {
                        if (!instrumentedUnits.contains(u)) {
                            InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                            instrumentedUnits.add(u);
                        }
                        instrumentedFirst = true;
                    }
                }
                if (traps.contains(u.toString())) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                    }
                    instrumentedFirst = true;
                }
                return instrumentedFirst;
            }
        }));
        
    }

    @Override
    public void start (String options, String staticLogFile, String apkPath, String mandolineJar) {
        soot.G.reset();
        if (options.contains("field")) {
            this.fieldTracking = true;
        }
        if (options.contains("thread")) {
            this.threadTracking = true;
        }
        if (options.contains("time")) {
            this.timeTracking = true;
        }
        if (options.contains("original")) {
            this.isOriginal = true;
        }

        initialize(apkPath, mandolineJar);
        runMethodTransformationPack();
        Scene.v().loadNecessaryClasses();
        AnalysisLogger.log(true, "Running packs ... ");
        PackManager.v().runPacks();
        AnalysisLogger.log(true, "Writing output ... ");
        PackManager.v().writeOutput();
        AnalysisLogger.log(true, "Output written ... ");

        AnalysisLogger.log(true, "Writing log file... ");
        File logFile = new File(staticLogFile);
        try {
            logFile.delete();
            FileUtils.writeStringToFile(logFile, staticLog.toString(), "UTF-8", true);
            
        } catch (IOException e) {
            throw new Error("Failed to write static log file");
        }

        AnalysisLogger.log(true, "Writing size file ... ");
        File sizeFile = new File("apk-size.txt");
        try {
            sizeFile.delete();
            FileUtils.writeStringToFile(sizeFile, "Number of Jimple statements (apkSize): " + appSize.toString(), "UTF-8", true);
            
        } catch (IOException e) {
            throw new Error("Failed to write static log file");
        }

        AnalysisLogger.log(true, "Number of Jimple statements (apkSize): {}", appSize.toString());
        AnalysisLogger.log(true, "Writing JAR");
        try {
            AnalysisLogger.log(true, "Soot file: {}", new File(Slicer.SOOT_OUTPUT_STRING));
            AnalysisLogger.log(true, "Soot file is directory?: {}", new File(Slicer.SOOT_OUTPUT_STRING).isDirectory());
            if (new File(Slicer.SOOT_OUTPUT_STRING).isDirectory()) {
                // File unzipDir = new File("unzip_dir");
                // unzipDir.mkdir();
                unzipTo(new File(apkPath), new File(Slicer.SOOT_OUTPUT_STRING));

                File dir = new File(jarName).getParentFile();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                List<String> instrumentedClasses = new ArrayList<>();
                listDirectory(new File(Slicer.SOOT_OUTPUT_STRING).getAbsolutePath()+1, Slicer.SOOT_OUTPUT_STRING, 0, instrumentedClasses);

                AnalysisLogger.log(true, "Number of classes: {}", instrumentedClasses.size());
                int numFiles = 100;
                for (int i = 0; i < instrumentedClasses.size(); i+=numFiles){
                    // String clazzFile = instrumentedClasses.get(i);
                    
                    int minIndex = Math.min(i+numFiles, instrumentedClasses.size());
                    String clazzFile = String.join(" ", instrumentedClasses.subList(i, minIndex));
                    String jarOptions;
                    if (i == 0) {
                        jarOptions = "cvf";
                    } else {
                        jarOptions = "uf";
                    }

                    Process p = Runtime.getRuntime().exec("jar " + jarOptions + " " + jarName + " " + clazzFile, null, new File(Slicer.SOOT_OUTPUT_STRING));
                    p.waitFor();
                    // String output = IOUtils.toString(p.getInputStream());
                    // String errorOutput = IOUtils.toString(p.getErrorStream());
                    // AnalysisLogger.log(true, "Process result {}", output + " " + errorOutput);
                }
                // Process p = Runtime.getRuntime().exec("jar cvf " + jarName + " " + String.join(" ", instrumentedClasses), null, new File(Slicer.SOOT_OUTPUT_STRING));
                // p.waitFor();
            }

        } catch (IOException | InterruptedException e) {
            AnalysisLogger.warn(true, "Couldn't create jar");
        }
        AnalysisLogger.log(true, "Instrumentation done: file wrote {}", jarName);
    }

    private void unzipTo(File fileZip, File unzipDir) throws IOException {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            // AnalysisLogger.log(true, "Zip entry: {}", zipEntry);
            if (!zipEntry.getName().endsWith("/") && !zipEntry.getName().endsWith(".class") && !zipEntry.getName().contains("META-INF")) {
                // AnalysisLogger.log(true, "Copying: {}", zipEntry);
                File newFile = new File(unzipDir + File.separator + zipEntry);
                File parent = newFile.getParentFile();
                parent.mkdirs();
                // write file content
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        
        zis.closeEntry();
        zis.close();
	}


	public void listDirectory(String base, String dirPath, int level, List<String> files) {
        File dir = new File(dirPath);
        File[] firstLevelFiles = dir.listFiles();
        if (firstLevelFiles != null && firstLevelFiles.length > 0) {
            for (File aFile : firstLevelFiles) {
                if (aFile.isDirectory()) {
                    listDirectory(base, aFile.getAbsolutePath(), level + 1, files);
                } else {
                    files.add(aFile.getAbsolutePath().substring(base.length())); // .replace("$", "\\$"));
                }
            }
        }
    }
}


class InstrumenterUtils {


    public static SootMethod createDestroyMethod(SootClass thisClass, SootMethod superMethod){
        List<Type> params = new ArrayList<>();
        // params.add(RefType.v(thisClass.getName()));
        Type ret = VoidType.v();
        SootMethod onDestroy = new SootMethod("onDestroy", params, ret, Modifier.PROTECTED);
        JimpleBody body = Jimple.v().newBody(onDestroy);
        // ThisRef instance = Jimple.v().newThisRef(RefType.v());
        onDestroy.setActiveBody(body);
        
        final PatchingChain<Unit> units = body.getUnits();
        Local thiz = Jimple.v().newLocal("$this", thisClass.getType());
        body.getLocals().add(thiz);
        units.add(Jimple.v().newIdentityStmt(thiz, Jimple.v().newThisRef(thisClass.getType())));
        units.add(
            Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(thiz, superMethod.makeRef())
            )
        );
        units.add(Jimple.v().newReturnVoidStmt());
        return onDestroy;
    }






    public static Local insertThreadTrackingAfter(Unit u, Body b){
        final PatchingChain<Unit> units = b.getUnits();
        Local threadField = Jimple.v().newLocal("threadField", RefType.v("java.lang.Thread"));
        b.getLocals().add(threadField);
        Local threadIdField = Jimple.v().newLocal("threadIdField", LongType.v());
        b.getLocals().add(threadIdField);

        SootMethod currThread = Scene.v().getSootClass("java.lang.Thread").getMethod("java.lang.Thread currentThread()");
        SootMethod getId = Scene.v().getSootClass("java.lang.Thread").getMethod("long getId()");

        units.insertAfter(Jimple.v().newAssignStmt(threadIdField,
            Jimple.v().newVirtualInvokeExpr (threadField, getId.makeRef())), u);

        units.insertAfter(Jimple.v().newAssignStmt(threadField,
            Jimple.v().newStaticInvokeExpr (currThread.makeRef())), u);
        return threadIdField;
    }

    public static Local insertThreadTracking(Unit u, Body b){
        final PatchingChain<Unit> units = b.getUnits();
        Local threadField = Jimple.v().newLocal("threadField", RefType.v("java.lang.Thread"));
        b.getLocals().add(threadField);
        Local threadIdField = Jimple.v().newLocal("threadIdField", LongType.v());
        b.getLocals().add(threadIdField);

        SootMethod currThread = Scene.v().getSootClass("java.lang.Thread").getMethod("java.lang.Thread currentThread()");
        SootMethod getId = Scene.v().getSootClass("java.lang.Thread").getMethod("long getId()");

        units.insertBefore(Jimple.v().newAssignStmt(threadField,
            Jimple.v().newStaticInvokeExpr (currThread.makeRef())), u);

        units.insertBefore(Jimple.v().newAssignStmt(threadIdField,
            Jimple.v().newVirtualInvokeExpr (threadField, getId.makeRef())), u);
        return threadIdField;
    }

    public static void insertFieldTracking(Unit u, PatchingChain<Unit> units, Body b, AssignStmt stmt, Set<Unit> instrumentedUnits, String payload) {
        boolean debug = b.getMethod().getDeclaringClass().getName().contains("FlowLayout")
                     && b.getMethod().getName().contains("onMeasure");


        Local tmpField = Jimple.v().newLocal("tmpField", RefType.v("java.lang.Object"));
        b.getLocals().add(tmpField);

        // Local tmpRef = Jimple.v().newLocal("tmpRef", RefType.v("java.io.PrintStream"));
        // b.getLocals().add(tmpRef);

        Local tmpString = Jimple.v().newLocal("tmpString", RefType.v("java.lang.String"));
        b.getLocals().add(tmpString);

        SootMethod printerMethod = Scene.v().getSootClass("MandolineLogger").getMethod("void println(java.lang.String)");
        // SootMethod printerMethod = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
        
        

        if (!stmt.containsFieldRef()) {
            return;
        }
        // Value l = stmt.getLeftOp();
        // Value r = stmt.getRightOp();
        // if (l instanceof FieldRef) {
        //     if (((FieldRef) l).getField().getType() instanceof PrimType) {
        //         return;
        //     }
        //     field = r;
        // } else if (r instanceof FieldRef) {
        //     if (((FieldRef) r).getField().getType() instanceof PrimType) {
        //         return;
        //     }
        //     field = l;
        // } else {
        //     return;
        // }
        // Value field = stmt.getFieldRefBox().getValue();

        // Value field = stmt.getFieldRefBox().getValue();
        // if (stmt.getFieldRef().getType() instanceof PrimType) {
        //     return;
        // }

        if (stmt.getFieldRef() instanceof StaticFieldRef) {
            return;
        } 
        // Value field = ((InstanceFieldRef)stmt.getFieldRef()).getBase();
        Value field = ((InstanceFieldRef)stmt.getFieldRef()).getBase();


        Local sb = Jimple.v().newLocal("sb", RefType.v("java.lang.StringBuilder"));
        b.getLocals().add(sb);
        Local hashCode = Jimple.v().newLocal("hashCode", IntType.v());
        b.getLocals().add(hashCode);

        SootMethod identityHashCode = Scene.v().getMethod
        ("<java.lang.System: int identityHashCode(java.lang.Object)>");
        SootMethod sbInit = Scene.v().getMethod
        ("<java.lang.StringBuilder: void <init>()>");
        SootMethod sbAppendString = Scene.v().getMethod
        ("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>");
        SootMethod sbAppendInt = Scene.v().getMethod
        ("<java.lang.StringBuilder: java.lang.StringBuilder append(int)>");
        SootMethod sbtoString = Scene.v().getMethod
        ("<java.lang.StringBuilder: java.lang.String toString()>");
        Unit temp;
        

        temp = Jimple.v().newAssignStmt(sb, Jimple.v().newNewExpr( RefType.v("java.lang.StringBuilder")));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        temp = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr (sb, sbInit.makeRef()));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        temp = Jimple.v().newAssignStmt(hashCode, Jimple.v().newStaticInvokeExpr(identityHashCode.makeRef(), field));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (sb, sbAppendString.makeRef(), StringConstant.v(payload)));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (sb, sbAppendString.makeRef(), StringConstant.v(":FIELD:")));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (sb, sbAppendInt.makeRef(), hashCode));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        temp = Jimple.v().newAssignStmt(tmpString, Jimple.v().newVirtualInvokeExpr (sb, sbtoString.makeRef()));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        temp = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(printerMethod.makeRef(), tmpString));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);
        return;
    }


    public static Local addStringRef(Body body) {
        Local tmpStrRef = Jimple.v().newLocal("stringRef", RefType.v("java.lang.String"));
        body.getLocals().add(tmpStrRef);
        return tmpStrRef;
    }

    public static Local addTmpString(Body body) {
        Local tmpString = Jimple.v().newLocal("tmpString", RefType.v("java.lang.String"));
        body.getLocals().add(tmpString);
        return tmpString;
    }

    public static Local addTagString(Body body) {
        Local tmpString = Jimple.v().newLocal("tagString", RefType.v("java.lang.String"));
        body.getLocals().add(tmpString);
        return tmpString;
    }


    public static Local addStringBuilder(Body body) {
        Local sb = Jimple.v().newLocal("sb", RefType.v("java.lang.StringBuilder"));
        body.getLocals().add(sb);
        return sb;
    }

    public static Local addPrintStream(Body body) {
        Local printStream = Jimple.v().newLocal("printStream", RefType.v("java.io.PrintStream"));
        body.getLocals().add(printStream);
        return printStream;
    }

    public static String getPayload(TYPE type, Unit u, SootClass sc, SootMethod sm, Body b) {
        String header = u.getJavaSourceStartLineNumber() +"ZZZ"+ sc.getName() + "ZZZ" + sm.getName();
        String tag = "SLICING: ";
        String typeStr = "";
        
        switch(type)
        {
            case DIRECTOR:
                typeStr = "__director__";
                break;
            case HEAD:
                typeStr = "__head__";
                break;
            case TAIL:
                typeStr = "__tail__";
                break;
            case INST:
                typeStr = "__inst__";
                break;
            default:
                break;
        }
        return tag+"ZZZ" + header + "ZZZ" + typeStr+"ZZZ" + u.toString();
    }


    public static void addFlush(Unit u, PatchingChain<Unit> units, Body b, AddedLocals addedLocals, SootClass cls, SootMethod mtd, Flags flags, Set<Unit> instrumentedUnits , Map<Unit, Long> taggedUnits, GlobalCounter globalLineCounter){
        
        long counter = globalLineCounter.inc();
        taggedUnits.put(u, counter);
        SootMethod sbInit = Scene.v().getMethod("<java.lang.StringBuilder: void <init>()>");
        SootMethod sbAppendString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>");
        SootMethod sbAppendLong = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(long)>");
        SootMethod sbToString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.String toString()>");
        SootMethod printerMethod = Scene.v().getSootClass("MandolineLogger").getMethod("void flush(java.lang.String)");
        Unit temp = null;


        if (addedLocals.startTimer==null && flags.timeTracking && flags.isCallbackOrThread) {
            addedLocals.startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (flags.isOriginal) {
            return;
        }
        
        if (addedLocals.threadId == null && flags.threadTracking) {
            addedLocals.threadId = InstrumenterUtils.insertThreadTracking(u, b);
        }
        if (addedLocals.tmpString == null) {
            addedLocals.tmpString = InstrumenterUtils.addTmpString(b);
        }
        if (addedLocals.sb == null) {
            addedLocals.sb = InstrumenterUtils.addStringBuilder(b);
        }

        String payload = String.valueOf(counter);

        if (!flags.threadTracking || flags.threadIdInserted) {
            temp = Jimple.v().newAssignStmt(addedLocals.tmpString, StringConstant.v(payload));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
        } else {
            temp = Jimple.v().newAssignStmt(addedLocals.sb, Jimple.v().newNewExpr( RefType.v("java.lang.StringBuilder")));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr (addedLocals.sb, sbInit.makeRef()));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendString.makeRef(), StringConstant.v(payload+":")));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendLong.makeRef(), addedLocals.threadId));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newAssignStmt(addedLocals.tmpString, Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbToString.makeRef()));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            flags.threadIdInserted = true;
        }

        temp = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(printerMethod.makeRef(), addedLocals.tmpString));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        if (flags.fieldTracking && flags.superCalled) {
            if (u instanceof AssignStmt) {
                AssignStmt stmt = (AssignStmt) u;
                InstrumenterUtils.insertFieldTracking(u, units, b, stmt, instrumentedUnits, payload);
            }
        }
        b.validate();
    }


    public static void addPrint(Unit u, PatchingChain<Unit> units, Body b, AddedLocals addedLocals, SootClass cls, SootMethod mtd, Flags flags, Set<Unit> instrumentedUnits , Map<Unit, Long> taggedUnits, GlobalCounter globalLineCounter){
        long counter = globalLineCounter.inc();
        taggedUnits.put(u, counter);
        // for (SootMethod s:  Scene.v().getSootClass("MandolineLogger").getMethods()) {
        //     logger.info("Method: {}", s);
        // }

        // AnalysisLogger.log(true, "add print: {}", counter);

        SootMethod sbInit = Scene.v().getMethod("<java.lang.StringBuilder: void <init>()>");
        SootMethod sbAppendString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>");
        SootMethod sbAppendLong = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(long)>");
        SootMethod sbToString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.String toString()>");
        SootMethod printerMethod = Scene.v().getSootClass("MandolineLogger").getMethod("void println(java.lang.String)");
        // SootMethod printerMethod = Scene.v().getSootClass("org.tinylog.Logger").getMethod("void info(java.lang.Object)");
        // SootMethod printerMethod = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
        Unit temp = null;


        if (addedLocals.startTimer==null && flags.timeTracking && flags.isCallbackOrThread) {
            addedLocals.startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (flags.isOriginal) {
            return;
        }

        if (addedLocals.threadId == null && flags.threadTracking) {
            addedLocals.threadId = InstrumenterUtils.insertThreadTracking(u, b);
        }
        if (addedLocals.tmpString == null) {
            addedLocals.tmpString = InstrumenterUtils.addTmpString(b);
        }
        if (addedLocals.sb == null) {
            addedLocals.sb = InstrumenterUtils.addStringBuilder(b);
        }
        // if (addedLocals.tagString == null) {
        //     addedLocals.tagString = InstrumenterUtils.addTagString(b);
        // }

        // temp = Jimple.v().newAssignStmt(addedLocals.tagString, StringConstant.v("SLICING"));
        // instrumentedUnits.add(temp);
        // units.insertBefore(temp, u);

        // if (addedLocals.printer == null) {
        //     addedLocals.printer = InstrumenterUtils.addPrintStream(b);
        // }

        String payload = String.valueOf(counter);

        if (!flags.threadTracking || flags.threadIdInserted) {
        // if (!flags.threadTracking) {
            temp = Jimple.v().newAssignStmt(addedLocals.tmpString, StringConstant.v(payload));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
        } else {
            temp = Jimple.v().newAssignStmt(addedLocals.sb, Jimple.v().newNewExpr( RefType.v("java.lang.StringBuilder")));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr (addedLocals.sb, sbInit.makeRef()));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendString.makeRef(), StringConstant.v(payload+":")));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendLong.makeRef(), addedLocals.threadId));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            temp = Jimple.v().newAssignStmt(addedLocals.tmpString, Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbToString.makeRef()));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);
            flags.threadIdInserted = true;
        }

        temp = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(printerMethod.makeRef(), addedLocals.tmpString));
        instrumentedUnits.add(temp);
        units.insertBefore(temp, u);

        // temp = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(printerMethod.makeRef(), addedLocals.tagString, addedLocals.tmpString));
        // instrumentedUnits.add(temp);
        // units.insertBefore(temp, u);
        
        // temp = Jimple.v().newAssignStmt(addedLocals.printer, Jimple.v().newStaticFieldRef(Scene.v().getField("<java.lang.System: java.io.PrintStream err>").makeRef()));
        // instrumentedUnits.add(temp);
        // units.insertBefore(temp, u);
        // temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(addedLocals.printer, printerMethod.makeRef(), addedLocals.tmpString));
        // instrumentedUnits.add(temp);
        // units.insertBefore(temp, u);
        if (flags.fieldTracking && flags.superCalled) {
            if (u instanceof AssignStmt) {
                AssignStmt stmt = (AssignStmt) u;
                InstrumenterUtils.insertFieldTracking(u, units, b, stmt, instrumentedUnits, payload);
            }
        }
        // AnalysisLogger.log(true, "Added print({}) : {}", payload, u);
        b.validate();
    }

    public static Local insertStartTimeTracking(Unit u, Body b){
        final PatchingChain<Unit> units = b.getUnits();
        Local startTime = Jimple.v().newLocal("startTime", LongType.v());
        b.getLocals().add(startTime);
        SootMethod getTime = Scene.v().getSootClass("java.lang.System").getMethod("long nanoTime()");
        units.insertBefore(Jimple.v().newAssignStmt(startTime,
            Jimple.v().newStaticInvokeExpr(getTime.makeRef())), u);
        return startTime;
    } 
    
    public static void insertEndTimeTracking(Unit u, PatchingChain<Unit> units, Body b, AddedLocals addedLocals, Flags flags, Set<Unit> instrumentedUnits , Map<Unit, Long> taggedUnits){
        if (flags.timeTracking && flags.isCallbackOrThread) {
            if (addedLocals.startTimer == null) {
                return;
            }
            SootMethod sbInit = Scene.v().getMethod("<java.lang.StringBuilder: void <init>()>");
            SootMethod sbAppendString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>");
            SootMethod sbAppendLong = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(long)>");
            SootMethod sbToString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.String toString()>");
            // SootMethod printerMethod = Scene.v().getSootClass("MandolineLogger").getMethod("void println(java.lang.String)");
            SootMethod printerMethod = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");

            if (addedLocals.startTimer==null && flags.timeTracking && flags.isCallbackOrThread) {
                addedLocals.startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
            }
            if (addedLocals.threadId == null && flags.threadTracking) {
                addedLocals.threadId = InstrumenterUtils.insertThreadTracking(u, b);
            }
            if (addedLocals.tmpString == null) {
                addedLocals.tmpString = InstrumenterUtils.addTmpString(b);
            }
            if (addedLocals.sb == null) {
                addedLocals.sb = InstrumenterUtils.addStringBuilder(b);
            }

            if (addedLocals.printer == null) {
                addedLocals.printer = InstrumenterUtils.addPrintStream(b);
            }

            Local endTime = Jimple.v().newLocal("endTime", LongType.v());
            b.getLocals().add(endTime);
            SootMethod getTime = Scene.v().getSootClass("java.lang.System").getMethod("long nanoTime()");
            Unit temp = null;

            temp = Jimple.v().newAssignStmt(endTime, Jimple.v().newStaticInvokeExpr(getTime.makeRef()));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            units.insertBefore(Jimple.v().newAssignStmt(
                addedLocals.printer, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);

            temp = Jimple.v().newAssignStmt(addedLocals.sb, Jimple.v().newNewExpr( RefType.v("java.lang.StringBuilder")));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            temp = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr (addedLocals.sb, sbInit.makeRef()));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendString.makeRef(), StringConstant.v("Timer-Method:"+b.getMethod().getSignature()+":")));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendLong.makeRef(), addedLocals.startTimer));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendString.makeRef(), StringConstant.v(":")));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbAppendLong.makeRef(), endTime));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            temp = Jimple.v().newAssignStmt(addedLocals.tmpString, Jimple.v().newVirtualInvokeExpr (addedLocals.sb, sbToString.makeRef()));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            temp = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(addedLocals.printer, printerMethod.makeRef(), addedLocals.tmpString));
            instrumentedUnits.add(temp);
            units.insertBefore(temp, u);

            // temp = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(printerMethod.makeRef(), addedLocals.tmpString));
            // instrumentedUnits.add(temp);
            // units.insertBefore(temp, u);
        }
        b.validate();
    }
}



class StmtSwitch extends AbstractStmtSwitch{
    
    boolean threadInserted = false;
    boolean isOriginal = false;
    boolean isCallback = false;
    boolean isCallbackOrThread = false;
    boolean timeTracking = false;
    Local startTimer = null;
    SootClass cls;
    SootMethod mtd;
    Unit u;
    Body b;
    PatchingChain<Unit> units;
    StmtSwitch(){
        this.threadInserted = false;
        this.isCallbackOrThread = false;
        this.timeTracking = false;
    }

    public void setOriginal(boolean isOriginal) {
        this.isOriginal = isOriginal;
    }

    public void setCallbackOrThread(boolean isCallbackOrThread) {
        this.isCallbackOrThread = isCallbackOrThread;
    }

    public void setTimeTracking(boolean timeTracking) {
        this.timeTracking = timeTracking;
    }

    public void setU(Unit u) {
        this.u = u;
    }

    public void setB(Body b) {
        this.b = b;
    }

    public void setUnits(PatchingChain<Unit> units) {
        this.units = units;
    }

    public void setClassAndMethod(SootClass c, SootMethod m) {
        this.cls=c;
        this.mtd=m;
    }

    public void setCallback(boolean c) {
        this.isCallback = c;
    }

    public void caseInvokeStmt(InvokeStmt stmt) {
        Local tmpRef = InstrumenterUtils.addPrintStream(b);
        Local tmpString = InstrumenterUtils.addTmpString(b);
        String payload = InstrumenterUtils.getPayload(TYPE.INST, u, cls, mtd, b);
        if(isCallback)
        {
            payload = "CALLBACK_SLC: " + payload;
            isCallback = false;
        }

        if (startTimer==null && timeTracking && isCallbackOrThread) {
            startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }
        if (!isOriginal) {
            units.insertBefore(Jimple.v().newAssignStmt(
                tmpRef, Jimple.v().newStaticFieldRef(
                        Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);

            units.insertBefore(Jimple.v().newAssignStmt(tmpString, 
                    StringConstant.v(payload)), u);
            
            SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
        }
        b.validate();
    }
    
    
    public void caseAssignStmt(AssignStmt stmt) {
        Local tmpRef = InstrumenterUtils.addPrintStream(b);
        Local tmpString = InstrumenterUtils.addTmpString(b);
        String payload = InstrumenterUtils.getPayload(TYPE.INST, u, cls, mtd, b);
        if(isCallback)
        {
            payload = "CALLBACK_SLC: " + payload;
            isCallback = false;
        }

        if (startTimer==null && timeTracking && isCallbackOrThread) {
            startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (!isOriginal) {
            units.insertBefore(Jimple.v().newAssignStmt(
                    tmpRef, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);
            
            units.insertBefore(Jimple.v().newAssignStmt(tmpString,
                    StringConstant.v(payload)), u);
            
            SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
        }
        b.validate();
    }
        
    
    public void caseDefinitionStmt(DefinitionStmt stmt) {
        Local tmpRef = InstrumenterUtils.addPrintStream(b);
        Local tmpString = InstrumenterUtils.addTmpString(b);
        String payload = InstrumenterUtils.getPayload(TYPE.INST, u, cls, mtd, b);
        if(isCallback)
        {
            payload = "CALLBACK_SLC: " + payload;
            isCallback = false;
        }

        if (startTimer==null && timeTracking && isCallbackOrThread) {
            startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (!isOriginal) {
            units.insertBefore(Jimple.v().newAssignStmt(
                    tmpRef, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);

            units.insertBefore(Jimple.v().newAssignStmt(tmpString,
                    StringConstant.v(payload)), u);
            
            SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
        }
        b.validate();
    }
    
    public void caseLookupSwitchStmt(LookupSwitchStmt stmt) {
        Local tmpRef = InstrumenterUtils.addPrintStream(b);
        Local tmpString = InstrumenterUtils.addTmpString(b);
        String payload = InstrumenterUtils.getPayload(TYPE.INST, u, cls, mtd, b);
        if(isCallback) {
            payload = "CALLBACK_SLC: " + payload;
            isCallback = false;
        }

        if (startTimer==null && timeTracking && isCallbackOrThread) {
            startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (!isOriginal) {
            units.insertBefore(Jimple.v().newAssignStmt(
                    tmpRef, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);

            units.insertBefore(Jimple.v().newAssignStmt(tmpString,
                    StringConstant.v(payload)), u);
            
            SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
        }
        b.validate();
    }
    public void caseSwitchStmt(SwitchStmt stmt) {
        Local tmpRef = InstrumenterUtils.addPrintStream(b);
        Local tmpString = InstrumenterUtils.addTmpString(b);
        String payload = InstrumenterUtils.getPayload(TYPE.INST, u, cls, mtd, b);
        if(isCallback)
        {
            payload = "CALLBACK_SLC: " + payload;
            isCallback = false;
        }

        if (startTimer==null && timeTracking && isCallbackOrThread) {
            startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (!isOriginal) {
            units.insertBefore(Jimple.v().newAssignStmt(
                    tmpRef, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);

            units.insertBefore(Jimple.v().newAssignStmt(tmpString,
                    StringConstant.v(payload)), u);
            
            SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
        }
        b.validate();
    }
    public void caseIfStmt(IfStmt stmt) {
        Local tmpRef = InstrumenterUtils.addPrintStream(b);
        Local tmpString = InstrumenterUtils.addTmpString(b);
        String payload = InstrumenterUtils.getPayload(TYPE.INST, u, cls, mtd, b);
        if(isCallback)
        {
            payload = "CALLBACK_SLC: " + payload;
            isCallback = false;
        }

        if (startTimer==null && timeTracking && isCallbackOrThread) {
            startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (!isOriginal) {
            units.insertBefore(Jimple.v().newAssignStmt(
                    tmpRef, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);

            units.insertBefore(Jimple.v().newAssignStmt(tmpString,
                    StringConstant.v(payload)), u);
            
            SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
        }
        b.validate();
    }
    
    public void caseStmt(Stmt stmt) {
        Local tmpRef = InstrumenterUtils.addPrintStream(b);
        Local tmpString = InstrumenterUtils.addTmpString(b);
        String payload = InstrumenterUtils.getPayload(TYPE.INST, u, cls, mtd, b);
        if(isCallback)
        {
            payload = "CALLBACK_SLC: " + payload;
            isCallback = false;
        }

        if (startTimer==null && timeTracking && isCallbackOrThread) {
            startTimer = InstrumenterUtils.insertStartTimeTracking(u, b);
        }

        if (!isOriginal) {
            units.insertBefore(Jimple.v().newAssignStmt(
                    tmpRef, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);
            
            units.insertBefore(Jimple.v().newAssignStmt(tmpString,
                    StringConstant.v(payload)), u);
            
            SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
        }
        b.validate();
    }


    @Override
    public void caseThrowStmt(ThrowStmt stmt) {
        instrumentEnd();
    }

    @Override
    public void caseReturnStmt(ReturnStmt stmt) {
        instrumentEnd();
    }

    @Override
    public void caseReturnVoidStmt(ReturnVoidStmt stmt) {
        instrumentEnd();
    }

    private void instrumentEnd() {
        if (timeTracking && isCallbackOrThread) {
            if (startTimer == null) {
                return;
            }
            Local tmpRef = InstrumenterUtils.addPrintStream(b);
            Local tmpString = InstrumenterUtils.addTmpString(b);
            Local sb = InstrumenterUtils.addStringBuilder(b);

            SootMethod sbInit = Scene.v().getMethod("<java.lang.StringBuilder: void <init>()>");
            SootMethod sbAppendString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>");
            SootMethod sbAppendLong = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(long)>");
            SootMethod sbToString = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.String toString()>");
            // SootMethod printerMethod = Scene.v().getSootClass("MandolineLogger").getMethod("void println(java.lang.String)");
            SootMethod printerMethod = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");

            Local endTime = Jimple.v().newLocal("endTime", LongType.v());
            b.getLocals().add(endTime);
            SootMethod getTime = Scene.v().getSootClass("java.lang.System").getMethod("long nanoTime()");
            units.insertBefore(Jimple.v().newAssignStmt(endTime,
                Jimple.v().newStaticInvokeExpr(getTime.makeRef())), u);
            units.insertBefore(Jimple.v().newAssignStmt(
                    tmpRef, Jimple.v().newStaticFieldRef(
                            Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);
            units.insertBefore(Jimple.v().newAssignStmt(
                sb, Jimple.v().newNewExpr( RefType.v("java.lang.StringBuilder"))), u);
            units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr (sb, sbInit.makeRef())), u);
            units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr (sb, sbAppendString.makeRef(), StringConstant.v("Timer-Method:"+b.getMethod().getSignature()+":"))), u);
            units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr (sb, sbAppendLong.makeRef(), startTimer)), u);
            units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr (sb, sbAppendString.makeRef(), StringConstant.v(":"))), u);
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr (sb, sbAppendLong.makeRef(), endTime)), u);
            units.insertBefore(Jimple.v().newAssignStmt(tmpString,
                Jimple.v().newVirtualInvokeExpr (sb, sbToString.makeRef())), u);
            units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(tmpRef, printerMethod.makeRef(), tmpString)), u);

            // units.insertBefore(Jimple.v().newInvokeStmt(
            //     Jimple.v().newStaticInvokeExpr(printerMethod.makeRef(), tmpString)), u);
        }
        b.validate();
    }
}

class AddedLocals {
    Local startTimer;
    Local threadId;
    Local tmpString;
    Local sb;
    Local tagString;
    Local printer;
}

class Flags {
    boolean timeTracking;
    boolean threadTracking;
    boolean fieldTracking;
    boolean isCallbackOrThread;
    boolean threadIdInserted;
    boolean isOriginal;
    boolean superCalled;
    Flags(boolean timeTracking, boolean threadTracking, boolean fieldTracking, boolean isCallbackOrThread, boolean threadIdInserted, boolean isOriginal){
       this.timeTracking = timeTracking;
       this.threadTracking = threadTracking;
       this.fieldTracking = fieldTracking;
       this.isCallbackOrThread = isCallbackOrThread;
       this.threadIdInserted = threadIdInserted;
       this.isOriginal = isOriginal;
       this.superCalled = false;
    }
}

class GlobalCounter {
    AtomicLong counter = new AtomicLong(0L);
    public long inc(){
        return counter.incrementAndGet();
    }
    @Override
    public String toString() {
        return String.valueOf(counter.get());
    }
}


enum TYPE {DIRECTOR, HEAD, TAIL, DEF, INST};



