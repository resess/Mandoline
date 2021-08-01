package ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence;

import ca.ubc.ece.resess.slicer.dynamic.core.framework.FrameworkModel;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.core.datadependence.BackwardStaticFieldAnalysis;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisCache;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisUtils;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.CalledChunk;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementList;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementSet;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.Traversal;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import soot.Local;
import soot.SootField;
import soot.Type;
import soot.Unit;

import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.Constant;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;


/**
 * 
 */
public class AliasAnalysis {

    private static int analysisCount;
    private static final boolean SEARCH_SUBFIELDS = false;
    public DynamicControlFlowGraph icdg;
    public Traversal traversal;
    public AliasAnalysis(DynamicControlFlowGraph icdg) {
        this.icdg = icdg;
        this.traversal = new Traversal(icdg);
    }

    public static void setAnalysisCount(int analysisCount) {
        AliasAnalysis.analysisCount = analysisCount;
    }

    public static void incrementAnalysisCount(){
        AliasAnalysis.analysisCount++;
    }

    public StatementSet reachingDefinitions(StatementInstance iu, AccessPath ap, CallbackDetection callbackDetection) {
        AliasAnalysis.setAnalysisCount(0);
        StatementSet aliasPath = new StatementSet();
        startAliasAnalysis(iu, ap, aliasPath, callbackDetection);
        StatementList orderedPath = new StatementList();
        for (StatementInstance pathElem : aliasPath) {
            if (pathElem.getLineNo() < iu.getLineNo() || ap.isStaticField()) {
                orderedPath.add(pathElem);
            }
        }
        addInstances(orderedPath);
        Collections.sort(orderedPath, (lhs, rhs) -> {
            if (rhs.getLineNo() > lhs.getLineNo()) {
                return 1;
            } else if (rhs.getLineNo() < lhs.getLineNo()) {
                return -1;
            }
            return 0;
        });
        StatementInstance defSubSig = matchField(iu, orderedPath, AliasAnalysis::getFieldSubSignature);
        StatementSet ret = new StatementSet();
        if (defSubSig != null) {
            int index = orderedPath.indexOf(defSubSig);
            ret.addAll(orderedPath.subList((SEARCH_SUBFIELDS)? 0:index, index+1));
        }
        return lastDefined(ret);
    }


    public static String getFieldSubSignature(SootField sf) {
        return sf.getSubSignature();
    }

    private void addInstances(StatementList orderedPath) {
        boolean found = false;
        for (StatementInstance si: orderedPath) {
            if (traversal.isFrameworkMethod(si) && !si.isAssign() && (AnalysisUtils.getCallerExp(si) instanceof InstanceInvokeExpr)) {
                found = matchReferenceToVariable(orderedPath, found, si);
            }
            if (found) {
                return;
            }
        }
    }


    private boolean matchReferenceToVariable(StatementList orderedPath, boolean found, StatementInstance si)
            throws Error {
        Value base = ((InstanceInvokeExpr) AnalysisUtils.getCallerExp(si)).getBase();
        StatementMap chunk = traversal.getChunk(si.getLineNo());
        if (chunk != null) {
            for (StatementInstance sii: chunk.values()) {
                if (sii.isAssign() && (((AssignStmt) sii.getUnit()).getLeftOp().equals(base))) {
                    orderedPath.add(sii);
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    private StatementSet lastDefined(StatementSet aliasPath) {
        StatementSet lastDefined = new StatementSet();
        Set<Pair<String, Type>> definedFields = new HashSet<>();
        for (StatementInstance si: aliasPath) {
            if (si.isAssign()) {
                Value leftOp = ((AssignStmt) si.getUnit()).getLeftOp();
                Value rightOp = ((AssignStmt) si.getUnit()).getRightOp();
                if (leftOp instanceof FieldRef) {
                    SootField sf = ((FieldRef) leftOp).getField();
                    Pair<String, Type> fieldNameType = new Pair<>(sf.getName(), sf.getType());
                    if (!definedFields.contains(fieldNameType)) {
                        lastDefined.add(si);
                        definedFields.add(fieldNameType);
                    }
                } else if (rightOp instanceof InvokeExpr) {
                    lastDefined.add(si);
                }
            } else {
                lastDefined.add(si);
            }
            
        }
        return lastDefined;
    }

    private void startAliasAnalysis(StatementInstance iu, AccessPath ap, StatementSet aliasPath, CallbackDetection callbackDetection) {
        AliasSet aliasedVars = new AliasSet();
        aliasedVars.add(ap);
        AnalysisCache.reset();
        if (ap.isStaticField()) {
            BackwardStaticFieldAnalysis bw = new BackwardStaticFieldAnalysis(icdg, iu, ap, aliasPath);
            bw.run();
        } else {
            BackwardAliasAnalysis bw = new BackwardAliasAnalysis(icdg, iu, aliasedVars, aliasPath, iu.getLineNo(), callbackDetection);
            Set<AliasAnalysis> bwContinuation = bw.run();
            callAnalysisContinuation(bwContinuation, aliasPath);
        }
    }

    private StatementInstance checkInstructionSignature(String instanceStr, StatementInstance nextInstr) {
        StatementInstance def = null;
        if (nextInstr != null) {
            Stmt nextStmt = (Stmt) nextInstr.getUnit();
            if (nextStmt.containsInvokeExpr()) {
                InvokeExpr expr = nextStmt.getInvokeExpr();
                if ((expr instanceof InstanceInvokeExpr) && ((InstanceInvokeExpr) expr).getBase().toString().equals(instanceStr)) {
                    def = nextInstr;
                }
            }
        }
        return def;
    }

    private StatementInstance matchField(StatementInstance iu, StatementList orderedPath, Function<SootField, String> signatureFunc) {
        StatementInstance def = null;
        if (!(iu.getUnit() instanceof AssignStmt)) {
            return null;
        }
        if (!((((AssignStmt) iu.getUnit()).getRightOp()) instanceof FieldRef)) {
            return null;
        }
        SootField sf = ((FieldRef) (((AssignStmt) iu.getUnit()).getRightOp())).getField();
        for (int i = 0; i < orderedPath.size(); i++) {
            StatementInstance instr = orderedPath.get(i);
            Unit u = instr.getUnit();
            if ((u instanceof AssignStmt) && iu.isAfter(instr)) {
                if (instr.containsInvokeExpr()) {
                    def = checkAssignInvokeSignature(sf, instr, signatureFunc);
                } else {
                    def = checkAssignSignature(sf, i, instr, orderedPath, signatureFunc);
                }
            }
            if (def != null) {
                break;
            }
        }
        AnalysisLogger.log(Constants.DEBUG, "Found a def {}", def);
        return def;
    }


    private StatementInstance checkAssignInvokeSignature(SootField sf, StatementInstance instr, Function<SootField, String> signatureFunc) {
        StatementInstance matched = null;
        Value invokeExpr = ((AssignStmt) instr.getUnit()).getRightOp();
        if (invokeExpr instanceof InvokeExpr) {
            Stmt stmt = (Stmt) instr.getUnit();
            if (stmt.containsInvokeExpr()) {
                InvokeExpr expr = stmt.getInvokeExpr();
                if ((expr instanceof InstanceInvokeExpr)) {
                    String base = ((InstanceInvokeExpr) expr).getBase().toString();
                    matched = matchInstanceToField(sf, instr, signatureFunc, matched, base);
                }
            }
        }
        return matched;
    }


    private StatementInstance matchInstanceToField(SootField sf, StatementInstance instr,
            Function<SootField, String> signatureFunc, StatementInstance matched, String base) {
        StatementInstance prev = icdg.mapNoUnits(instr.getLineNo()-1);
        if (prev != null && prev.isAssign()) {
            Value right = ((AssignStmt) prev.getUnit()).getRightOp();
            if ((right instanceof FieldRef) && signatureFunc.apply(((FieldRef) right).getField()).equals(signatureFunc.apply(sf))) {
                Value left = ((AssignStmt) prev.getUnit()).getLeftOp();
                if (left.toString().equals(base)) {
                    matched = instr;
                }
            }
        }
        return matched;
    }

    private StatementInstance checkAssignSignature(SootField sf, int i, StatementInstance instr, StatementList orderedPath, Function<SootField, String> signatureFunc) {
        AssignStmt stmt = (AssignStmt) instr.getUnit();
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();
        StatementInstance def = null;
        if ((left instanceof FieldRef) && signatureFunc.apply(((FieldRef) left ).getField()).equals(signatureFunc.apply(sf))) {
            def = instr;
        } else if ((right instanceof FieldRef) && signatureFunc.apply(((FieldRef) right).getField()).equals(signatureFunc.apply(sf)) && i > 0) {
            String instanceStr = stmt.getLeftOp().toString();
            StatementInstance nextInstr = icdg.mapNoUnits(instr.getLineNo()+1);
            def = checkInstructionSignature(instanceStr, nextInstr);
            if (def == null) {
                nextInstr = orderedPath.get(i-1);
                def = checkInstructionSignature(instanceStr, nextInstr);
            }
        }
        return def;
    }

    AccessPath getUsedField(StatementInstance si) {
        AccessPath accessPath = new AccessPath(si.getLineNo(), AccessPath.NOT_DEFINED, si);
        Unit u = si.getUnit();
        if (u instanceof AssignStmt) {
            AssignStmt stmt = (AssignStmt) u;
            Value r = stmt.getRightOp();
            if (r instanceof FieldRef) {
                for (ValueBox vb: ((FieldRef) r).getUseBoxes()){
                    accessPath = new AccessPath(vb.getValue().toString(), ((Local) vb).getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                }
                accessPath.add(((FieldRef) r).getField().getName(), ((FieldRef) r).getField().getType(), si);
            }
        }
        return accessPath;
    }

    public Pair<AliasAnalysisResult, AliasAnalysisResult> forwardAliasInMethod(AliasSet aliasSet, StatementInstance iu, Stmt stmt, StatementSet aliasPath) {
        AliasAnalysisResult aliasAnalysisResultInMethod = null;
        AliasAnalysisResult aliasAnalysisResultOutMethod = null;
        AliasSet aliasedParams = getAliasedParam(aliasSet, stmt);
        StatementMap calledChunk = traversal.getCalledChunk(iu.getLineNo()).getChunk();
        AliasSet forwardAliasSet = aliasSet.subtract(aliasedParams);
        if (calledChunk == null && !aliasedParams.isEmpty()){
            FrameworkModel.forwardWrapper(aliasSet, forwardAliasSet, iu, aliasPath);
            aliasAnalysisResultInMethod = new AliasAnalysisResult(iu, new AliasSet(), aliasedParams);
        } else if (!aliasedParams.isEmpty()) {
            aliasAnalysisResultInMethod = new AliasAnalysisResult(iu, new AliasSet(), aliasedParams);
        }
        aliasAnalysisResultOutMethod = new AliasAnalysisResult(iu, new AliasSet(), forwardAliasSet);
        return new Pair<>(aliasAnalysisResultInMethod, aliasAnalysisResultOutMethod);
    }


    public AliasSet getAliasedParam(AliasSet aliasSet, Stmt stmt) {
        AliasSet aliasedParams = new AliasSet();
        for (ValueBox vb: stmt.getUseBoxes()){
            Value param = vb.getValue();
            for (AccessPath ap: aliasSet) {
                if(ap.startsWith(param.toString()) || ap.isStaticField()) {
                    aliasedParams.add(ap);
                }
            }
        }
        return aliasedParams;
    }


    public AliasAnalysisResult backwardGenMethodWrapper(AliasSet aliasSet, StatementInstance iu, StatementSet aliasPath) {
        AliasSet fwSet = new AliasSet();
        AliasSet bwAliasSet = new AliasSet(aliasSet);
        FrameworkModel.backwardWrapper(fwSet, aliasSet, bwAliasSet, iu, aliasPath);
        return new AliasAnalysisResult(iu, bwAliasSet, fwSet);
    }

    public Pair<AliasAnalysisResult, AliasAnalysisResult> goBackInMethod(AliasSet aliasSet, StatementInstance si, AccessPath leftAp, Pair<CalledChunk, AccessPath> p) {
        Pair<AliasSet, AliasSet> changedScope = traversal.changeScopeToCalled(si, aliasSet);
        AliasSet paramSet = changedScope.getO1();
        AliasSet remainingSet = changedScope.getO2();
        if (leftAp != null) {
            for (AccessPath ap: aliasSet) {
                if (ap.startsWith(leftAp.getPath())) {
                    if (p.getO1().getRetVariable() != null && !p.getO1().getRetVariable().equals("")) {
                        AccessPath retAp = new AccessPath(p.getO2().getBase().getO1(), p.getO2().getBase().getO2(), ap.getUsedLine(), ap.getDefinedLine(), si);
                        retAp.add(ap.getAfter(leftAp.getPath()).getO1(), ap.getAfter(leftAp.getPath()).getO2(), si);
                        paramSet.add(retAp);
                    }
                }
            }
        }
        CalledChunk calledChunk = p.getO1();
        AliasAnalysisResult aliasAnalysisResultInMethod = new AliasAnalysisResult(calledChunk.getRetIu(), paramSet, new AliasSet());
        AliasAnalysisResult aliasAnalysisResultOutMethod = new AliasAnalysisResult(si, remainingSet, new AliasSet());
        return new Pair<>(aliasAnalysisResultInMethod, aliasAnalysisResultOutMethod);
    }

    public void callAnalysisContinuation(Set<AliasAnalysis> continuations, StatementSet aliasPath) {
        Deque<AliasAnalysis> analysisStack = new ArrayDeque<>();
        analysisStack.addAll(continuations);
        int oldAliasPathSize = 0;
        while (!analysisStack.isEmpty()) {
            AliasAnalysis analysisContinuation = analysisStack.pop();
            if (analysisCount > 0 && analysisCount % 100000 == 0) {
                if (aliasPath.size() == oldAliasPathSize) {
                    return;
                }
                oldAliasPathSize = aliasPath.size();
            }
            if (shouldRunForwardAnalysis(analysisContinuation) || shouldRunBackwardAnalysis(analysisContinuation)) {
                AliasAnalysis.incrementAnalysisCount();
                AnalysisRunner analysisRunner = new AnalysisRunner(analysisContinuation, analysisStack);
                analysisRunner.run();
            }
        }
    }


    private boolean shouldRunBackwardAnalysis(AliasAnalysis analysisContinuation) {
        return (analysisContinuation instanceof BackwardAliasAnalysis) && (!((BackwardAliasAnalysis) analysisContinuation).startFields.isEmpty());
    }


    private boolean shouldRunForwardAnalysis(AliasAnalysis analysisContinuation) {
        return (analysisContinuation instanceof ForwardAliasAnalysis) && (!((ForwardAliasAnalysis) analysisContinuation).startFields.isEmpty());
    }

    public AccessPath getFieldAccessPath(Value value, int usedAt, int definedAt, StatementInstance si) {
        AccessPath ap = new AccessPath(usedAt, definedAt, si);
        if (((FieldRef) value).getUseBoxes().isEmpty()) {
            ap = new AccessPath(((FieldRef) value).getField().getDeclaringClass().getName(), ((FieldRef) value).getField().getType(), usedAt, definedAt, si);
            ap.setStaticField();
        } else {
            for (ValueBox vb: ((FieldRef) value).getUseBoxes()){
                ap = new AccessPath(vb.getValue().toString(), vb.getValue().getType(), usedAt, definedAt, si);
            }
        }
        ap.add(((FieldRef) value).getField().getName(), ((FieldRef) value).getField().getType(), si);
        return ap;
    }


    public AccessPath getRightAccessPath(Value r, StatementInstance si) {
        AccessPath rightAp;
        try {
            rightAp = new AccessPath(r.getUseBoxes().get(0).getValue().toString(), r.getUseBoxes().get(0).getValue().getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
        } catch (IndexOutOfBoundsException e){
            rightAp = new AccessPath(r.toString(), r.getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
        }
        return rightAp;
    }

    public Pair<AliasAnalysisResult, AliasAnalysisResult> assignmentAliasAnalysis(AliasSet aliasSet, StatementInstance si, StatementSet aliasPath) {

        AliasSet backwardAliasSet = initializeBackwardSet(aliasSet);

        AliasSet forwardAliasSet = initilizeForwardSet(aliasSet);
        
        Unit u = si.getUnit();
        AssignStmt stmt = (AssignStmt) u;
        Value l = stmt.getLeftOp();
        Value r = stmt.getRightOp();

        if ((r instanceof InvokeExpr) && (this instanceof BackwardAliasAnalysis)) {
            return ((BackwardAliasAnalysis) this).assignInvokeAliasAnalysis(aliasSet, si);
        }

        if ((r instanceof InvokeExpr) && (this instanceof ForwardAliasAnalysis)) {
            return ((ForwardAliasAnalysis) this).assignInvokeAliasAnalysis(aliasSet, si);
        }

        AccessPath lhs = extractLHSofAssign(si, l);
        AccessPath rhs = extractRHSofAssign(si, l, r);

        matchToRHS(aliasSet, si, backwardAliasSet, forwardAliasSet, lhs, rhs);
        matchToLHS(aliasSet, si, backwardAliasSet, forwardAliasSet, lhs, rhs);

        updateAliasPath(aliasSet, si, aliasPath, lhs, rhs);

        return new Pair<>(null, new AliasAnalysisResult(si, backwardAliasSet, forwardAliasSet));
    }


    private void matchToLHS(AliasSet aliasSet, StatementInstance si, AliasSet backwardAliasSet, AliasSet forwardAliasSet,
            AccessPath lhs, AccessPath rhs) {
        for (AccessPath v: aliasSet) {
            matchSubFieldDefintions(si, backwardAliasSet, forwardAliasSet, lhs, rhs, v);
            matchFullFieldDefintions(si, backwardAliasSet, forwardAliasSet, lhs, rhs, v);
        }
    }


    private void matchFullFieldDefintions(StatementInstance si, AliasSet backwardAliasSet, AliasSet forwardAliasSet, AccessPath lhs,
            AccessPath rhs, AccessPath v) {
        if (lhs.isPrefixOf(v) || lhs.pathEquals(v)) {
            // instance is a full re-definition of v
            if (this instanceof BackwardAliasAnalysis) {
                if (v.getUsedLine() > si.getLineNo()) {
                    // Do not search before the definition
                    backwardAliasSet.remove(v);
                    // Follow the assigned variable both backward and forward
                    backwardAliasSet.add(v.extendFields(rhs, lhs, si.getLineNo(), v.getDefinedLine(), si));
                    forwardAliasSet.add(v.extendFields(rhs, lhs, si.getLineNo(), v.getDefinedLine(), si));
                }
            } else if (this instanceof ForwardAliasAnalysis) {
                backwardAliasSet.add(v.extendFields(rhs, lhs, si.getLineNo(), v.getDefinedLine(), si));
                forwardAliasSet.add(v.extendFields(rhs, lhs, si.getLineNo(), v.getDefinedLine(), si));
                forwardAliasSet.remove(v);
            }
        }
    }


    private void matchSubFieldDefintions(StatementInstance si, AliasSet backwardAliasSet, AliasSet forwardAliasSet, AccessPath lhs,
            AccessPath rhs, AccessPath v) {
        if (v.isPrefixOf(lhs) && rhs != null) {
            // instance is a re-definition of a field of v
            if (v.hasAfter(lhs)) {
                forwardAliasSet.add(new AccessPath(rhs, si.getLineNo(), v.getDefinedLine(), si));
            }
            backwardAliasSet.add(new AccessPath(rhs, si.getLineNo(), v.getDefinedLine(), si));
        }
    }


    private void matchToRHS(AliasSet aliasSet, StatementInstance si, AliasSet backwardAliasSet, AliasSet forwardAliasSet,
            AccessPath lhs, AccessPath rhs) {
        for (AccessPath v: aliasSet) {
            if (v.hasCommonPrefix(rhs)) {
                // lhs is a new alias for v
                forwardAliasSet.add(v.extendFields(lhs, rhs, v.getUsedLine(), si.getLineNo(), si));
                if (lhs.isParameter(si)) { // removed to fix test case 5
                    // add lhs to V_b for fields and globals
                    backwardAliasSet.add(v.extendFields(lhs, rhs, v.getUsedLine(), si.getLineNo(), si));
                }
            }
        }
    }


    private void updateAliasPath(AliasSet aliasSet, StatementInstance si, StatementSet aliasPath, AccessPath lhs,
            AccessPath rhs) {
        if (lhs.length() > 1) {
            for (AccessPath v: aliasSet) {
                updateAliasPathBackward(si, aliasPath, lhs, v);
                updateAliasPathForward(si, aliasPath, lhs, rhs, v);
            }
        }
    }


    private void updateAliasPathForward(StatementInstance si, StatementSet aliasPath, AccessPath lhs, AccessPath rhs, AccessPath v) {
        if ((this instanceof ForwardAliasAnalysis) && 
                (v.isPrefixOf(lhs) || 
                variableIsPrameterUsedAfterDefintion(si, aliasPath, lhs, v) || 
                variableIsLocalDefinedBeforeSubfieldReDefintion(si, aliasPath, lhs, v) || 
                variableIsCompleteReDefintion(rhs, v))
                ) {
            aliasPath.add(si);
        }
    }


    private boolean variableIsCompleteReDefintion(AccessPath rhs, AccessPath v) {
        return rhs != null && v.pathEquals(rhs);
    }


    private boolean variableIsLocalDefinedBeforeSubfieldReDefintion(StatementInstance si, StatementSet aliasPath, AccessPath lhs, AccessPath v) {
        return !lhs.isParameter(si) && v.pathEquals(lhs) && v.addedSideIsPrefixOf(lhs) && ((v.getDefinedLine() != AccessPath.NOT_DEFINED)) && !matchField(si, aliasPath);
    }


    private boolean variableIsPrameterUsedAfterDefintion(StatementInstance si, StatementSet aliasPath, AccessPath lhs, AccessPath v) {
        return lhs.isParameter(si) && v.pathEquals(lhs) && (v.getDefinedLine() == AccessPath.NOT_DEFINED) && !matchField(si, aliasPath);
    }


    private void updateAliasPathBackward(StatementInstance si, StatementSet aliasPath, AccessPath lhs, AccessPath v) {
        if (this instanceof BackwardAliasAnalysis) {
            if (v.getUsedLine() > lhs.getUsedLine()) {
                if (v.isPrefixOf(lhs) || (v.pathEquals(lhs) && !matchField(si, aliasPath))) {
                    aliasPath.add(si);
                }
            }
        }
    }


    private AccessPath extractRHSofAssign(StatementInstance si, Value l, Value r) {
        AccessPath rhs = null;
        if (l instanceof FieldRef) {
            if (!(r instanceof Constant) && !(AnalysisUtils.isPrimitiveType(r))) {
                rhs = new AccessPath(r.toString(), r.getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
            }
        } else if (l instanceof ArrayRef){
            if (!(r instanceof Constant) && !(AnalysisUtils.isPrimitiveType(r))) {
                rhs = new AccessPath(r.toString(), r.getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
            }
        } else {
            if (r instanceof FieldRef) {
                rhs = getFieldAccessPath(r, AccessPath.NOT_USED, si.getLineNo(), si);
            } else if (r instanceof NewExpr) {
                rhs = new AccessPath(si.getLineNo(), AccessPath.NOT_DEFINED, si);
            } else if (r instanceof Local || r instanceof CastExpr){
                rhs = getRightAccessPath(r, si);
            }
        }
        return rhs;
    }


    private AccessPath extractLHSofAssign(StatementInstance si, Value l) {
        AccessPath lhs = null;
        if (l instanceof FieldRef) {
            lhs = getFieldAccessPath(l, AccessPath.NOT_USED, si.getLineNo(), si);
        } else if (l instanceof ArrayRef){
            lhs = new AccessPath(((ArrayRef) l).getBase().toString(), ((ArrayRef) l).getBase().getType(), AccessPath.NOT_USED, si.getLineNo(), si);
        } else {
            lhs = new AccessPath(l.toString(), l.getType(), AccessPath.NOT_USED, si.getLineNo(), si);
        }
        return lhs;
    }


    private AliasSet initilizeForwardSet(AliasSet aliasSet) throws Error {
        AliasSet forwardAliasSet;
        if (this instanceof BackwardAliasAnalysis) {
            forwardAliasSet = new AliasSet();
        } else if (this instanceof ForwardAliasAnalysis) {
            forwardAliasSet = new AliasSet(aliasSet);
        } else {
            throw new Error("Unsupported alias analysis type");
        }
        return forwardAliasSet;
    }


    private AliasSet initializeBackwardSet(AliasSet aliasSet) throws Error {
        AliasSet backwardAliasSet;
        if (this instanceof BackwardAliasAnalysis) {
            backwardAliasSet = new AliasSet(aliasSet);
        } else if (this instanceof ForwardAliasAnalysis) {
            backwardAliasSet = new AliasSet();
        } else {
            throw new Error("Unsupported alias analysis type");
        }
        return backwardAliasSet;
    }

    private boolean matchField(StatementInstance iu, StatementSet aliasPath) {
        StatementInstance def = null;
        if (!(iu.getUnit() instanceof AssignStmt)) {
            return false;
        }
        SootField sf = ((FieldRef) (((AssignStmt) iu.getUnit()).getLeftOp())).getField();
        
        for (StatementInstance instr: aliasPath) {
            Unit u = instr.getUnit();
            if ((instr.getLineNo() < iu.getLineNo()) && (u instanceof AssignStmt)) {
                def = matchFieldSignature(def, sf, instr);
            }
            if (def != null) {
                break;
            }
        }
    
        return def != null;
    }

    private StatementInstance matchFieldSignature(StatementInstance def, SootField sf, StatementInstance instr) {
        Unit u = instr.getUnit();
        AssignStmt stmt = (AssignStmt) u;
        Value left = stmt.getLeftOp();
        if ((left instanceof FieldRef) && ((FieldRef) left ).getField().getSubSignature().equals(sf.getSubSignature())) {
            def = instr;
        } 
        return def;
    }
}

