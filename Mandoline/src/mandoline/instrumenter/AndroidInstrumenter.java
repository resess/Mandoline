package mandoline.instrumenter;

import java.io.File;
import java.io.IOException;
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
import soot.jimple.infoflow.android.callbacks.AndroidCallbackDefinition;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import mandoline.utils.AnalysisLogger;


public class AndroidInstrumenter extends Instrumenter{
    Map<SootClass, SootMethod> callbackMethods = new HashMap<>();
    Set<String> threadMethods = new HashSet<>();
    private boolean fieldTracking = false;
    private boolean threadTracking = false;
    private boolean timeTracking = false;
    private boolean isAndroidSlicer = false;
    private boolean isOriginal = false;
    private Long appSize = 0L;
    JSONObject staticLog = new JSONObject();
    GlobalCounter globalLineCounter = new GlobalCounter();
    Chain<SootClass> libClasses = null;
    void initialize(String pkgName2) {
        Options.v().set_src_prec(Options.src_prec_apk);
        String pkgName = pkgName2.replace("'", "");
        if(pkgName.contains("/"))
        {
            String[] pkgNameArray = pkgName.split("/");
            pkgName = pkgNameArray[pkgNameArray.length-1];
        }
        System.out.println ("pkg: "+pkgName);
        Scene.v().addBasicClass("java.io.PrintStream",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System",SootClass.SIGNATURES);
        Scene.v().addBasicClass("MandolineLogger", SootClass.BODIES);
        Scene.v().addBasicClass("MandolineWriter", SootClass.BODIES);
        Scene.v().addBasicClass("MandolineShutdown", SootClass.BODIES);
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
                Scene.v().getSootClass("MandolineLogger").setApplicationClass();
                Scene.v().getSootClass("MandolineWriter").setApplicationClass();
                Scene.v().getSootClass("MandolineShutdown").setApplicationClass();
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
                if (cls.getName().contains("MandolineLogger")) {
                    return;
                }
                if (cls.getName().contains("MandolineWriter")) {
                    return;
                }
                if (cls.getName().contains("MandolineShutdown")) {
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
                    flags.threadTracking = false;
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
                        instrumentedFirst = basicBlockInstrument(b, cls, mtd, isOnDestroy, addedLocals, flags, units,
                                                                instrumentedUnits, instrumentedFirst, unitNumMap, taggedUnits, u);
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
                    final Unit u) {
                unitNumMap.put(u, -1L);
                if (threadMethods.contains(b.getMethod().getSubSignature()) ||
                    callbackMethods.values().contains(b.getMethod()) || 
                    b.getMethod().getName().startsWith("on")) {
                        flags.isCallbackOrThread = true;
                }
                if (instrumentedUnits.contains(u)) {
                    return instrumentedFirst;
                }
                if (isOnDestroy && !instrumentedFirst &&!(u instanceof IdentityStmt)) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addFlush(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
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
                } else if (u instanceof GotoStmt) {
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
                } else if (u instanceof InvokeStmt) {
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
                } else if ((u instanceof AssignStmt) && ((AssignStmt) u).containsInvokeExpr()) {
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
                } else if (u instanceof ReturnVoidStmt) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                        InstrumenterUtils.insertEndTimeTracking(u, units, b, addedLocals, flags, instrumentedUnits, taggedUnits);
                    }
                } else if (u instanceof ReturnStmt) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                        InstrumenterUtils.insertEndTimeTracking(u, units, b, addedLocals, flags, instrumentedUnits, taggedUnits);
                    }
                } else if (u instanceof ThrowStmt) {
                    if (!instrumentedUnits.contains(u)) {
                        InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                        instrumentedUnits.add(u);
                        InstrumenterUtils.insertEndTimeTracking(u, units, b, addedLocals, flags, instrumentedUnits, taggedUnits);
                    }
                } else if (u instanceof AssignStmt && !(u instanceof IdentityStmt)){
                    if (((AssignStmt) u).containsFieldRef()) {
                        if(flags.fieldTracking) {
                            if (!instrumentedUnits.contains(u)) {
                                InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                                instrumentedUnits.add(u);
                            }
                        }
                    }
                } else if (u instanceof Stmt && !(u instanceof IdentityStmt)){
                    if (!instrumentedFirst ) {
                        if (!instrumentedUnits.contains(u)) {
                            InstrumenterUtils.addPrint(u, units, b, addedLocals, cls, mtd, flags, instrumentedUnits, taggedUnits, globalLineCounter);
                            instrumentedUnits.add(u);
                        }
                        instrumentedFirst = true;
                    }
                } else {
                    // pass
                }
                return instrumentedFirst;
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
        initialize(args[2]);
        runMethodTransformationPack();
        int argLen = args.length-3;
        String newArgs[] = new String [argLen];
        for (int ii = 3; ii < args.length; ii++) {
            newArgs[ii-3]=args[ii];
        }
        AnalysisLogger.log(true, "Soot args: {}", Arrays.asList(newArgs));
        soot.Main.main(newArgs);
        File logFile = new File(staticLogFile);
        try {
            logFile.delete();
            FileUtils.writeStringToFile(logFile, staticLog.toString(), "UTF-8", true);
            
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
