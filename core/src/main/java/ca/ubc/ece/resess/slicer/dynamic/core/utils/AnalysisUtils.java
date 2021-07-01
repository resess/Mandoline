package ca.ubc.ece.resess.slicer.dynamic.core.utils;

import java.util.List;


import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import soot.ArrayType;
import soot.PrimType;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

public class AnalysisUtils {

    private AnalysisUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isInteger(String s) {
        try { 
            Integer.parseInt(s);
        } catch(NumberFormatException | NullPointerException e) {
            return false;
        }
        return true;
    }

    public static boolean isPrimitiveType(Value v){
        Type t = v.getType();
        if (v.getType() instanceof ArrayType) {
            t = ((ArrayType) v.getType()).getArrayElementType();
        }
        if (t instanceof PrimType) {
            return true;
        }
        return false;
    }

    public static boolean noCommons(List<String> p1, List<String> p2, List<Type> t1, List<Type> t2) {
        for (int i = 0; i < p1.size(); i++) {
            for (int j = 0; j < p2.size(); j++) {
                if (p1.get(i).equals(p2.get(j))) {
                    if (t1.get(i).equals(t2.get(j))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean matchingType(AliasSet taintSet, StatementInstance caller) {
        for (AccessPath accessPath: taintSet) {
            Type t = caller.getMethod().getDeclaringClass().getType();
            int index = accessPath.classIndex(t);
            if (index != -1) {
                return true;
            }
        }
        return false;
    }


    public static InvokeExpr getCallerExp(StatementInstance caller) throws Error {
        InvokeExpr callerExp = null;
        if (caller == null) {
            return callerExp;
        }
        try {
            if (caller.getUnit() instanceof IfStmt) {
                callerExp = ((IfStmt) caller.getUnit()).getTarget().getInvokeExpr();
            } else if (caller.getUnit() instanceof Stmt) {
                callerExp = ((Stmt) caller.getUnit()).getInvokeExpr();
            } 
        } catch (Exception e) {
            return null;
        }
        return callerExp;
    }

    public static Object stackTraceToString(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append(ste.toString());
            sb.append("\n");
            if (i > 7) {
                break;
            }
        }
        return sb.toString();
    }

    public static boolean isAndroidMethod(StatementInstance stmt, AccessPath var) {
        if (stmt.getMethod().getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS) || var.getField().equals("mContext")) {
            return true;
        }
        return false;
    }
    
    public static boolean isMethodParameter(StatementInstance si, AccessPath ap) {
        for (Unit uu: si.getMethod().getActiveBody().getUnits()) {
            if (uu instanceof IdentityStmt) {
                if (uu.toString().contains("@this") || uu.toString().contains("@parameter")) {
                    String base = uu.getDefBoxes().get(0).getValue().toString();
                    if (ap.getPathString().equals(base)) {
                        return true;
                    }
                }
            } else {
                break;
            }
        }

        return false;
    }
}