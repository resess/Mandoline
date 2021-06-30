package ca.ubc.ece.resess.slicer.dynamic.mandoline.datadependence;

import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.CallerContext;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.Traversal;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;

import soot.SootField;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jgrapht.Graphs;


public class CallbackDetection {

    private ArrayList<Integer> callLocations = new ArrayList<>();
    private ArrayList<Integer> returnLocations = new ArrayList<>();
    private DynamicControlFlowGraph icdg;
    private Traversal traversal;

    public CallbackDetection(DynamicControlFlowGraph icdg, StatementInstance start, AccessPath field){
        this.icdg = icdg;
        this.traversal = new Traversal(icdg);
        Set<String> addedMethods = new LinkedHashSet<>();
        for (int i = start.getLineNo(); i >=0; i--) {
            StatementInstance si = icdg.mapNoUnits(i);
            if (si == null) {
                continue;
            }
            findCandidateCallbacks(field, addedMethods, si);
        }
        ArrayList<Integer> flipped = flipArray(callLocations);
        callLocations = new ArrayList<>(flipped);
        flipped = flipArray(returnLocations);
        returnLocations = new ArrayList<>(flipped);
    }


    private void findCandidateCallbacks(AccessPath field, Set<String> addedMethods, StatementInstance si) {
        Unit u = si.getUnit();
        if (u instanceof AssignStmt) {
            Value leftOp = ((AssignStmt) u).getLeftOp();
            if (leftOp instanceof FieldRef) {
                findCallbacksReachableFromField(field, addedMethods, si, leftOp);
            }
        }
    }


    private void findCallbacksReachableFromField(AccessPath field, Set<String> addedMethods, StatementInstance si,
            Value leftOp) {
        SootField sf = ((FieldRef) leftOp).getField();
        if (sf.getSubSignature().equals(field.getFieldSubSignature())) {
            int oldCaller = 0;
            int newCaller = si.getLineNo();
            while (newCaller != oldCaller) {
                if (!addedMethods.contains(icdg.mapNoUnits(newCaller).getMethod().getSignature())) {
                    int stmtNumber = traversal.getFirstStmt(newCaller);
                    if (!callLocations.contains(stmtNumber)) {
                        callLocations.add(stmtNumber);
                    }
                    stmtNumber = traversal.getLastStmt(newCaller);
                    if (!returnLocations.contains(stmtNumber)) {
                        returnLocations.add(stmtNumber);
                    }
                }
                addedMethods.add(icdg.mapNoUnits(newCaller).getMethod().getSignature());
                oldCaller = newCaller;
                newCaller = traversal.getCaller(oldCaller);
            }
        }
    }


    private ArrayList<Integer> flipArray(ArrayList<Integer> array) {
        ArrayList<Integer> flipped = new ArrayList<>();
        for (int i = array.size()-1; i >= 0; i--) {
            flipped.add(array.get(i));
        }
        return flipped;
    }

    public Set<CallerContext> getBackwardCallers(StatementInstance lastCaller, AliasSet taintSet, Integer lowerBound) {
        Set<CallerContext> ccList = new LinkedHashSet<>();
        CallerContext cc = getBackwardCaller(lastCaller, taintSet);
        if (cc != null && cc.getCaller() != null) {
            ccList.add(cc);
        }
        ccList.addAll(getBackwardCallbacks(lastCaller, taintSet, lowerBound));
        return ccList;
    }



    Set<CallerContext> getBackwardCallbacks (StatementInstance iu, AliasSet taintSet, Integer lowerBound) {
        if (iu==null) {
            return null;
        }
        Set<CallerContext> ccSet = new LinkedHashSet<>();
        int pos = iu.getLineNo()-1;
        int start = callLocations.size()-1;
        for (int i = start; i >=0; i--) {
            Integer location = callLocations.get(i);
            if (location < pos) {
                start = i;
                break;
            }
        }
        for (int i = start; i >=0; i--) {
            Integer location = callLocations.get(i);
            if (location > lowerBound) {
                break;
            }
            matchBackwardCallback(iu, taintSet, ccSet, location);
        }
        return ccSet;
    }


    private void matchBackwardCallback(StatementInstance iu, AliasSet taintSet, Set<CallerContext> ccSet,
            Integer location) {
        StatementInstance possibleCallback = icdg.mapNoUnits(location);
        if (possibleCallback != null) {
            for (AccessPath accessPath: taintSet) {
                int index = accessPath.classIndex(possibleCallback.getClassType());
                if (index != -1) {
                    AccessPath newAccessPath = accessPath.clipFromIndex(index, accessPath.getUsedLine(), accessPath.getDefinedLine(), iu);
                    newAccessPath.replaceBase("r0");
                    CallerContext callerContext = traversal.getCallerForwardChunk(possibleCallback, new AliasSet(newAccessPath));
                    callerContext.setCallback(true);
                    if (!callerContext.getAliasedArgs().isEmpty()) {
                        ccSet.add(callerContext);
                    }
                }
            }
        }
    }


    public CallerContext getBackwardCaller (StatementInstance iu, AliasSet taintSet) {
        if (iu==null) {
            return null;
        }
        CallerContext callerContext = new CallerContext();
        int caller = traversal.getCaller(iu.getLineNo());
        if (icdg.mapNoUnits(caller) == null) {
            return null;
        }
        AliasSet aliasedArgs = new AliasSet();
        StatementInstance callerSi = icdg.mapNoUnits(caller);
        callerContext.setCaller(icdg.mapNoUnits(caller));
        callerContext.setCallerChunk(traversal.getChunk(caller));
        Type t = callerSi.getMethod().getDeclaringClass().getType();
        for (AccessPath accessPath: taintSet) {
            int index = accessPath.classIndex(t);
            if (index != -1) {
                AccessPath newAccessPath = accessPath.clipFromIndex(index, accessPath.getUsedLine(), accessPath.getDefinedLine(), iu);
                newAccessPath.replaceBase("r0");
                aliasedArgs.add(newAccessPath);
            }
        }
        callerContext.setAliasedArgs(aliasedArgs);
        return callerContext;
    }

    public Set<CallerContext> nextCallback(StatementInstance si, AccessPath accessPath, Integer lowerBound) {
        Set<CallerContext> contexts = new HashSet<>();
        int pos = si.getLineNo()+1;
        for (Integer location: callLocations) {
            if (pos > location) {
                continue;
            }
            if (location > lowerBound) {
                break;
            }
            StatementInstance possibleCallback = icdg.mapNoUnits(location);
            if (possibleCallback == null || possibleCallback.methodEquals(si)) {
                continue;
            }
            accessPath = matchForwardCallback(possibleCallback, si, accessPath, contexts);
            if (accessPath.isEmpty()) {
                break;
            }
        }
        return contexts;
    }

    private AccessPath matchForwardCallback(StatementInstance possibleCallback, StatementInstance si, AccessPath accessPath, Set<CallerContext> contexts) {
        Type t = possibleCallback.getMethod().getDeclaringClass().getType();
        int index = accessPath.classIndex(t);
        if (index != -1) {
            accessPath = accessPath.clipFromIndex(index, accessPath.getUsedLine(), accessPath.getDefinedLine(), si);
            accessPath.replaceBase("r0");
            try {
                contexts.add(traversal.getCallerForwardChunk(possibleCallback, new AliasSet(accessPath)));
                accessPath = accessPath.clipFromIndex(1, accessPath.getUsedLine(), accessPath.getDefinedLine(), si);
            } catch (Exception e) {
                AnalysisLogger.warn(true, "Exception ignored: {}", e);
            }
        }
        return accessPath;
    }

    Set<CallerContext> getForwardCaller (StatementInstance iu, AliasSet taintSet, Integer lowerBound) {
        Set<CallerContext> callerContexts = new LinkedHashSet<>();
        if (iu==null) {
            return new LinkedHashSet<>();
        }
        int caller = traversal.getCaller(iu.getLineNo());
        if (Graphs.predecessorListOf(icdg.getGraph(), caller).isEmpty()){
            for (AccessPath ap: taintSet) {
                Set<CallerContext> contexts = nextCallback(iu, ap, lowerBound);
                for (CallerContext callerContext: contexts) {
                    if (callerContext != null) {
                        callerContext.setCallback(true);
                    }
                    callerContexts.add(callerContext);
                }
            }
            return callerContexts;
        }
        CallerContext callerContext = new CallerContext();
        callerContext.setCaller(icdg.mapNoUnits(caller));
        callerContext.setCallerChunk(traversal.getForwardChunk(caller));
        try {
            callerContext.setAliasedArgs(traversal.changeScopeToCaller(iu, callerContext.getCaller(), taintSet));
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            return new LinkedHashSet<>();
        }
        callerContexts.add(callerContext);
        return callerContexts;
    }

    public CallerContext getDoInBackGround(StatementInstance iu, Set<AccessPath> taintSet) {
        int pos = iu.getLineNo()-1;
        StatementInstance possibleDoInBack = icdg.mapNoUnits(pos);
        while (pos > 0) {
            possibleDoInBack = icdg.mapNoUnits(pos);
            if (possibleDoInBack != null && possibleDoInBack.getMethod().getName().equals("doInBackground") && (possibleDoInBack.classEquals(iu))) {
                break;
            }
            pos--;
        }
        return traversal.getCallerForwardChunk(possibleDoInBack, taintSet);
    }

    public CallerContext getSendMessage(StatementInstance iu, AliasSet taintSet) {
        CallerContext callerContext = new CallerContext();
        int pos = iu.getLineNo()-1;
        StatementInstance possibleSendMessage;
        while (pos > 0) {
            possibleSendMessage = icdg.mapNoUnits(pos);
            if (possibleSendMessage != null) {
                Unit possibleUnit = possibleSendMessage.getUnit();
                if (((Stmt) possibleUnit).containsInvokeExpr() && (((Stmt) possibleUnit).getInvokeExpr().getMethod().getSignature().equals("<android.os.Handler: boolean sendMessage(android.os.Message)>"))) {
                    break;
                }
            }
            pos--;
        }
        int caller = pos;
        callerContext.setCaller(icdg.mapNoUnits(caller));
        callerContext.setCallerChunk(traversal.getChunk(caller));
        try {
            callerContext.setAliasedArgs(traversal.changeScopeToCaller(iu, callerContext.getCaller(), taintSet));
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            return null;
        }
        return callerContext;
    }

}
