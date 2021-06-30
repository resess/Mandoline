package ca.ubc.ece.resess.slicer.dynamic.core.slicer;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;
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
    private static final Integer IN_MAP = 1;
    private Map<String, Integer> visited = new ConcurrentHashMap<>();

    public SlicingWorkingSet(boolean stopSlicing){
        this.stopSlicing = stopSlicing;
    }

    public DynamicSlice getDynamicSlice() {
        return dynamicSlice;
    }

    public void setStopSlicing(boolean stopSlicing) {
        this.stopSlicing = stopSlicing;
    }

    public void addMultiple(StatementInstance iu, Set<AccessPath> variables, String edgeType){
        for (AccessPath var: variables) {
            Pair<StatementInstance, AccessPath> frontier = new Pair<>(iu, var);
            this.add(iu, var, frontier, edgeType);
        }
    }

    public synchronized void add(StatementInstance iu, AccessPath var, Pair<StatementInstance, AccessPath> source, String edgeType){
        Pair<StatementInstance, AccessPath> frontier = new Pair<>(iu, var);
        String frontierLine = iu.getLineNo() +":"+ var.getPathString();
        if (!this.stopSlicing) {
            if (!isVisited(frontierLine)) {
                AnalysisLogger.log(Constants.DEBUG, "Added to working set: {}", frontier);
                this.add(frontier);
                visited.put(frontierLine, IN_MAP);
            }
        }
        
        if (!this.dynamicSlice.hasEdge(frontier.getO1().getLineNo(), source.getO1().getLineNo(), edgeType)) {
            dynamicSlice.add(frontier, source, edgeType);
        }
    }

    private boolean isVisited(String pos) {
        return visited.containsKey(pos);
    }

    public void addStmtOnly (StatementInstance si, Pair<StatementInstance, AccessPath> source, String edgeType){
        this.add(si, new AccessPath(si.getLineNo(), AccessPath.NOT_DEFINED, si), source, edgeType);
    }

    public synchronized void addMethodOfStmt(StatementInstance si, Pair<StatementInstance, AccessPath> source) {
        dynamicSlice.addMethod(source.getO1().getLineNo(), si.getLineNo());
    }

    public void addStmt(StatementInstance si, Pair<StatementInstance, AccessPath> source, String edgeType){
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
                this.add(si, var, source, edgeType);
            } else {
                if (((Stmt) u).containsInvokeExpr()) {
                    for(ValueBox vb:useBoxes) {
                        if(vb.getValue() instanceof Local) {
                            var = new AccessPath(vb.getValue().toString(), vb.getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                            this.add(si, var, source, edgeType);
                        }
                    }
                } else {
                    if (r instanceof Local) {
                        var = new AccessPath(r.toString(), r.getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                        this.add(si, var, source, edgeType);
                    } else {
                        for(ValueBox vb:r.getUseBoxes()) {
                            if(vb.getValue() instanceof Local) {
                                var = new AccessPath(vb.getValue().toString(), vb.getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                                this.add(si, var, source, edgeType);
                            }
                        }
                    }
                }
            }
        } else {
            for(ValueBox vb:useBoxes) {
                if(vb.getValue() instanceof Local) {
                    var = new AccessPath(vb.getValue().toString(), vb.getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                    this.add(si, var, source, edgeType);
                }
            }
        }

        if (var.isEmpty()) {
            this.add(si, var, source, edgeType);
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