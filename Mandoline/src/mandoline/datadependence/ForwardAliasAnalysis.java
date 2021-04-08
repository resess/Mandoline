package mandoline.datadependence;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import mandoline.accesspath.AccessPath;
import mandoline.accesspath.AliasSet;
import mandoline.graph.CallerContext;
import mandoline.graph.ICDG;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementSet;
import mandoline.utils.AnalysisCache;
import mandoline.utils.Constants;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.ThrowStmt;
import soot.toolkits.scalar.Pair;

/**
 * Forward alias analysis to find aliases of variable after a statement in the trace
 * @author Khaled Ahmed
 */
public class ForwardAliasAnalysis extends AliasAnalysis {
    StatementInstance startNode;
    StatementSet aliasPath;
    AliasSet startFields;
    Integer lowBound;
    boolean fromMethodCall;
    CallbackDetection callbackDetection;
    /**
     * Create a forward alias analysis object
     * @param iu Statement to start from
     * @param ap Access path to find aliases of
     * @param aliasPath Statements that cause variables to alias the access path 
     * @param lowBound Index of the last statement to stop at
     * @param fromMethodCall analysis starts from a method call (vs regular statement)
     */
    // The constructor ForwardAliasAnalysis(InstructionUnits, AliasSet, InstructionSet, Integer, boolean, CallbackDetection) is undefinedJava(134217858)

    ForwardAliasAnalysis(ICDG icdg, StatementInstance iu, AliasSet ap, StatementSet aliasPath, Integer lowBound, boolean fromMethodCall, CallbackDetection callbackDetection) {
        super(icdg);
        this.startNode = iu;
        this.startFields = ap;
        this.aliasPath = aliasPath;
        this.lowBound = lowBound;
        this.fromMethodCall = fromMethodCall;
        this.callbackDetection = callbackDetection;
    }

    /**
     * Run the forward analysis
     * @return Set of forward alias analyses to run after this analysis
     */
    public Set<AliasAnalysis> run(){

        Set<AliasAnalysis> ret = new LinkedHashSet<>();
        if (startFields.isEmpty()) {
            return ret;
        }
        if (this.startNode.getLineNo() > lowBound) {
            return ret;
        }
        // Create initial alias set, remove elements in the set that were previously analyzed
        AliasSet aliasSet = new AliasSet();
        aliasSet.addAll(this.startFields);
        aliasSet = AnalysisCache.filterByAnalysisCache(startNode, aliasSet, Constants.FORWARD);


        AliasSet newAliasSet = new AliasSet(aliasSet);
        if (newAliasSet.isEmpty()) {
            return ret;
        }
        aliasSet = new AliasSet(newAliasSet);
        // Analyze method body
        Set<StatementInstance> nextIus = traversal.nextNodes(this.startNode);
        AliasSet origAliasSet = new AliasSet(aliasSet);
        for (StatementInstance iu: nextIus) {
            analyzeStatementInstance(ret, origAliasSet, this.startNode, iu, nextIus);
        }
        analyzeNextCallback(ret, origAliasSet);
        return ret;
    }

    private void analyzeNextCallback(Set<AliasAnalysis> ret, AliasSet origAliasSet) {
        if (ret.isEmpty()) {
            Set<CallerContext> forwardCallbacks = callbackDetection.getForwardCaller(this.startNode, origAliasSet, this.lowBound);
            for (CallerContext cc: forwardCallbacks) {
                ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, cc.getCaller(), cc.getAliasedArgs(), this.aliasPath, this.lowBound, false, this.callbackDetection);
                ret.add(forwardAliasAnalysis);
            }
        }
    }

    private void analyzeStatementInstance(Set<AliasAnalysis> ret, AliasSet origAliasSet, StatementInstance start, StatementInstance iu, Set<StatementInstance> nextIus) {
        AliasSet aliasSet;
        aliasSet = traversal.changeScope(origAliasSet, start, iu);
        // Analyze assignment statements and method calls
        AliasAnalysisResult aliasAnalysisResult = null;
        Unit u = iu.getUnit();
        if (u instanceof AssignStmt) {
            aliasAnalysisResult = inspectAssignment(ret, iu, aliasSet);
        } else if (u instanceof InvokeStmt) {
            aliasAnalysisResult = inspectInvoke(ret, iu, aliasSet);
        } else if (u instanceof ReturnStmt || u instanceof ThrowStmt || u instanceof ReturnVoidStmt) {
            aliasAnalysisResult = returnAliasAnalysis(aliasSet, iu);
        }
        analyzeNextStatement(ret, iu, nextIus, aliasSet, aliasAnalysisResult);
    }

    private void analyzeNextStatement(Set<AliasAnalysis> ret, StatementInstance iu, Set<StatementInstance> nextIus,
            AliasSet aliasSet, AliasAnalysisResult aliasAnalysisResult) {
        StatementInstance nextStatement = iu;
        AliasSet backwardAliasSet = new AliasSet();
        AliasSet forwardAliasSet = new AliasSet(aliasSet);
        if (aliasAnalysisResult != null) {
            nextStatement = aliasAnalysisResult.getNextStatement();
            backwardAliasSet = aliasAnalysisResult.getBackwardAliasSet();
            forwardAliasSet = aliasAnalysisResult.getForwardAliasSet();
        }
        BackwardAliasAnalysis backwardAliasAnalysis = new BackwardAliasAnalysis(this.icdg, nextStatement, backwardAliasSet, this.aliasPath, this.lowBound, this.callbackDetection);
        ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, nextStatement, forwardAliasSet, this.aliasPath, this.lowBound, false, this.callbackDetection);
        ret.add(backwardAliasAnalysis);
        ret.add(forwardAliasAnalysis);
        if (nextStatement != null && !nextIus.contains(nextStatement)) {
            analyzeStatementInstance(ret, forwardAliasSet, nextStatement, nextStatement, new LinkedHashSet<>(Arrays.asList(nextStatement)));
        }
    }

    private AliasAnalysisResult inspectInvoke(Set<AliasAnalysis> ret, StatementInstance iu, AliasSet aliasSet) {
        AliasAnalysisResult aliasAnalysisResult;
        Pair<AliasAnalysisResult, AliasAnalysisResult> methodResult = invokeAliasAnalysis(aliasSet, iu);
        AliasAnalysisResult resultInMethod = methodResult.getO1();
        if (resultInMethod != null) {
            ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, resultInMethod.getNextStatement(), resultInMethod.getForwardAliasSet(), this.aliasPath, this.lowBound, false, this.callbackDetection);
            ret.add(forwardAliasAnalysis);
        }
        aliasAnalysisResult = methodResult.getO2();
        return aliasAnalysisResult;
    }

    private AliasAnalysisResult inspectAssignment(Set<AliasAnalysis> ret, StatementInstance iu, AliasSet aliasSet) {
        AliasAnalysisResult aliasAnalysisResult;
        Pair<AliasAnalysisResult, AliasAnalysisResult> methodResult = assignmentAliasAnalysis(aliasSet, iu, aliasPath);
        AliasAnalysisResult resultInMethod = methodResult.getO1();
        if (resultInMethod != null) {
            ForwardAliasAnalysis forwardAliasAnalysis = new ForwardAliasAnalysis(this.icdg, resultInMethod.getNextStatement(), resultInMethod.getForwardAliasSet(), this.aliasPath, this.lowBound, false, this.callbackDetection);
            ret.add(forwardAliasAnalysis);
        }
        aliasAnalysisResult = methodResult.getO2();
        return aliasAnalysisResult;
    }

    public AliasAnalysisResult returnAliasAnalysis(AliasSet aliasSet, StatementInstance si) {
        Unit u = si.getUnit();
        AccessPath returnVar = null;
        if (u instanceof ReturnStmt) {
            returnVar = new AccessPath(si.getReturnVar(), si.getReturnType(), AccessPath.NOT_USED, si.getLineNo(), si);
        } else if (u instanceof ThrowStmt) {
            returnVar = new AccessPath(si.getThrowVar(), si.getThrowType(), AccessPath.NOT_USED, si.getLineNo(), si);
        }
        int caller = traversal.getCaller(si.getLineNo());
        int returnSite = traversal.nextFlowEdge(caller);
        if (returnSite == caller) {
            return new AliasAnalysisResult(si, new AliasSet(), new AliasSet());
        }
        AliasSet forwardAliasSet = traversal.changeScopeToCaller(si, icdg.mapNoUnits(caller), aliasSet);
        if (returnVar != null) {
            for (AccessPath v: aliasSet) {
                if (v.startsWith(returnVar) && icdg.mapNoUnits(caller).getUnit() instanceof AssignStmt) {
                    Value l = ((AssignStmt) icdg.mapNoUnits(caller).getUnit()).getLeftOp();
                    AccessPath returnAccessPath = new AccessPath(l.toString(), l.getType(), AccessPath.NOT_USED, si.getLineNo(), si);
                    returnAccessPath.add(v.getAfter(returnVar).getO1(), v.getAfter(returnVar).getO2(), si);
                    forwardAliasSet.add(returnAccessPath);
                }
            }
        }
        return new AliasAnalysisResult(icdg.mapNoUnits(returnSite), new AliasSet(), forwardAliasSet);
    }

    public Pair<AliasAnalysisResult, AliasAnalysisResult> invokeAliasAnalysis(AliasSet aliasSet, StatementInstance iu) {
        Unit u = iu.getUnit();
        Stmt stmt = (InvokeStmt) u;
        return forwardAliasInMethod(aliasSet, iu, stmt, aliasPath);
    }

    public Pair<AliasAnalysisResult, AliasAnalysisResult> assignInvokeAliasAnalysis(AliasSet aliasSet, StatementInstance iu) {
        Unit u = iu.getUnit();
        Stmt stmt = (AssignStmt) u;
        return forwardAliasInMethod(aliasSet, iu, stmt, aliasPath);
    }

    @Override
    public String toString() {
        return String.format("Forward analysis from: t = %s with V = %s", this.startNode, this.startFields);
    }
} 