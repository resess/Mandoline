package mandoline.slicer;

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
import soot.Local;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
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

    SliceMethod(ICDG icdg, boolean staticAnalysis, boolean dynamicAnalysis, boolean frameworkModel) {
        this.icdg = icdg;
        this.traversal = new Traversal(icdg);
        this.staticAnalysis = staticAnalysis;
        this.dynamicAnalysis = dynamicAnalysis;
        this.frameworkModel = frameworkModel;
    }

    DynamicSlice slice(StatementInstance start, Set<AccessPath> variables) {
        SlicingWorkingSet workingSet = new SlicingWorkingSet();
        workingSet.addMultiple(start, variables);
        while (!workingSet.isEmpty()) {
            Pair<StatementInstance, AccessPath> p = workingSet.pop();
            StatementInstance stmt = p.getO1();
            AccessPath var = p.getO2();
            if (AnalysisUtils.isAndroidMethod(stmt, var)) {
                continue;
            }

            StatementMap chunk = traversal.getChunk(stmt);
            AnalysisLogger.log(true, "Slicing on {}", p);


            getControlDependence(workingSet, p, stmt, chunk);


            StatementSet def = null;
            AliasSet usedVars = new AliasSet();
            

            def = getDataDependence(workingSet, p, stmt, var, chunk, def, usedVars);

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
                AnalysisLogger.log(!def.contains(iu), "Return def {}\n", iu);
                if (retPair != null) {
                    workingSet.add(iu, retPair.getO2(), p);
                } else {
                    workingSet.addStmt(iu, p);
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
                        workingSet.add(iu, p.getO2(), p);
                    }
                }
            }
        }
        if (iu.getCalledMethod()!=null && iu.getCalledMethod().getSignature().equals("<java.lang.Object: void <init>()>")) {
            if (((InstanceInvokeExpr) ((Stmt) iu.getUnit()).getInvokeExpr()).getBaseBox().toString().equals(p.getO2().getPathString())) {
                workingSet.add(iu, p.getO2(), p);
            }
        }
    }

    private StatementSet getDataDependence(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p,
            StatementInstance stmt, AccessPath var, StatementMap chunk, StatementSet def, AliasSet usedVars) {
        if (var.getField().equals("")) {
            def = localReachingDef(stmt, var, chunk, usedVars, frameworkModel);
            AnalysisLogger.log(true, "Local def {}", def);
        } else if (staticAnalysis || var.isStaticField()) {
            AnalysisLogger.log(true, "Getting static heap def of {}-{}", var, var.getClassPath());
            CallbackDetection callbackDetection = new CallbackDetection(icdg, stmt, var);
            def = (new AliasAnalysis(icdg)).reachingDefinitions(stmt, var, callbackDetection);
            if (def.isEmpty()) {
                AnalysisLogger.log(true, "Static heap def of {} is None", var);
            } else {
                AnalysisLogger.log(true, "Static heap def of {} is {}", var, def);
            }
        } else if (dynamicAnalysis) {
            AnalysisLogger.log(true, "Getting dynamic heap def of {}", var);
            def = (new DynamicHeapAnalysis(icdg)).reachingDefinitions(stmt, var);
            AnalysisLogger.log(true, "Dynamic heap def of {} is {}", var, def);
        } else {
            AnalysisLogger.log(true, "Ignoring heap def {} ", var);
        }
        if (!usedVars.isEmpty() && def != null) {
            for (StatementInstance iu: def) {
                for (AccessPath usedVar: usedVars) {
                    workingSet.add(iu, usedVar, p);
                }
            }
        }
        return def;
    }

    private void getControlDependence(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p,
            StatementInstance stmt, StatementMap chunk) {
        StatementInstance dom = ControlDominator.getControlDominator(stmt, chunk);
        if (dom != null) {
            AnalysisLogger.log(true, "Control-dom is {}", dom);
            workingSet.addStmt(dom, p);
        } else {
            dom = null;
            try {
                dom = icdg.mapNoUnits(traversal.getCaller(stmt.getLineNo()));
            } catch (Exception e) {
                AnalysisLogger.warn(true, "Exception ignored", e);
            }
            if (dom != null && ((Stmt) dom.getUnit()).containsInvokeExpr() && ((Stmt) dom.getUnit()).getInvokeExpr().getMethod().getName().equals(stmt.getMethod().getName()) ) {
                AnalysisLogger.log(true, "Control-dom is {}", dom);
                workingSet.addStmtOnly(dom, p);
            }
        }
    }

    StatementInstance getCallStmt (StatementMap chunk){
        StatementInstance c = null;
        for (StatementInstance u: chunk.values()) {
            c = u;
        }
        return c;
    }

    StatementSet localReachingDef(StatementInstance iu, AccessPath ap, StatementMap chunk, AliasSet usedVars, boolean frameworkModel){
        if (ap.isEmpty() || chunk == null) {
            return null;
        }
        StatementSet defSet = new StatementSet();
        chunk = chunk.reverseTraceOrder(iu);
        for (StatementInstance u: chunk.values()) {
            if (u.getLineNo() >= iu.getLineNo() || u.getUnit()==null) {
                continue;
            }
            for (ValueBox def: u.getUnit().getDefBoxes()) {
                if(def.getValue() instanceof Local) {
                    if (ap.baseEquals(def.getValue().toString())) {
                        defSet.add(u);
                        return defSet;
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
            InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(u);
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
                        return defSet;
                    }
                }
            }
        }
        if (defSet.isEmpty()) {
            defSet.addAll(getReachingInCaller(iu, ap));
        }
        return defSet;
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
            AnalysisLogger.log(true, "TaintedParams: {}", taintedParams);
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