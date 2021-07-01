package ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence;

import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisUtils;
import soot.SootMethod;
import soot.Value;
import soot.jimple.Constant;
import soot.jimple.InvokeExpr;
import ca.ubc.ece.resess.slicer.dynamic.mandoline.graph.ICDG;
public class SpecialDependence {
    private ICDG icdg;

    public SpecialDependence(ICDG icdg) {
        this.icdg = icdg;
    }

    public StatementInstance getSpecialDependence(StatementInstance si) {
        SootMethod called = si.getCalledMethod();
        if (called==null) {
            return null;
        }
        String signature = called.getSignature();
        switch (signature) {
            case "<android.content.SharedPreferences: java.lang.String getString(java.lang.String,java.lang.String)>":
                return sharedPrefsPutString(si);
            default: 
                return null;
        }
    }
    private StatementInstance sharedPrefsPutString(StatementInstance si) {
        AnalysisLogger.log(true, "Is shared prefs getString!");
        InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(si);
        Value key = invokeExpr.getArgs().get(0);
        if (key instanceof Constant) {
            // AnalysisLogger.log(true, "Type: {}, val: {}", key.getType(), key.toString());
        }
        StatementInstance possibleTarget = null;
        int pos = si.getLineNo();
        while (pos >= 0) {
            possibleTarget = searchForMethod(pos, "<android.content.SharedPreferences$Editor: android.content.SharedPreferences$Editor putString(java.lang.String,java.lang.String)>");
            if (possibleTarget == null) {
                break;
            }
            // AnalysisLogger.log(true, "Possible target: {}", possibleTarget);
            InvokeExpr targetInvokeExpr = AnalysisUtils.getCallerExp(possibleTarget);
            Value targetKey = targetInvokeExpr.getArgs().get(0);
            // AnalysisLogger.log(true, "key: {}, target key:{}", key, targetKey);
            if (targetKey.equals(key)) {
                break;
            }
            pos = possibleTarget.getLineNo();
        }
        if (possibleTarget != null) {
            AnalysisLogger.log(true, "Found target is: {}", possibleTarget);
        }
        return possibleTarget;
    }
    private StatementInstance searchForMethod(int start, String signature) {
        int pos = start;
        while (pos >=0) {
            pos--;
            StatementInstance stmt = icdg.mapNoUnits(pos);
            if (stmt != null) {
                SootMethod called = stmt.getCalledMethod();
                if (called!=null && called.getSignature().equals(signature)) {
                    return stmt;
                }
            }
        }
        return null;
    }

    public StatementInstance getDoInBackgroundReturn(StatementInstance si) {
        int pos = si.getLineNo();
        String classSignature = si.getMethod().getDeclaringClass().getName();
        AnalysisLogger.log(true, "Looking for {}, with clazz sig: {}", si, classSignature);
        while (pos >=0) {
            pos--;
            StatementInstance stmt = icdg.mapNoUnits(pos);
            if (stmt != null) {
                if (stmt.getMethod().getDeclaringClass().getName().equals(classSignature)) {
                    AnalysisLogger.log(true, "Inspecting stmt: {}, with clazz: {}", stmt, stmt.getMethod().getDeclaringClass().getName());
                    if (stmt.getMethod().getSubSignature().equals("java.lang.Object doInBackground(java.lang.Object[])")) {
                        return stmt;
                    }
                }
            }
        }
        return null;
    }
}
