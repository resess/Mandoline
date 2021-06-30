package ca.ubc.ece.resess.slicer.dynamic.core.instrumenter;


import soot.Body;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.LongType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.IfStmt;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.StringConstant;
import soot.jimple.SwitchStmt;
import soot.jimple.ThrowStmt;
import soot.jimple.Stmt;


public class StmtSwitch extends AbstractStmtSwitch {
    
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
    public StmtSwitch(){
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