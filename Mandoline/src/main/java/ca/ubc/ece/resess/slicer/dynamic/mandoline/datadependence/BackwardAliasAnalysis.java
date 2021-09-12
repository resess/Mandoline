package ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence;

import java.util.LinkedHashSet;
import java.util.Set;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.core.datadependence.BackwardStaticFieldAnalysis;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.CalledChunk;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.CallerContext;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementSet;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisCache;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;
import soot.toolkits.scalar.Pair;
import soot.jimple.InstanceInvokeExpr;


public class BackwardAliasAnalysis extends AliasAnalysis {
    StatementInstance startNode;
    StatementSet aliasPath;
    AliasSet startFields;
    Integer lowBound;
    CallbackDetection callbackDetection;
    BackwardAliasAnalysis(DynamicControlFlowGraph icdg, StatementInstance iu, AliasSet ap, StatementSet aliasPath, Integer lowBound, CallbackDetection callbackDetection, AnalysisCache analysisCache) {
        super(icdg, analysisCache);
        this.startNode = iu;
        this.startFields = ap;
        this.aliasPath = aliasPath;
        this.lowBound = lowBound;
        this.callbackDetection = callbackDetection;
    }

    public Set<AliasAnalysis> run(){

        Set<AliasAnalysis> ret = new LinkedHashSet<>();
        if (startFields.isEmpty()) {
            return new LinkedHashSet<>();
        }

        AliasSet aliasSet = new AliasSet();
        aliasSet.addAll(this.startFields);
        aliasSet = analysisCache.filterByAnalysisCache(startNode, aliasSet, Constants.BACKWARD);
        AliasSet newAliasSet = new AliasSet(aliasSet);

        runSeparateAnalysisForStaticVariables(aliasSet, newAliasSet);

        aliasSet = new AliasSet(newAliasSet);
        if (aliasSet.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<StatementInstance> previousIus = traversal.previousNodes(this.startNode);
        AliasSet origAliasSet = new AliasSet(aliasSet);
        for (StatementInstance iu: previousIus) {
            aliasSet = backwardChangeScope(ret, origAliasSet, iu);
            AliasAnalysisResult aliasAnalysisResult = null;
            Unit u = iu.getUnit();
            if (u instanceof AssignStmt) {
                aliasAnalysisResult = inspectAssignment(ret, aliasSet, iu);
            } else if (u instanceof InvokeStmt) {
                aliasAnalysisResult = inspectInvoke(ret, aliasSet, iu);
            }
            analyzeNextStatement(ret, aliasSet, iu, aliasAnalysisResult);
        }

        analyzeNextCallback(ret, origAliasSet);

        return ret;
    }

    private void analyzeNextCallback(Set<AliasAnalysis> ret, AliasSet origAliasSet) {
        if (ret.isEmpty()) {
            Set<CallerContext> backwardCallers = callbackDetection.getBackwardCallers(this.startNode, origAliasSet, this.lowBound);
            for (CallerContext cc: backwardCallers) {
                ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, cc.getCaller(), cc.getAliasedArgs(), this.aliasPath, this.lowBound, false, this.callbackDetection, this.analysisCache);
                ret.add(forwardAliasAnalysis);
            }
            Set<CallerContext> forwardCallbacks = callbackDetection.getForwardCaller(this.startNode, origAliasSet, this.lowBound);
            for (CallerContext cc: forwardCallbacks) {
                ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, cc.getCaller(), cc.getAliasedArgs(), this.aliasPath, this.lowBound, false, this.callbackDetection, this.analysisCache);
                ret.add(forwardAliasAnalysis);
            }
        }
    }

    private void analyzeNextStatement(Set<AliasAnalysis> ret, AliasSet aliasSet, StatementInstance iu,
            AliasAnalysisResult aliasAnalysisResult) {
        StatementInstance nextStatement = iu;
        AliasSet backwardAliasSet = new AliasSet(aliasSet);
        AliasSet forwardAliasSet = new AliasSet();
        if (aliasAnalysisResult != null) {
            nextStatement = aliasAnalysisResult.getNextStatement();
            backwardAliasSet = aliasAnalysisResult.getBackwardAliasSet();
            forwardAliasSet = aliasAnalysisResult.getForwardAliasSet();
        } 
        BackwardAliasAnalysis backwardAliasAnalysis = new BackwardAliasAnalysis(this.icdg, nextStatement, backwardAliasSet, this.aliasPath, this.lowBound, this.callbackDetection, this.analysisCache);
        ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, nextStatement, forwardAliasSet, this.aliasPath, this.lowBound, false, this.callbackDetection, this.analysisCache);
        ret.add(backwardAliasAnalysis);
        ret.add(forwardAliasAnalysis);
    }

    private AliasAnalysisResult inspectInvoke(Set<AliasAnalysis> ret, AliasSet aliasSet, StatementInstance iu) {
        AliasAnalysisResult aliasAnalysisResult;
        Pair<AliasAnalysisResult, AliasAnalysisResult> methodResult = invokeAliasAnalysis(aliasSet, iu);
        AliasAnalysisResult resultInMethod = methodResult.getO1();
        if (resultInMethod != null) {
            BackwardAliasAnalysis backwardAliasAnalysis = new BackwardAliasAnalysis(this.icdg, resultInMethod.getNextStatement(), resultInMethod.getBackwardAliasSet(), this.aliasPath, this.lowBound, this.callbackDetection, this.analysisCache);
            ret.add(backwardAliasAnalysis);
        }
        aliasAnalysisResult = methodResult.getO2();
        return aliasAnalysisResult;
    }

    private AliasAnalysisResult inspectAssignment(Set<AliasAnalysis> ret, AliasSet aliasSet, StatementInstance iu) {
        AliasAnalysisResult aliasAnalysisResult;
        Pair<AliasAnalysisResult, AliasAnalysisResult> methodResult = assignmentAliasAnalysis(aliasSet, iu, aliasPath);
        AliasAnalysisResult resultInMethod = methodResult.getO1();
        if (resultInMethod != null) {
            BackwardAliasAnalysis backwardAliasAnalysis = new BackwardAliasAnalysis(this.icdg, resultInMethod.getNextStatement(), resultInMethod.getBackwardAliasSet(), this.aliasPath, this.lowBound, this.callbackDetection, this.analysisCache);
            ret.add(backwardAliasAnalysis);
        }
        aliasAnalysisResult = methodResult.getO2();
        return aliasAnalysisResult;
    }

    private AliasSet backwardChangeScope(Set<AliasAnalysis> ret, AliasSet origAliasSet, StatementInstance iu) {
        AliasSet aliasSet;
        aliasSet = traversal.changeScope(origAliasSet, this.startNode, iu);
        if (traversal.getCaller(this.startNode.getLineNo()) == iu.getLineNo()) {
            int next = traversal.nextFlowEdge(iu.getLineNo());
            ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, icdg.mapNoUnits(next), aliasSet, this.aliasPath, this.lowBound, false, this.callbackDetection, this.analysisCache);
            ret.add(forwardAliasAnalysis);
        }
        return aliasSet;
    }

    private void runSeparateAnalysisForStaticVariables(AliasSet aliasSet, AliasSet newAliasSet) {
        for (AccessPath ap: aliasSet) {
            if (ap.isStaticField()) {
                BackwardStaticFieldAnalysis bw = new BackwardStaticFieldAnalysis(icdg, startNode, ap, aliasPath, analysisCache);
                bw.run();
                newAliasSet.remove(ap);
            }
        }
    }

    public Pair<AliasAnalysisResult, AliasAnalysisResult> invokeAliasAnalysis(AliasSet aliasSet, StatementInstance si) {
        AliasAnalysisResult aliasAnalysisResultInMethod = null;
        AliasAnalysisResult aliasAnalysisResultOutMethod = null;
        int lineNo = si.getLineNo();
        Unit u = si.getUnit();
        Pair<CalledChunk, AccessPath> p = new Pair<>(traversal.getCalledChunk(lineNo), null);
        if (p.getO1().getChunk() != null) {
            if (!p.getO1().getRetIu().methodEquals(this.startNode)) {
                Pair<AliasAnalysisResult, AliasAnalysisResult> resultFromMethod = goBackInMethod(aliasSet, si, null, p);
                aliasAnalysisResultInMethod = resultFromMethod.getO1();
                aliasAnalysisResultOutMethod = resultFromMethod.getO2();
            }
        } else {
            if (((InvokeStmt )u).getInvokeExpr() instanceof InstanceInvokeExpr) {
                aliasAnalysisResultOutMethod = backwardGenMethodWrapper(aliasSet, si, aliasPath);
            }
        }
        return new Pair<>(aliasAnalysisResultInMethod, aliasAnalysisResultOutMethod);
    }
    public Pair<AliasAnalysisResult, AliasAnalysisResult> assignInvokeAliasAnalysis(AliasSet aliasSet, StatementInstance si) {
        AliasAnalysisResult aliasAnalysisResultInMethod = null;
        AliasAnalysisResult aliasAnalysisResultOutMethod = null;
        Unit u = si.getUnit();
        AssignStmt stmt = (AssignStmt) u;
        Value l = stmt.getLeftOp();
        AccessPath lhs = new AccessPath(l.toString(), l.getType(), AccessPath.NOT_USED, si.getLineNo(), si);
        Pair<CalledChunk, AccessPath> p = traversal.getReturnIfStmtIsCall(si.getLineNo());
        if (p != null) {
            if (!p.getO1().getRetIu().methodEquals(this.startNode)) {
                Pair<AliasAnalysisResult, AliasAnalysisResult> resultFromMethod = goBackInMethod(aliasSet, si, lhs, p);
                aliasAnalysisResultInMethod = resultFromMethod.getO1();
                aliasAnalysisResultOutMethod = resultFromMethod.getO2();
            }
        } else {
            aliasAnalysisResultOutMethod = backwardGenMethodWrapper(aliasSet, si, aliasPath);
        }
        return new Pair<>(aliasAnalysisResultInMethod, aliasAnalysisResultOutMethod);
    }

    @Override
    public String toString() {
        return String.format("Backward analysis from: t = %s with V = %s", this.startNode, this.startFields);
    }
}