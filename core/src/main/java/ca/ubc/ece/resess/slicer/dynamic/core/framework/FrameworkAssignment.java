package ca.ubc.ece.resess.slicer.dynamic.core.framework;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.exceptions.FrameworkModelException;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisUtils;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;
import soot.Type;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;

public class FrameworkAssignment {

    enum VariableType {
        RETURN, INSTANCE, PARAM, CLEAR, UNKNOW
    }

    String leftPath = "";
    String rightPath = "";
    Integer leftParam = Constants.UNKNOWN;
    Integer rightParam = Constants.UNKNOWN;
    VariableType leftType = VariableType.UNKNOW;
    VariableType rightType = VariableType.UNKNOW;

    FrameworkAssignment(Integer lp, Integer rp) {
        this(lp, rp, "", "");
    }
    

    FrameworkAssignment(Integer lp, Integer rp, String l, String r) {
        this.leftParam = lp;
        this.rightParam = rp;
        this.leftPath = l;
        this.rightPath = r;

        if (leftParam.equals(Constants.THIS)) {
            leftType = VariableType.INSTANCE;
        } else if (leftParam.equals(Constants.RETURN)) {
            leftType = VariableType.RETURN;
        } else {
            leftType = VariableType.PARAM;
        }

        if (rightParam.equals(Constants.CLEAR)) {
            rightType = VariableType.CLEAR;
        } else if (rightParam.equals(Constants.THIS)) {
            rightType = VariableType.INSTANCE;
        } else {
            rightType = VariableType.PARAM;
        }

        if (leftPath.startsWith("[")) {
            leftPath = leftPath.substring(1);
        }
        if (leftPath.endsWith("]")) {
            leftPath = leftPath.substring(0, leftPath.length()-1);
        }

        if (rightPath.startsWith("[")) {
            rightPath = rightPath.substring(1);
        }
        if (rightPath.endsWith("]")) {
            rightPath = rightPath.substring(0, rightPath.length()-1);
        }
    }


    public AccessPath getLeftParamAccessPath(InvokeExpr expr, int lineNo, StatementInstance si) {
        if (!leftType.equals(VariableType.PARAM)) {
            throw new FrameworkModelException("Getting instance access path for call with no parameter flow!");
        }
        AccessPath accessPath = new AccessPath(expr.getArg(leftParam).toString(), expr.getArg(leftParam).getType(), AccessPath.NOT_USED, lineNo, si);
        if (!leftPath.isEmpty()) {
            final String[] splitted = leftPath.split("\\s+");
            String field = splitted[2];
            accessPath.add(field, new Type(){
                private static final long serialVersionUID = 1L;
                @Override
                public String toString() {
                    return splitted[1];
                }
            }, si);
        }
        return accessPath;
    }

    public AccessPath getInstanceAccessPath(InvokeExpr expr, int lineNo, StatementInstance si) {
        if (!(expr instanceof InstanceInvokeExpr)) {
            throw new FrameworkModelException("Not an instance invoke expr!");
        }

        InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr) expr;
        AccessPath accessPath = new AccessPath(instanceExpr.getBase().toString(), instanceExpr.getBase().getType(), lineNo, lineNo, si);
        if (!leftPath.isEmpty()) {
            final String[] splitted = leftPath.split("\\s+");
            String field = splitted[2];
            accessPath.add(field, new Type(){
                private static final long serialVersionUID = 1L;
                @Override
                public String toString() {
                    return splitted[1];
                }
            }, si);
        }
        return accessPath;
    }


    public AccessPath getReturnAccessPath(AssignStmt unit, int lineNo, StatementInstance si) {
        if (!leftType.equals(VariableType.RETURN)) {
            throw new FrameworkModelException("Getting return access path for call with no return!");
        }
        AccessPath accessPath = new AccessPath(unit.getLeftOp().toString(), unit.getLeftOp().getType(), lineNo, lineNo, si);
        if (!leftPath.isEmpty()) {
            final String[] splitted = leftPath.split("\\s+");
            String field = splitted[2];
            accessPath.add(field, new Type(){
                private static final long serialVersionUID = 1L;
                @Override
                public String toString() {
                    return splitted[1];
                }
            }, si);
        }
        return accessPath;
    }

    public AccessPath getRightAccessPath(InvokeExpr invokeExpr, int lineNo, StatementInstance si) {
        AccessPath rightAccessPath;
        if (rightType.equals(VariableType.CLEAR)) {
            rightAccessPath = new AccessPath(lineNo, AccessPath.NOT_DEFINED, si);
        } else if (rightType.equals(VariableType.PARAM)) {
            if (!(invokeExpr.getArg(rightParam) instanceof Constant)&& !(AnalysisUtils.isPrimitiveType(invokeExpr.getArg(rightParam)))) {
                rightAccessPath = new AccessPath(invokeExpr.getArg(rightParam).toString(), invokeExpr.getArg(rightParam).getType(), lineNo, AccessPath.NOT_DEFINED, si);
                if (!rightPath.isEmpty()) {
                    final String[] splitted = rightPath.split("\\s+");
                    String field = splitted[2];
                    rightAccessPath.add(field, new Type(){
                        private static final long serialVersionUID = 1L;
                        @Override
                        public String toString() {
                            return splitted[1];
                        }
                    }, si);
                }
            } else {
                rightAccessPath = new AccessPath(lineNo, AccessPath.NOT_DEFINED, si);
            }
        } else if (rightType.equals(VariableType.INSTANCE)) {
            if (invokeExpr instanceof InstanceInvokeExpr) {
                rightAccessPath = new AccessPath(((InstanceInvokeExpr) invokeExpr).getBase().toString(), ((InstanceInvokeExpr) invokeExpr).getBase().getType(), lineNo, AccessPath.NOT_DEFINED, si);
            } else {
                rightAccessPath = new AccessPath(lineNo, AccessPath.NOT_DEFINED, si);
            }
        } else {
            throw new FrameworkModelException("Unknown right variable type!");
        }
        return rightAccessPath;
    }


    public Integer getLeftParam() {
        return leftParam;
    }

    public Integer getRightParam() {
        return rightParam;
    }

    public String getLeftPath() {
        return leftPath;
    }

    public String getRightPath() {
        return rightPath;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (leftType.equals(VariableType.PARAM)) {
            sb.append(leftParam);
        } else {
            sb.append(leftType.toString());
        }
        
        if (!leftPath.isEmpty()) {
            sb.append(".");
            sb.append(leftPath);
        }
        sb.append(" = ");

        if (rightType.equals(VariableType.PARAM)) {
            sb.append(rightParam);
        } else {
            sb.append(rightType.toString());
        }
        
        if (!rightPath.isEmpty()) {
            sb.append(".");
            sb.append(rightPath);
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return 17 + 
                31*leftPath.hashCode() + 
                31*rightPath.hashCode() +
                31*leftParam.hashCode() + 
                31*rightParam.hashCode() + 
                31*leftType.hashCode()+
                31*rightType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FrameworkAssignment) {
            FrameworkAssignment other = (FrameworkAssignment) obj;
            return  this.leftPath.equals(other.leftPath) && 
                    this.rightPath.equals(other.rightPath) && 
                    this.leftParam.equals(other.leftParam) && 
                    this.rightParam.equals(other.rightParam) && 
                    this.leftType.equals(other.leftType) && 
                    this.rightType.equals(other.rightType);
        }
        return false;
    }
}