package mandoline.slicer;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import mandoline.accesspath.AccessPath;
import mandoline.statements.StatementInstance;
import soot.toolkits.scalar.Pair;
import soot.Local;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;

public class SlicingWorkingSet extends ArrayDeque<Pair<StatementInstance, AccessPath>> {
    
    private static final long serialVersionUID = 1L;
    private boolean stopSlicing;

    DynamicSlice dynamicSlice = new DynamicSlice();

    SlicingWorkingSet(boolean stopSlicing){
        this.stopSlicing = stopSlicing;
    }

    public DynamicSlice getDynamicSlice() {
        return dynamicSlice;
    }

    public void setStopSlicing(boolean stopSlicing) {
        this.stopSlicing = stopSlicing;
    }

    void addMultiple(StatementInstance iu, Set<AccessPath> variables){
        for (AccessPath var: variables) {
            Pair<StatementInstance, AccessPath> frontier = new Pair<>(iu, var);
            this.add(iu, var, frontier);
        }
    }

    void add(StatementInstance iu, AccessPath var, Pair<StatementInstance, AccessPath> source){
        Pair<StatementInstance, AccessPath> frontier = new Pair<>(iu, var);
        if (!this.stopSlicing) {
            if (!this.dynamicSlice.containsKey(frontier)) {
                this.add(frontier);
            }
        }
        dynamicSlice.put(frontier, source);
    }

    void addStmtOnly (StatementInstance si, Pair<StatementInstance, AccessPath> source){
        this.add(si, new AccessPath(si.getLineNo(), AccessPath.NOT_DEFINED, si), source);
    }

    void addMethodOfStmt(StatementInstance si, Pair<StatementInstance, AccessPath> source){
        dynamicSlice.addMethod(source.getO1().getLineNo(), si.getLineNo());
    }

    void addStmt(StatementInstance si, Pair<StatementInstance, AccessPath> source){
        Unit u = si.getUnit();
        List<ValueBox> useBoxes = u.getUseBoxes();
        AccessPath var = new AccessPath(si.getLineNo(), AccessPath.NOT_DEFINED, si);
        if (u instanceof AssignStmt) {
            AssignStmt stmt = (AssignStmt) u;
            Value r = stmt.getRightOp();
            if (r instanceof FieldRef) {
                if (((FieldRef) r).getUseBoxes().size() == 0) {
                    var = new AccessPath(((FieldRef) r).getField().getDeclaringClass().getName(), ((FieldRef) r).getField().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                    var.setStaticField();
                } else {
                    for (ValueBox v: ((FieldRef) r).getUseBoxes()){
                        var = new AccessPath(v.getValue().toString(), v.getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                    }
                }
                var.add(((FieldRef) r).getField().getName(), ((FieldRef) r).getField().getType(), si);
                this.add(si, var, source);
            } else {
                if (((Stmt) u).containsInvokeExpr()) {
                    for(ValueBox vb:useBoxes) {
                        if(vb.getValue() instanceof Local) {
                            var = new AccessPath(vb.getValue().toString(), vb.getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                            this.add(si, var, source);
                        }
                    }
                } else {
                    if (r instanceof Local) {
                        var = new AccessPath(r.toString(), r.getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                        this.add(si, var, source);
                    } else {
                        for(ValueBox vb:r.getUseBoxes()) {
                            if(vb.getValue() instanceof Local) {
                                var = new AccessPath(vb.getValue().toString(), vb.getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                                this.add(si, var, source);
                            }
                        }
                    }
                }
            }
        } else {
            for(ValueBox vb:useBoxes) {
                if(vb.getValue() instanceof Local) {
                    var = new AccessPath(vb.getValue().toString(), vb.getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                    this.add(si, var, source);
                }
            }
        }

        if (var.isEmpty()) {
            this.add(si, var, source);
        }
    }

    public void removeAllWithStmt(StatementInstance dom) {
        Set<Pair<StatementInstance, AccessPath>> toRemove = new LinkedHashSet<>();
        for (Pair<StatementInstance, AccessPath> p : this) {
            if (p.getO1().equals(dom)) {
                toRemove.add(p);
            }
        }
        this.removeAll(toRemove);
    }
}