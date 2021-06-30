package ca.ubc.ece.resess.slicer.dynamic.core.instrumenter;



import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


import soot.Body;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.LongType;
import soot.Modifier;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StringConstant;
import soot.Value;
import soot.VoidType;
import soot.IntType;
import soot.jimple.InstanceFieldRef;
import soot.jimple.StaticFieldRef;


public class InstrumenterUtils {


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


    public static void addFlush(Unit u, PatchingChain<Unit> units, Body b, AddedLocals addedLocals, SootClass cls, SootMethod mtd, Flags flags, Set<Unit> instrumentedUnits , Map<Unit, Long> taggedUnits, InstrumentationCounter globalLineCounter){
        
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


    public static void addPrint(Unit u, PatchingChain<Unit> units, Body b, AddedLocals addedLocals, SootClass cls, SootMethod mtd, Flags flags, Set<Unit> instrumentedUnits , Map<Unit, Long> taggedUnits, InstrumentationCounter globalLineCounter){
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

