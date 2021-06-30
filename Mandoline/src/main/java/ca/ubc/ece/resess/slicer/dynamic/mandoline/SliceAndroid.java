package ca.ubc.ece.resess.slicer.dynamic.mandoline;


import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence.AliasAnalysis;
import ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence.CallbackDetection;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.slicer.SliceMethod;
import ca.ubc.ece.resess.slicer.dynamic.core.slicer.SlicingWorkingSet;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementSet;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;

import soot.toolkits.scalar.Pair;

public class SliceAndroid extends SliceMethod {

    public SliceAndroid(DynamicControlFlowGraph icdg, boolean frameworkModel, boolean dataFlowsOnly, boolean controlFlowOnly, SlicingWorkingSet workingSet) {
        super(icdg, frameworkModel, dataFlowsOnly, controlFlowOnly, workingSet);
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
        if (!usedVars.isEmpty() && def != null) {
            for (StatementInstance iu: def) {
                for (AccessPath usedVar: usedVars) {
                    workingSet.add(iu, usedVar, p, "data");
                }
            }
        }
        return def;
    }
}