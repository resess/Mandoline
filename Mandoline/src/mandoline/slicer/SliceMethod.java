package mandoline.slicer;

import java.util.LinkedHashSet;
import java.util.Set;

import mandoline.accesspath.AccessPath;
import mandoline.accesspath.AliasSet;
import mandoline.controldependence.ControlDominator;
import mandoline.datadependence.AliasAnalysis;
import mandoline.datadependence.CallbackDetection;
import mandoline.datadependence.DynamicHeapAnalysis;
import mandoline.framework.FrameworkModel;
import mandoline.graph.CalledChunk;
import mandoline.graph.ICDG;
import mandoline.graph.Traversal;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementMap;
import mandoline.statements.StatementSet;
import mandoline.utils.AnalysisLogger;
import mandoline.utils.AnalysisUtils;
import mandoline.utils.Constants;
import soot.Local;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

public class SliceMethod {
    private ICDG icdg;
    private Traversal traversal;
    private boolean staticAnalysis = true;
    private boolean dynamicAnalysis = false;
    private boolean frameworkModel = true;
    private boolean dataFlowsOnly = false;
    private boolean controlFlowOnly = false;
    private SlicingWorkingSet workingSet;

    SliceMethod(ICDG icdg, boolean staticAnalysis, boolean dynamicAnalysis, boolean frameworkModel, boolean dataFlowsOnly, boolean controlFlowOnly, SlicingWorkingSet workingSet) {
        this.icdg = icdg;
        this.traversal = new Traversal(icdg);
        this.staticAnalysis = staticAnalysis;
        this.dynamicAnalysis = dynamicAnalysis;
        this.frameworkModel = frameworkModel;
        this.dataFlowsOnly = dataFlowsOnly;
        this.controlFlowOnly = controlFlowOnly;
        if (workingSet != null) {
            this.workingSet = workingSet;
        } else {
            this.workingSet = new SlicingWorkingSet(false);
        }
        
    }

    public DynamicSlice slice(StatementInstance start, Set<AccessPath> variables) {
        StatementInstance firstDom = null;
        StatementInstance dom = null;
        
        if (variables.isEmpty()) {
            workingSet.addStmt(start, new Pair<>(start, new AccessPath(start.getLineNo(), AccessPath.NOT_DEFINED, start)), "data");
        } else {
            workingSet.addMultiple(start, variables, "data");
        }
        while (true) {

            Pair<StatementInstance, AccessPath> p;
            synchronized(workingSet) {
                if (!workingSet.isEmpty()) {
                    p = workingSet.pop();
                } else {
                    break;
                }
            }

            StatementInstance stmt = p.getO1();
            AccessPath var = p.getO2();
            if (AnalysisUtils.isAndroidMethod(stmt, var)) {
                continue;
            }

            StatementMap chunk = traversal.getChunk(stmt);
            AnalysisLogger.log(Constants.DEBUG, "Slicing on {}", p);

            if (!dataFlowsOnly) {
                dom = getControlDependence(workingSet, p, stmt, chunk);
                AnalysisLogger.log(Constants.DEBUG, "Control-dom is {}", dom);
                if ((firstDom != null) && firstDom.equals(dom)) {
                    workingSet.removeAllWithStmt(dom);
                }
            }

            StatementSet def = null;
            AliasSet usedVars = new AliasSet();
            

            if (!controlFlowOnly) {
                def = getDataDependence(workingSet, p, stmt, var, chunk, def, usedVars);
            }

            if (def != null) {
                addDataDependenceToWorkingSet(workingSet, p, var, def);
            }
        }
        return workingSet.getDynamicSlice().traceOrder();
    }

    private void addDataDependenceToWorkingSet(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p, AccessPath var,
            StatementSet def) {
        for (StatementInstance iu: def) {
            if (iu != null) {
                Pair<CalledChunk, AccessPath> retPair = traversal.getReturnIfStmtIsCall(iu.getLineNo());
                if (retPair != null) {
                    iu = retPair.getO1().getRetIu();
                }
                AnalysisLogger.log(Constants.DEBUG && !def.contains(iu), "Return def {}\n", iu);
                if (retPair != null) {
                    workingSet.add(iu, retPair.getO2(), p, "data");
                } else {
                    workingSet.addStmt(iu, p, "data");
                    if (var.getField().equals("")) {
                        getUsedVariables(workingSet, p, iu);
                    }
                }
            }
        }
    }

    private void getUsedVariables(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p, StatementInstance iu) {
        for (ValueBox fieldDef: iu.getUnit().getDefBoxes()) {
            if (fieldDef.getValue() instanceof FieldRef){
                for (ValueBox vb: ((FieldRef) fieldDef.getValue()).getUseBoxes()){
                    if (p.getO2().baseEquals(vb.getValue().toString())) {
                        workingSet.add(iu, p.getO2(), p, "data");
                    }
                }
            }
        }
        if (iu.getCalledMethod()!=null && iu.getCalledMethod().getSignature().equals("<java.lang.Object: void <init>()>")) {
            if (((InstanceInvokeExpr) ((Stmt) iu.getUnit()).getInvokeExpr()).getBaseBox().toString().equals(p.getO2().getPathString())) {
                workingSet.add(iu, p.getO2(), p, "data");
            }
        }
    }

    private StatementSet getDataDependence(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p,
            StatementInstance stmt, AccessPath var, StatementMap chunk, StatementSet def, AliasSet usedVars) {
        if (var.getField().equals("")) {
            def = localReachingDef(stmt, var, chunk, usedVars, frameworkModel);
            AnalysisLogger.log(Constants.DEBUG, "Local def {}", def);
        } else if (staticAnalysis || var.isStaticField()) {
            AnalysisLogger.log(Constants.DEBUG, "Getting static heap def of {}-{}", var, var.getClassPath());
            CallbackDetection callbackDetection = new CallbackDetection(icdg, stmt, var);
            def = (new AliasAnalysis(icdg)).reachingDefinitions(stmt, var, callbackDetection);
            if (def.isEmpty()) {
                AnalysisLogger.log(Constants.DEBUG, "Static heap def of {} is None", var);
            } else {
                AnalysisLogger.log(Constants.DEBUG, "Static heap def of {} is {}", var, def);
            }
        } else if (dynamicAnalysis) {
            AnalysisLogger.log(Constants.DEBUG, "Getting dynamic heap def of {}", var);
            def = (new DynamicHeapAnalysis(icdg)).reachingDefinitions(stmt, var);
            AnalysisLogger.log(Constants.DEBUG, "Dynamic heap def of {} is {}", var, def);
        } else {
            AnalysisLogger.log(Constants.DEBUG, "Ignoring heap def {} ", var);
        }
        if (!usedVars.isEmpty() && def != null) {
            for (StatementInstance iu: def) {
                for (AccessPath usedVar: usedVars) {
                    workingSet.add(iu, usedVar, p, "data");
                }
            }
        }
        return def;
    }

    private StatementInstance getControlDependence(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p,
            StatementInstance stmt, StatementMap chunk) {
        StatementInstance dom = ControlDominator.getControlDominator(stmt, chunk, this.icdg);
        if (dom != null) {
            workingSet.addStmt(dom, p, "control");
        } else {
            dom = null;
            try {
                dom = icdg.mapNoUnits(traversal.getCaller(stmt.getLineNo()));
                AnalysisLogger.log(Constants.DEBUG, "Got caller: {}", dom);
            } catch (Exception e) {
                AnalysisLogger.warn(true, "Exception ignored", e);
            }
            if (dom != null && ((Stmt) dom.getUnit()).containsInvokeExpr() && ((Stmt) dom.getUnit()).getInvokeExpr().getMethod().getName().equals(stmt.getMethod().getName()) ) {
                workingSet.addStmtOnly(dom, p, "control");
                workingSet.addMethodOfStmt(dom, p);
            }
        }
        return dom;
    }

    StatementInstance getCallStmt (StatementMap chunk){
        StatementInstance c = null;
        for (StatementInstance u: chunk.values()) {
            c = u;
        }
        return c;
    }

    StatementSet localReachingDef(StatementInstance iu, AccessPath ap, StatementMap chunk, AliasSet usedVars, boolean frameworkModel){
        // AnalysisLogger.log(Constants.DEBUG, "Getting local def for {} with chunk {}", ap, chunk);
        if (ap.isEmpty() || chunk == null) {
            return null;
        }
        Set<Pair<StatementInstance, AccessPath>> backwardDefVars = new LinkedHashSet<>();
        StatementSet defSet = new StatementSet();
        chunk = chunk.reverseTraceOrder(iu);
        boolean localFound = false;
        for (StatementInstance u: chunk.values()) {
            if (localFound) {
                break;
            }
            // AnalysisLogger.log(Constants.DEBUG, "Inspecting {}", u);
            if (u.getLineNo() >= iu.getLineNo() || u.getUnit()==null) {
                continue;
            }
            for (ValueBox def: u.getUnit().getDefBoxes()) {
                // AnalysisLogger.log(Constants.DEBUG, "Inspecting def {}", def);
                if(def.getValue() instanceof Local) {
                    if (ap.baseEquals(def.getValue().toString())) {
                        backwardDefVars.add(new Pair<>(u, new AccessPath(def.getValue().toString(), def.getValue().getType(), AccessPath.NOT_USED, u.getLineNo(), u)));
                        defSet.add(u);
                        localFound = true;
                        break;
                    }
                } else if (def.getValue() instanceof FieldRef){
                    for (ValueBox vb: ((FieldRef) def.getValue()).getUseBoxes()){
                        if (ap.baseEquals(vb.getValue().toString())) {
                            backwardDefVars.add(new Pair<>(u, new AccessPath(def.getValue().toString(), def.getValue().getType(), AccessPath.NOT_USED, u.getLineNo(), u)));
                            defSet.add(u);
                        }
                    }
                } else if (def.getValue() instanceof ArrayRef) {
                    Value v = ((ArrayRef) def.getValue()).getBase();
                    if (ap.baseEquals(v.toString())) {
                        backwardDefVars.add(new Pair<>(u, new AccessPath(def.getValue().toString(), def.getValue().getType(), AccessPath.NOT_USED, u.getLineNo(), u)));
                        defSet.add(u);
                    }
                }
            }
            InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(u);
            // AnalysisLogger.log(Constants.DEBUG, "Invoke expr {}", invokeExpr);
            if (invokeExpr != null) {
                if (!traversal.isFrameworkMethod(u)) {
                    if (! (((Stmt) u.getUnit()) instanceof AssignStmt)) {
                        if (invokeExpr != null && !invokeExpr.getMethod().isStatic()) {
                            if (ap.baseEquals(((InstanceInvokeExpr) invokeExpr).getBase().toString())){
                                defSet.add(u);
                            }
                        }
                    }
                } else if (frameworkModel) {
                    if (FrameworkModel.localWrapper(u, ap, defSet, usedVars)) {
                        // pass
                    }
                }
            }
        }
        if (defSet.isEmpty()) {
            defSet.addAll(getReachingInCaller(iu, ap));
        }
        AnalysisLogger.log(Constants.DEBUG, "Defs backward are: {}", defSet);
        // defSet = localReachingDefForward(backwardDefVars, defSet);
        // AnalysisLogger.log(Constants.DEBUG, "Defs with forward are: {}", defSet);
        return defSet;
    }


    private StatementSet localReachingDefForward(Set<Pair<StatementInstance, AccessPath>> backwardDefVars, StatementSet defSet) {
        
        for (Pair<StatementInstance, AccessPath> p: backwardDefVars) {
            findDefsForward(p, defSet);
        }
        return defSet;
    }

    private void findDefsForward(Pair<StatementInstance, AccessPath> p, StatementSet defSet){
        StatementInstance si = p.getO1();
        AliasSet as = new AliasSet(p.getO2());
        int pos = si.getLineNo();
        iterateDefsForward(as, defSet, pos);
    }

    private void iterateDefsForward(AliasSet as, StatementSet defSet, int pos) {
        if (as.isEmpty()) {
            return;
        }
        StatementInstance current = icdg.mapNoUnits(pos);
        Set<StatementInstance> nexts = traversal.nextNodes(current);
        for (StatementInstance next: nexts) {
            AliasSet newAs = traversal.changeScope(as, next, current);
            for (AccessPath ap: newAs) {
                for (ValueBox def: next.getUnit().getDefBoxes()) {
                    // AnalysisLogger.log(Constants.DEBUG, "Inspecting def {}", def);
                    if (def.getValue() instanceof FieldRef){
                        for (ValueBox vb: ((FieldRef) def.getValue()).getUseBoxes()){
                            if (ap.baseEquals(vb.getValue().toString())) {
                                defSet.add(next);
                            }
                        }
                    } else if (def.getValue() instanceof ArrayRef) {
                        Value v = ((ArrayRef) def.getValue()).getBase();
                        if (ap.baseEquals(v.toString())) {
                            defSet.add(next);
                        }
                    } else if ((next.getUnit() instanceof AssignStmt) && (((AssignStmt) next.getUnit()).getRightOp() instanceof CastExpr)) {
                        Value v = ((AssignStmt) next.getUnit()).getRightOp();
                        if (ap.getPathString().equals(v.toString())) {
                            defSet.add(next);
                        }
                    }
                }
            }
            iterateDefsForward(newAs, defSet, next.getLineNo());
        }
    }

    private StatementSet getReachingInCaller(StatementInstance iu, AccessPath ap) throws Error {
        StatementSet defSet = new StatementSet();
        int callerPos = traversal.getCaller(iu.getLineNo());
        AliasSet apSet = new AliasSet();
        apSet.add(ap);
        AliasSet taintedParams = traversal.changeScopeToCaller(iu, icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(callerPos)), apSet);
        if (taintedParams == null || taintedParams.isEmpty()) {
            return defSet;
        }
        if (taintedParams.size() > 1) {
            // AnalysisLogger.log(Constants.DEBUG, "TaintedParams: {}", taintedParams);
            throw new Error("More than one definition of a local variable!");
        }
        StatementInstance nextCaller = null;
        ap = taintedParams.iterator().next();
        StatementMap callerChunk = traversal.getChunk(callerPos);
        for (StatementInstance u: callerChunk.values()) {
            if (u.getLineNo()==callerPos) {
                nextCaller = u;
            }
            if (u.getUnit() == null || u.getLineNo()==callerPos) {
                continue;
            }
            nextCaller = u;

            boolean foundLocalDef = findLocalDefs(ap, defSet, u);

            if (foundLocalDef) {
                return defSet;
            }

            foundLocalDef = findLocalDefInFrameworkMethod(ap, defSet, u, foundLocalDef);

            if (foundLocalDef) {
                return defSet;
            }
        }
        
        if (nextCaller != null && nextCaller.equals(iu)) {
            return defSet;
        }
        if (nextCaller != null) {
            return getReachingInCaller(nextCaller, ap);
        } else {
            return defSet;
        }
        
    }

    private boolean findLocalDefInFrameworkMethod(AccessPath ap, StatementSet defSet, StatementInstance u, boolean foundLocalDef)
            throws Error {
        if (frameworkModel) {
            InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(u);
            if (! (((Stmt) u.getUnit()) instanceof AssignStmt)) {
                if (invokeExpr != null && !invokeExpr.getMethod().isStatic()) {
                    if (ap.baseEquals(((InstanceInvokeExpr) invokeExpr).getBase().toString())){
                        defSet.add(u);
                        foundLocalDef = true;
                    }
                }
            }
        }
        return foundLocalDef;
    }

    private boolean findLocalDefs(AccessPath ap, StatementSet defSet, StatementInstance u) {
        boolean foundLocalDef = false;
        for (ValueBox def: u.getUnit().getDefBoxes()) {
            if(def.getValue() instanceof Local) {
                if (ap.baseEquals(def.getValue().toString())) {
                    defSet.add(u);
                    foundLocalDef = true;
                }
            } else if (def.getValue() instanceof FieldRef){
                for (ValueBox vb: ((FieldRef) def.getValue()).getUseBoxes()){
                    if (ap.baseEquals(vb.getValue().toString())) {
                        defSet.add(u);
                    }
                }
            } else if (def.getValue() instanceof ArrayRef) {
                Value v = ((ArrayRef) def.getValue()).getBase();
                if (ap.baseEquals(v.toString())) {
                    defSet.add(u);
                }
            }
        }
        return foundLocalDef;
    }
}