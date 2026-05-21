package ca.ubc.ece.resess.slicer.dynamic.mandoline.instrumenter;


import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import soot.Body;
import soot.BodyTransformer;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Trap;
import soot.Unit;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.options.Options;
import soot.toolkits.scalar.Pair;
import soot.util.Chain;
import soot.util.MultiMap;
import soot.jimple.IdentityStmt;
import soot.jimple.ThrowStmt;
import soot.jimple.infoflow.android.callbacks.AndroidCallbackDefinition;



import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import ca.ubc.ece.resess.slicer.dynamic.core.instrumenter.Instrumenter;
import ca.ubc.ece.resess.slicer.dynamic.core.instrumenter.InstrumenterUtils;
import ca.ubc.ece.resess.slicer.dynamic.core.instrumenter.InstrumentationCounter;
import ca.ubc.ece.resess.slicer.dynamic.core.instrumenter.StmtSwitch;
import ca.ubc.ece.resess.slicer.dynamic.core.instrumenter.AddedLocals;
import ca.ubc.ece.resess.slicer.dynamic.core.instrumenter.Flags;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;


public class AndroidInstrumenter extends Instrumenter {
    private static final String LOGGER_CLASS = "DynamicSlicingLogger";
    private static final String WRITER_CLASS = "DynamicSlicingLoggerWriter";
    private static final String SHUTDOWN_CLASS = "DynamicSlicingLoggerShutdown";
    Map<SootClass, SootMethod> callbackMethods = new HashMap<>();
    Set<String> threadMethods = new HashSet<>();
    private boolean fieldTracking = false;
    private boolean threadTracking = false;
    private boolean timeTracking = false;
    private boolean isAndroidSlicer = false;
    private boolean isOriginal = false;
    private Long appSize = 0L;
    JsonObject staticLog = new JsonObject();
    InstrumentationCounter globalLineCounter = new InstrumentationCounter();
    Chain<SootClass> libClasses = null;

    void initialize() {
        Options.v().set_src_prec(Options.src_prec_apk);
        createInstrumentationPackagesList();
        Scene.v().addBasicClass("java.io.PrintStream",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System",SootClass.SIGNATURES);
        Scene.v().addBasicClass(LOGGER_CLASS, SootClass.BODIES);
        Scene.v().addBasicClass(WRITER_CLASS, SootClass.BODIES);
        Scene.v().addBasicClass(SHUTDOWN_CLASS, SootClass.BODIES);
        libClasses = Scene.v().getLibraryClasses();
    }
    
    public AndroidInstrumenter(MultiMap<SootClass,AndroidCallbackDefinition> cm, Map<Pair<SootMethod,Unit>,String> tc) {
        for (AndroidCallbackDefinition acd: cm.values()){
            this.callbackMethods.put(acd.getTargetMethod().getDeclaringClass(), acd.getTargetMethod());
        }
        this.threadMethods.addAll(tc.values());
    }
   
    
    void runMethodTransformationPack() {
        PackManager.v().getPack("wjpp").add(new Transform("wjpp.classadder", new SceneTransformer(){
            @Override
            protected void internalTransform(String phaseName, Map<String, String> options) {
                Scene.v().getSootClass(LOGGER_CLASS).setApplicationClass();
                Scene.v().getSootClass(WRITER_CLASS).setApplicationClass();
                Scene.v().getSootClass(SHUTDOWN_CLASS).setApplicationClass();
                if (!isAndroidSlicer && !isOriginal) {
                    for (SootClass cls: Scene.v().getClasses()) {
                        try {
                            if (cls.getSuperclass().getName().contains("Activity") || cls.getSuperclass().getName().contains("Service")) {
                                Set<String> mSet = new HashSet<>();
                                for (SootMethod m: cls.getMethods()){
                                    mSet.add(m.getName());
                                }
                                if (!mSet.contains("onDestroy")) {
                                    SootMethod onDestroyMethod = InstrumenterUtils.createDestroyMethod(cls, cls.getSuperclass().getMethodByName("onDestroy"));
                                    cls.addMethod(onDestroyMethod);
                                    mSet.add(onDestroyMethod.getName());
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        }));


        PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", new BodyTransformer() {
            @Override
            protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {
                SootClass cls = b.getMethod().getDeclaringClass();
                if (cls.getName().contains(LOGGER_CLASS)) {
                    return;
                }
                if (cls.getName().contains(WRITER_CLASS)) {
                    return;
                }
                if (cls.getName().contains(SHUTDOWN_CLASS)) {
                    return;
                }
                if (cls.getName().startsWith("android.")) {
                    return;
                }
                if (cls.getName().startsWith("androidx.")) {
                    return;
                }
                boolean skip = !AndroidInstrumenter.this.instrumentationPackagesList.isEmpty();
                for (String includedPkg: AndroidInstrumenter.this.instrumentationPackagesList) {
                    if (cls.getPackageName().startsWith(includedPkg) ) {
                        skip = false;
                    }
                }
                if (skip) {
                    return;
                }

                Long methodSize = 0L;
                SootMethod mtd = b.getMethod();
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
                }

                List<String> traps = new ArrayList<>();
                for (Trap trap: mtd.getActiveBody().getTraps()) {
                    traps.add(trap.getBeginUnit().toString());
                }

                final PatchingChain<Unit> units = b.getUnits();
                Set<Unit> instrumentedUnits = new HashSet<>();
                boolean instrumentedFirst = false;
                LinkedHashMap<Unit, Long> unitNumMap = new LinkedHashMap<>();
                Map<Unit, Long> taggedUnits = new HashMap<>();
                for(Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
                    final Unit u = (Unit) iter.next();
                    if (!(u instanceof IdentityStmt)) {
                        methodSize += 1;
                    }
                    if (isAndroidSlicer) {
                        stmtSwitch.setClassAndMethod(cls, mtd);
                        if (threadMethods.contains(b.getMethod().getSubSignature()) ||
                            callbackMethods.values().contains(b.getMethod()) || 
                            b.getMethod().getName().startsWith("on")) {
                                stmtSwitch.setCallbackOrThread(true);
                        }
                        if(b.getMethod().getName().startsWith("on")) {
                            stmtSwitch.setCallback(true);
                        }
                        stmtSwitch.setU(u);
                        stmtSwitch.setB(b);
                        stmtSwitch.setUnits(units);
                        u.apply(stmtSwitch);
                    } else {
                        instrumentedFirst = InstrumenterUtils.basicBlockInstrument(b, cls, mtd, isOnDestroy, addedLocals, flags, units,
                                                                instrumentedUnits, instrumentedFirst, unitNumMap, taggedUnits, u, traps,
                                                                globalLineCounter, threadMethods, libClasses);
                    }
                    
                }
                synchronized (appSize) {
                    appSize += methodSize;
                }
                JsonObject job = new JsonObject();
                String key = "";
                JsonArray jArray = new JsonArray();
                Unit prevU = null;
                for (Unit u : unitNumMap.keySet()) {
                    if (u instanceof IdentityStmt) {
                        continue;
                    }
                    if (taggedUnits.containsKey(u)) {
                        if (!key.equals("")){
                            job.add(key, jArray);
                            jArray = new JsonArray();
                        }
                        key = taggedUnits.get(u).toString();
                    } else if (prevU != null && (prevU instanceof ReturnStmt || prevU instanceof ReturnVoidStmt || prevU instanceof ThrowStmt)) {
                        if (!key.equals("")){
                            job.add(key, jArray);
                            jArray = new JsonArray();
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
                    job.add(key, jArray);
                }
                
                synchronized(staticLog){
                    staticLog.add(b.getMethod().getSignature(), job);
                }
            }
        }));
        
    }

    public void start (String args[]) {
        soot.G.reset();
        AnalysisLogger.log(true, "Soot args: {}", Arrays.asList(args));
        if (args[0].contains("field")) {
            this.fieldTracking = true;
        }
        if (args[0].contains("thread")) {
            this.threadTracking = true;
        }
        if (args[0].contains("time")) {
            this.timeTracking = true;
        }
        if (args[0].contains("original")) {
            this.isOriginal = true;
        }
        if (args[0].contains("androidslicer")) {
            this.isAndroidSlicer = true;
        }
        String staticLogFile = args[1];
        initialize();
        runMethodTransformationPack();
        int argLen = args.length-2;
        String newArgs[] = new String [argLen];
        for (int ii = 2; ii < args.length; ii++) {
            newArgs[ii-2]=args[ii];
        }
        AnalysisLogger.log(true, "Soot args: {}", Arrays.asList(newArgs));
        soot.Main.main(newArgs);    
        File logFile = new File(staticLogFile);
        Gson gson = new GsonBuilder()
                        .disableHtmlEscaping()
                        .create();
        try {
            logFile.delete();
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(staticLogFile), StandardCharsets.UTF_8));
            JsonWriter writer = new JsonWriter(out);
            gson.toJson(staticLog, writer);
            writer.close();
            
        } catch (IOException e) {
            throw new Error("Failed to write static log file");
            
        }
        File sizeFile = new File("apk-size.txt");
        try {
            sizeFile.delete();
            FileUtils.writeStringToFile(sizeFile, "Number of Jimple statements (apkSize): " + appSize.toString(), "UTF-8", true);
            
        } catch (IOException e) {
            throw new Error("Failed to write static log file");
        }
        AnalysisLogger.log(true, "Number of Jimple statements (apkSize): {}", appSize.toString());
    }
}
