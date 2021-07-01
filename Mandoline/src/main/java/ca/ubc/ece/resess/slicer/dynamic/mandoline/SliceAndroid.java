package ca.ubc.ece.resess.slicer.dynamic.mandoline;


import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence.AliasAnalysis;
import ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence.CallbackDetection;
import ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence.SpecialDependence;
import ca.ubc.ece.resess.slicer.dynamic.mandoline.graph.ICDG;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.slicer.SliceMethod;
import ca.ubc.ece.resess.slicer.dynamic.core.slicer.SlicingWorkingSet;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementSet;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisUtils;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;

import soot.Value;
import soot.jimple.ReturnStmt;
import soot.toolkits.scalar.Pair;

public class SliceAndroid extends SliceMethod {
    private SpecialDependence specialDependence;
    public SliceAndroid(ICDG icdg, boolean frameworkModel, boolean dataFlowsOnly, boolean controlFlowOnly, SlicingWorkingSet workingSet) {
        super(icdg, frameworkModel, dataFlowsOnly, controlFlowOnly, workingSet);
        this.specialDependence = new SpecialDependence(icdg);
    }

    @Override
    public StatementSet getDataDependence(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p,
            StatementInstance stmt, AccessPath var, StatementMap chunk, StatementSet def, AliasSet usedVars) {
        if (var.getField().equals("")) {
            def = localReachingDef(stmt, var, chunk, usedVars, frameworkModel);
            AnalysisLogger.log(Constants.DEBUG, "Local def {}", def);
        } else {
            AnalysisLogger.log(Constants.DEBUG, "Getting static heap def of {}-{}", var, var.getClassPath());
            CallbackDetection callbackDetection = new CallbackDetection(icdg, stmt, var);
            def = (new AliasAnalysis(icdg)).reachingDefinitions(stmt, var, callbackDetection);
            if (def.isEmpty()) {
                AnalysisLogger.log(Constants.DEBUG, "Static heap def of {} is None", var);
            } else {
                AnalysisLogger.log(Constants.DEBUG, "Static heap def of {} is {}", var, def);
            }
        }
        StatementInstance specialDef = specialDependence.getSpecialDependence(stmt);
        if (specialDef != null) {
            def.add(specialDef);
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

    @Override
    public StatementSet getReachingInCaller(StatementInstance iu, AccessPath ap) throws Error {

        StatementSet defSet = new StatementSet();
        int callerPos = traversal.getCaller(iu.getLineNo());
        AliasSet apSet = new AliasSet();
        apSet.add(ap);
        AliasSet taintedParams = new AliasSet();
        if (iu.getMethod().getSubSignature().equals("void onPostExecute(java.lang.Object)") && AnalysisUtils.isMethodParameter(iu, ap)) {
            StatementInstance doInBackReturn = specialDependence.getDoInBackgroundReturn(iu);
            callerPos = doInBackReturn.getLineNo();
            Value retVar = ((ReturnStmt) doInBackReturn.getUnit()).getOp();
            taintedParams.add(new AccessPath(retVar.toString(), retVar.getType(), ap.getUsedLine(), ap.getDefinedLine(), doInBackReturn));
        } else {
            taintedParams = traversal.changeScopeToCaller(iu, icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(callerPos)), apSet);
        }
        

        AnalysisLogger.log(true, "for {} and {}, caller pos is {}, taintedParams is ", iu, ap, callerPos, taintedParams);
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
}