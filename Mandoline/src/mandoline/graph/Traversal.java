package mandoline.graph;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;

import mandoline.accesspath.AccessPath;
import mandoline.accesspath.AliasSet;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementMap;
import mandoline.utils.AnalysisCache;
import mandoline.utils.AnalysisLogger;
import mandoline.utils.AnalysisUtils;
import mandoline.utils.Constants;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

public class Traversal {

    private ICDG icdg;
    public Traversal(ICDG icdg) {
        this.icdg = icdg;
    }

    public Set<StatementInstance> previousNodes(StatementInstance iu) {
        Set<StatementInstance> previous = new LinkedHashSet<>();
        List<Integer> nodes = Graphs.predecessorListOf(icdg.getGraph(), iu.getLineNo());
        for (Integer node: nodes) {
            StatementInstance next = icdg.mapNoUnits(node);
            if (next != null) {
                previous.add(next);
            }
        }
        return previous;
    }

    public Set<StatementInstance> nextNodes(StatementInstance iu) {
        Set<StatementInstance> following = new LinkedHashSet<>();
        List<Integer> nodes = Graphs.successorListOf(icdg.getGraph(), iu.getLineNo());
        for (Integer node: nodes) {
            StatementInstance next = icdg.mapNoUnits(node);
            if (next != null) {
                following.add(next);
            }
        }
        return following;
    }



    public StatementMap getChunk(StatementInstance iu) {
        StatementMap chunk;
        if (iu.isReturn()) {
            chunk = iu.getReturnChunk();
        } else {
            chunk = getChunk(iu.getLineNo());
        }
        return chunk;
    }

    public StatementMap getChunk(int pos) {
        int startPos = pos;
        StatementMap cachedChunk = AnalysisCache.getFromBwChunkCache(pos);
        if (cachedChunk != null) {
            return cachedChunk;
        }
        StatementInstance iu = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(pos));
        if (iu == null) {
            return null;
        }
        String currentMethod = iu.getMethod().getSignature();
        StatementMap chunk = new StatementMap();
        boolean done = false;
        int newPos = 0;
        while(pos>=0 && !done) {
            iu = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(pos));
            if (iu!=null) {
                if(iu.getMethod().getSignature().equals(currentMethod)) {
                    chunk.put(iu.getUnitId(), iu);
                } else {
                    done = true;
                }
            }
            newPos = previousFlowEdge(pos, newPos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                done = true;
            }
        }
        AnalysisCache.putInBwChunkCache(startPos, chunk);
        return chunk;
    }

    private int previousFlowEdge(int pos, int newPos) {
        List<Integer> preds = Graphs.predecessorListOf(icdg.getGraph(), pos);
        for (Integer pred: preds) {
            DefaultWeightedEdge e = icdg.getGraph().getEdge(pred, pos);
            if (icdg.getGraph().getEdgeWeight(e) == Constants.FLOW_EDGE) {
                newPos = pred;
            }
        }
        return newPos;
    }

    public Pair<CalledChunk, AccessPath> getReturnIfStmtIsCall(int pos) {
        CalledChunk calledChunk = getCalledChunk(pos);
        if (calledChunk.getRetIu() != null){
            if (((Stmt) icdg.mapNoUnits(pos).getUnit()).getInvokeExpr().getMethod().getName().equals(calledChunk.getRetIu().getMethod().getName())) {
                if (calledChunk.getRetVariable() == null) {
                    InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(icdg.mapNoUnits(pos));
                    if (invokeExpr != null && !invokeExpr.getMethod().isStatic()) {
                        calledChunk.setRetVariable("r0");
                        calledChunk.setRetVarType(invokeExpr.getMethod().getDeclaringClass().getType());
                    }
                }
                if (calledChunk.getRetVariable() != null && calledChunk.getChunk() != null) {
                    return new Pair<>(calledChunk, new AccessPath(calledChunk.getRetVariable(), calledChunk.getRetVarType(), calledChunk.getRetIu().getLineNo(), AccessPath.NOT_DEFINED, calledChunk.getRetIu()));
                }
            }
        }
        return null;
    }

    public StatementMap getForwardChunk(int pos) {
        int startPos = pos;
        StatementMap cachedChunk = AnalysisCache.getFromFwChunkCache(pos);
        if (cachedChunk != null) {
            return cachedChunk;
        }
        StatementMap chunk = new StatementMap();
        StatementInstance iu = icdg.mapNoUnits(pos);
        int newPos = 0;
        String currentMethod = iu.getMethod().getSignature();
        while(pos>=0) {
            iu = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(pos));
            if (iu!=null) {
                if(iu.getMethod().getSignature().equals(currentMethod)) {
                    chunk.put(iu.getUnitId(), iu);
                } else {
                    break;
                }
            }
            newPos = nextFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }
        AnalysisCache.putInFwChunkCache(startPos, chunk);
        return chunk;
    }

    public int nextFlowEdge(int pos) {
        int newPos = pos;
        List<Integer> successors = Graphs.successorListOf(icdg.getGraph(), pos);
        Collections.sort(successors);
        for (Integer s: successors) {
            DefaultWeightedEdge e = icdg.getGraph().getEdge(pos, s);
            if (icdg.getGraph().getEdgeWeight(e) == Constants.FLOW_EDGE) {
                newPos = s;
            }
        }
        return newPos;
    }

    public CalledChunk getCalledChunk(int pos) {
        StatementInstance iu = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(pos));
        CalledChunk cached = AnalysisCache.getFromCalledChunkCache(iu);
        if (cached != null) {
            return cached;
        }
        CalledChunk calledChunk = new CalledChunk();
        int newPos = 0;
        boolean foundBody;
        Pair<Integer, Boolean> searchResult = searchForMethod(pos);
        pos = searchResult.getO1();
        foundBody = searchResult.getO2();
        if (icdg.getMapNoKey().get(pos) == null || !foundBody) {
            calledChunk.setChunk(null);
            AnalysisCache.putInCalledChunkCache(iu, calledChunk);
            return calledChunk;
        }
        iu = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(pos));
        calledChunk.setRetLine(pos);
        calledChunk.setRetIu(iu);
        String currentMethod = iu.getMethod().getSignature();
        while(pos>=0) {
            iu = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(pos));
            if (iu!=null) {
                if(iu.getMethod().getSignature().equals(currentMethod)) {
                    calledChunk.getChunk().put(iu.getUnitId(), iu);
                } else {
                    break;
                }
            }
            newPos = nextFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }

        if (iu == null) {
            calledChunk.setChunk(null);
            AnalysisCache.putInCalledChunkCache(iu, calledChunk);
            return calledChunk;
        }

        addReturnVariable(iu, calledChunk);

        AnalysisCache.putInCalledChunkCache(iu, calledChunk);
        return calledChunk;
    }


    private Pair<Integer, Boolean> searchForMethod(int pos) {
        Pair<Integer, Boolean> searchResult = new Pair<>();
        List<Integer> successors = Graphs.successorListOf(icdg.getGraph(), pos);
        searchResult.setO1(pos);
        searchResult.setO2(false);
        for (Integer s: successors) {
            DefaultWeightedEdge e = icdg.getGraph().getEdge(pos, s);
            if (icdg.getGraph().getEdgeWeight(e) == Constants.CALL_EDGE) {
                searchResult.setO1(s);
                searchResult.setO2(true);
                break;
            }
        }
        return searchResult;
    }




    private void addReturnVariable(StatementInstance iu, CalledChunk calledChunk) {
        if (iu.getUnit() instanceof ReturnStmt || iu.getUnit() instanceof ReturnVoidStmt) {
            calledChunk.setRetIu(iu);
            if (iu.getUnit() instanceof ReturnStmt) {
                calledChunk.setRetVariable(iu.getReturnVar());
                calledChunk.setRetVarType(iu.getReturnType());
            } else {
                calledChunk.setRetVariable(null);
            }
        }
        calledChunk.getRetIu().setReturnChunk(calledChunk.getChunk());
    }


    private int checkForCaller(int pos) {
        List<Integer> preds = Graphs.predecessorListOf(icdg.getGraph(), pos);
        for (Integer pred: preds) {
            DefaultWeightedEdge e = icdg.getGraph().getEdge(pred, pos);
            try {
                if (icdg.getGraph().getEdgeWeight(e) != Constants.FLOW_EDGE) {
                    pos = pred;
                    break;
                }
            } catch (NullPointerException ex) {
                pos = pred;
                break;
            }
        }
        return pos;
    }


    public int getFirstStmt (int pos) {
        int newPos = pos;
        while(pos>=0) {
            newPos = previousFlowEdge(pos, newPos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }
        return pos;
    }

    public int getLastStmt (int pos) {
        int newPos = pos;
        while(pos < icdg.getLastLine()) {
            newPos = nextFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }
        return pos;
    }



    public int getCaller (int pos) {
        int startPos = pos;
        Integer cachedPos = AnalysisCache.getFromCallerCache(startPos);
        if (cachedPos != null) {
            return cachedPos;
        }
        StatementInstance iu = icdg.mapNoUnits(pos);
        String currentMethod = iu.getMethod().getSignature();
        int newPos = pos;
        while(pos>=0) {
            iu = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(pos));
            if (iu!=null && !iu.getMethod().getSignature().equals(currentMethod)) {
                break;
            }
            newPos = previousFlowEdge(pos, newPos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                pos = checkForCaller(pos);
                break;
            }
        }
        AnalysisCache.putInCallerCache(startPos, pos);
        return pos;
    }


    public Pair<AliasSet, AliasSet> changeScopeToCalled(StatementInstance caller, AliasSet aliasSet) {
        AliasSet aliasedArgs = new AliasSet();
        AliasSet remainingSet = new AliasSet(aliasSet);
        AliasSet removedSet = new AliasSet();
        InvokeExpr callerExp = null;
        if (caller.getUnit() instanceof InvokeStmt) {
            callerExp = ((InvokeStmt) caller.getUnit()).getInvokeExpr();
        } else if (caller.getUnit() instanceof AssignStmt) {
            callerExp = ((AssignStmt) caller.getUnit()).getInvokeExpr();
        } else {
            AnalysisLogger.warn(true, "unsupported call stmt {}", caller);
            return new Pair<>(aliasedArgs, remainingSet);
        }
        
        List<Value> args = callerExp.getArgs();
        addReferenceVariableToArgs(callerExp, args);
        int argPos = 0;
        int inc = 0;
        if (icdg.getSetterCallbackMap().containsKey(new Pair<>(caller.getMethod(), caller.getUnit()))) {
            inc = 1;
        }
        for(Value arg: args) {
            for (AccessPath ap: aliasSet) {
                if (ap.baseEquals(arg.toString())) {
                    AccessPath aliasedArg = new AccessPath("$r"+String.valueOf(argPos+inc), arg.getType(), ap.getUsedLine(), ap.getDefinedLine(), caller).add(ap.getFields(), ap.getFieldsTypes(), caller);
                    aliasedArgs.add(aliasedArg);
                    removedSet.add(ap);
                }
            }
            argPos += 1;
        }
        for (AccessPath ap: aliasSet) {
            if (ap.isStaticField()) {
                aliasedArgs.add(ap);
                removedSet.add(ap);
            }
        }
        remainingSet = aliasSet.subtract(removedSet);
        return new Pair<>(aliasedArgs, remainingSet);
    }


    public CallerContext getCallerForwardChunk(StatementInstance iu, Set<AccessPath> aliasSet) {
        AliasSet newAliasSet = new AliasSet();
        CallerContext callerContext = AnalysisCache.getFromCallerForwardChunk(iu);
        if (callerContext == null) {
            callerContext = new CallerContext();
            int firstStmt = getFirstStmt(iu.getLineNo());
            StatementMap firstStmtChunk = getForwardChunk(firstStmt);
            callerContext.setForwardChunk(true);
            callerContext.setCallerChunk(firstStmtChunk);
            callerContext.setCaller(callerContext.getCallerChunk().values().iterator().next());
            AnalysisCache.putInCallerForwardChunk(iu, callerContext);
        }
        for (Unit uu: callerContext.getCaller().getMethod().getActiveBody().getUnits()) {
            if (uu instanceof IdentityStmt) {
                if (uu.toString().contains("@this") || uu.toString().contains("@parameter")) {
                    String base = uu.getDefBoxes().get(0).getValue().toString();
                    addAliasedAccessPaths(aliasSet, newAliasSet, base);
                }
            } else {
                break;
            }
        }
        addStaticAccessPaths(aliasSet, newAliasSet);
        callerContext.setAliasedArgs(newAliasSet);
        return callerContext;
    }

    private void addAliasedAccessPaths(Set<AccessPath> aliasSet, AliasSet newAliasSet, String base) {
        for (AccessPath ap: aliasSet) {
            if (ap.startsWith(base)) {
                newAliasSet.add(ap);
            }
        }
    }

    private void addStaticAccessPaths(Set<AccessPath> alaisSet, AliasSet newAliasSet) {
        for (AccessPath ap: alaisSet) {
            if (ap.isStaticField()) {
                newAliasSet.add(ap);
            }
        }
    }
    


    public AliasSet changeScope(AliasSet originalAliasSet, StatementInstance source, StatementInstance destination) {
        if (!source.methodEquals(destination)) {
            if (source.getLineNo() > destination.getLineNo()) {
                return changeScopeToCaller(destination, originalAliasSet);
            } else {
                return changeScopeToCalled(source, originalAliasSet).getO1();
            }
        } else {
            return originalAliasSet;
        }
    }

    public synchronized AliasSet changeScopeToCaller(StatementInstance caller, AliasSet aliasSet) {
        AliasSet aliasedArgs = new AliasSet();
        if (caller == null) {
            return aliasedArgs;
        }
        InvokeExpr callerExp = AnalysisUtils.getCallerExp(caller);
        if (callerExp == null) {
            return aliasedArgs;
        }
        
        List<Value> args = callerExp.getArgs();
        addReferenceVariableToArgs(callerExp, args);

        int inc = 0;
        if (icdg.getSetterCallbackMap().containsKey(new Pair<>(caller.getMethod(), caller.getUnit()))) {
            inc = 1;
        }
        int argPos = 0;
        for(Value arg: args) {
            for (AccessPath ap: aliasSet) {
                if (ap.getPath().isEmpty()) {
                    continue;
                }
                if (!callerExp.getMethod().isStatic() && !ap.getBase().getO1().startsWith("r")) {
                    inc = 1;
                }
                String start = ap.getBase().getO1().substring(0, 1);
                AccessPath p = new AccessPath(start+String.valueOf(argPos), arg.getType(), ap.getUsedLine(), ap.getDefinedLine(), caller);
                p.add(ap.getFields(), ap.getFieldsTypes(), caller);
                translateVaribleToCaller(caller, aliasedArgs, args, inc, argPos, ap, p);
                
            }
            argPos++;
        }
        propagateStaticVariablesToCaller(aliasSet, aliasedArgs);
        return aliasedArgs;
    }


    private void propagateStaticVariablesToCaller(AliasSet aliasSet, AliasSet aliasedArgs) {
        for (AccessPath v: aliasSet) {
            if (v.isStaticField()) {
                aliasedArgs.add(v);
            }
        }
    }

    private void addReferenceVariableToArgs(InvokeExpr callerExp, List<Value> args) {
        try {
            InstanceInvokeExpr instanceCallerExp = (InstanceInvokeExpr) callerExp;
            args.add(0, instanceCallerExp.getBase());
        } catch (Exception e) {
            // Ignored
        }
    }



    private void translateVaribleToCaller(StatementInstance caller, AliasSet aliasedArgs, List<Value> args, int inc, int argPos,
            AccessPath ap, AccessPath p) {
        try {
            if(p.pathEquals(ap)) {
                Value tArg = args.get(argPos+inc);
                if (!AnalysisUtils.isInteger(tArg.toString())) {
                    AccessPath aliasedArg = new AccessPath(tArg.toString(), tArg.getType(), ap.getUsedLine(), ap.getDefinedLine(), caller).add(p.getFields(), p.getFieldsTypes(), caller);
                    aliasedArgs.add(aliasedArg);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            // Ignored
        }
    }

    public boolean isFrameworkMethod(StatementInstance iu){
        return iu.containsInvokeExpr() && (getCalledChunk(iu.getLineNo()).getChunk() == null);
    }

}
