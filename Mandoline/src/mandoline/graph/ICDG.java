package mandoline.graph;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.util.Chain;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import mandoline.statements.StatementInstance;
import mandoline.utils.AnalysisLogger;
import mandoline.utils.Constants;
import soot.util.MultiMap;
import soot.jimple.infoflow.android.callbacks.AndroidCallbackDefinition;
import soot.toolkits.scalar.Pair;


public class ICDG {
    private Map<SootClass, SootMethod> callbackMethods = new HashMap<>();
    private Map<Pair<SootMethod, Unit>, String> threadCallers = new HashMap<>();
    private Set<String> threadMethods = new HashSet<>();
    private Map<Pair<SootMethod, Unit>, SootClass> setterCallbackMap = new HashMap<>();
    private Map<Pair<SootMethod, Unit>, Integer> setterLineMap = new HashMap<>();
    private Map<Pair<SootMethod, Unit>, Integer> threadLineMap = new HashMap<>();
    private Set<Long> threadIds = new HashSet<>();
    private Set<Pair<Integer, Integer>> connectedThreads = new HashSet<>();
    private Map<String, Long> mapThreadId = new LinkedHashMap<>();
    private Map<Integer, Long> fieldMap = new LinkedHashMap<>();
    private StatementInstance [] ins;

    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    private Map <Integer, StatementInstance> possibleCallbacks = new LinkedHashMap<>();
    private Map<Integer, Integer> nextUnitWithStaticField = new HashMap<>();
    private Map<Integer, Integer> previousUnitWithStaticField = new HashMap<>();
    private Map <String, StatementInstance> mapKeyUnits = new LinkedHashMap<>();
    private Map <Integer, String> mapNoKey = new LinkedHashMap<>();
    private Map <String, Integer> mapKeyNo = new LinkedHashMap<>();
    private long lastLine;

    public ICDG(Map<Pair<SootMethod, Unit>, SootClass> setterCallbackMap, MultiMap<SootClass, AndroidCallbackDefinition> callbackMethods, Map<Pair<SootMethod, Unit>, String> threadCallers) {
        this.setterCallbackMap = setterCallbackMap;
        for (AndroidCallbackDefinition acd: callbackMethods.values()){
            this.callbackMethods.put(acd.getTargetMethod().getDeclaringClass(), acd.getTargetMethod());
        }
        this.threadCallers = threadCallers;
        threadMethods.addAll(threadCallers.values());
    }

    public Map<Pair<SootMethod, Unit>, SootClass> getSetterCallbackMap() {
        return setterCallbackMap;
    }


    public ICDG createDCFG(List <Traces> tr) {
        int len = tr.size();
        AnalysisLogger.log(true, "Trace length: {}", len);
        Map<String, Map<Integer, String>> mapTrace = new LinkedHashMap<>();

        
        AnalysisLogger.log(true, "setterCallbackMap: {}", setterCallbackMap.toString());
        AnalysisLogger.log(true, "callbackMethods: {}", callbackMethods.toString());

        mapInstancesToMethods(tr, mapTrace, mapThreadId, fieldMap);

        addControlFlowsWithinMethods(mapTrace);
        
        
        ins = new StatementInstance[len];
        Chain<SootClass> chain = Scene.v().getApplicationClasses();
        Map<String, SootMethod> allMethods = createMethodsMap(chain);

        for (String key: mapTrace.keySet()) {
            SootMethod mt = allMethods.get(key);
            try {
                if(mt.getActiveBody()==null) { 
                    continue;
                }
            } catch(Exception ex) {
                continue;
            }
            if (mt.getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS)) {
                continue;
            }
            Body body = mt.getActiveBody();
            PatchingChain<Unit> units = body.getUnits();
            Map <String, Unit> unitString = new LinkedHashMap<>();
            Map<String, Pair<SootMethod, Unit>> settersInThisMethod = new HashMap<>();
            Map<String, Pair<SootMethod, Unit>> threadStartersInThisMethod = new HashMap<>();

            updateSettersMaps(mt, units, unitString, settersInThisMethod, threadStartersInThisMethod);

            addRegisterationEdgesForCallbacks(mapTrace, key, mt);

            addRegisterationEdgesForThreads(mapTrace, key, mt);

            Map <Integer, String> temp = mapTrace.get(key);
            for(Integer key1: temp.keySet()) {
                createStatementInstance(key, mt, unitString, settersInThisMethod, threadStartersInThisMethod, temp, key1);
            }
        }
        int i=0;
        while(i<ins.length) {
            if(ins[i]!=null){
                mapKeyNo.put(ins[i].getUnitId(), ins[i].getLineNo());
                mapKeyUnits.put(ins[i].getUnitId(), ins[i]);
                mapNoKey.put(ins[i].getLineNo(), ins[i].getUnitId());
            }
            i++;
        }
        setLastLine(ins.length);
        cleanGraphFromFalseEdges(connectedThreads);
        fixThreadsGraph();
        removeReturnEdges();
        savePossibleCallback();
        saveStaticFields();
        return this;
    }

    private void createStatementInstance(String key, SootMethod mt, Map<String, Unit> unitString,
            Map<String, Pair<SootMethod, Unit>> settersInThisMethod,
            Map<String, Pair<SootMethod, Unit>> threadStartersInThisMethod, Map<Integer, String> temp, Integer key1) {
        Integer leastDistance = Integer.MAX_VALUE;
        Unit closestUnit = null;
        boolean foundUnit = false;
        // AnalysisLogger.log(true, "Inspecting stmt {}", temp.get(key1));
        if(unitString.containsKey(temp.get(key1))) {
            foundUnit = matchStatementInstanceToTraceLine(key, mt, unitString, settersInThisMethod, threadStartersInThisMethod, temp, key1);
        }

        if (!foundUnit) {
            matchStatementInstanceToClosestTraceLine(key, mt, unitString, temp, key1, leastDistance, closestUnit);
        }
    }

    private void matchStatementInstanceToClosestTraceLine(String key, SootMethod mt, Map<String, Unit> unitString, Map<Integer, String> temp,
            Integer key1, Integer leastDistance, Unit closestUnit) {
        for(String us: unitString.keySet()) {
            String first = us;
            String second = temp.get(key1);
            if (first.contains("if") && first.contains("goto") && second.contains("if") && second.contains("goto")) {
                first = first.substring(0, first.indexOf("goto"));
                second = second.substring(0, second.indexOf("goto"));
            }
            if (StringUtils.getCommonPrefix(first, second).length() > 0) {
                int threshold = Math.min(first.length(), second.length())/2;
                Integer distance = (new LevenshteinDistance(threshold)).apply(first, second);
                if (distance == -1) {
                    distance = threshold;
                }
                if (distance < leastDistance) {
                    closestUnit = unitString.get(us);
                    leastDistance = distance;
                }
            }
        }
        try {
            StatementInstance iu = new StatementInstance(mt, closestUnit, key1, mapThreadId.get(key), fieldMap.get(key1), closestUnit.getJavaSourceStartLineNumber(), mt.getDeclaringClass().getFilePath());
            ins[key1] = iu;
        } catch (Exception e) {
            AnalysisLogger.warn(true, "Cannot create instruction {}", temp.get(key1));
        }
    }

    private boolean matchStatementInstanceToTraceLine(String key, SootMethod mt, Map<String, Unit> unitString,
            Map<String, Pair<SootMethod, Unit>> settersInThisMethod,
            Map<String, Pair<SootMethod, Unit>> threadStartersInThisMethod, Map<Integer, String> temp, Integer key1) {
        boolean foundUnit = false;
        String us = temp.get(key1);
        Unit unit = unitString.get(us);
        try {
            StatementInstance iu = new StatementInstance(mt, unit, key1, mapThreadId.get(key), fieldMap.get(key1), unit.getJavaSourceStartLineNumber(), mt.getDeclaringClass().getFilePath());
            ins[key1] = iu;
            if (settersInThisMethod.containsKey(us)) {
                setterLineMap.put(settersInThisMethod.get(us), key1);
            }
            if (threadStartersInThisMethod.containsKey(us)) {
                threadLineMap.put(threadStartersInThisMethod.get(us), key1);
            }
            foundUnit = true;
        } catch (Exception e) {
            AnalysisLogger.warn(true, "Cannot create instruction {}", temp.get(key1));
        }
        return foundUnit;
    }

    private void addRegisterationEdgesForThreads(Map<String, Map<Integer, String>> mapTrace, String key, SootMethod mt) {
        for (String threadMethod: threadMethods) {
            if (threadMethod.equals(mt.getSignature())) {
                for (Pair<SootMethod, Unit> threadStarters : threadCallers.keySet()) {
                    Integer source = threadLineMap.get(threadStarters);
                    Integer destination = mapTrace.get(key).entrySet().iterator().next().getKey();
                    try {
                        DefaultWeightedEdge e = graph.getEdge(source, destination);
                        if (e == null){
                            e = graph.addEdge(source, destination);
                        }
                        graph.setEdgeWeight(e, Constants.CALL_EDGE);
                        connectedThreads.add(new Pair<>(source, destination));
                    } catch (Exception e) {}
                    
                }
            }
        }
    }

    private void addRegisterationEdgesForCallbacks(Map<String, Map<Integer, String>> mapTrace, String key, SootMethod mt) {
        for (SootMethod cb: callbackMethods.values()) {
            if (cb.equals(mt)) {
                for (Pair<SootMethod, Unit> callbackSetter : setterCallbackMap.keySet()) {
                    connectCallback(mapTrace, key, mt, callbackSetter);
                }
            }
        }
    }

    private void connectCallback(Map<String, Map<Integer, String>> mapTrace, String key, SootMethod mt,
            Pair<SootMethod, Unit> callbackSetter) {
        try {
            if (callbackMethods.get(setterCallbackMap.get(callbackSetter)).equals(mt)) {
                Integer source = setterLineMap.get(callbackSetter);
                Integer destination = mapTrace.get(key).entrySet().iterator().next().getKey();
                DefaultWeightedEdge e = graph.getEdge(source, destination);
                if (e == null){
                    e = graph.addEdge(source, destination);
                }
                graph.setEdgeWeight(e, Constants.CALL_EDGE);
            }
        } catch (Exception e) {
            // Ignored
        }
    }

    private void updateSettersMaps(SootMethod mt, PatchingChain<Unit> units, Map<String, Unit> unitString,
            Map<String, Pair<SootMethod, Unit>> settersInThisMethod,
            Map<String, Pair<SootMethod, Unit>> threadStartersInThisMethod) {
        for(Unit u: units) {
            if (u instanceof IdentityStmt) {
                continue;
            }
            unitString.put(u.toString(), u);
            Pair<SootMethod, Unit> methodUnitPair = new Pair<>(mt, u);
            if (setterCallbackMap.containsKey(methodUnitPair)) {
                settersInThisMethod.put(u.toString(), methodUnitPair);
            }
            if (threadCallers.containsKey(methodUnitPair)) {
                threadStartersInThisMethod.put(u.toString(), methodUnitPair);
            }
        }
    }

    private Map<String, SootMethod> createMethodsMap(Chain<SootClass> chain) {
        Map<String, SootMethod> allMethods = new HashMap<>();
        Iterator<SootClass> iterator = chain.snapshotIterator();
        while(iterator.hasNext()) {
            SootClass sc = iterator.next();
            List<SootMethod> methods = sc.getMethods();
            for(SootMethod mt:methods) {
                allMethods.put(mt.getSignature(), mt);
            }
        }
        return allMethods;
    }

    private void addControlFlowsWithinMethods(Map<String, Map<Integer, String>> mapTrace) {
        for (String method : mapTrace.keySet()) {
            SortedSet<Integer> lines = new TreeSet<>(mapTrace.get(method).keySet());
            int prevKey = -1;
            for (Integer line: lines) {
                if (prevKey != -1) {
                    DefaultWeightedEdge e = graph.getEdge(prevKey, line);
                    if (e == null){
                        e = graph.addEdge(prevKey, line);
                    }
                    graph.setEdgeWeight(e, Constants.FLOW_EDGE);
                }
                prevKey = line;
            }
            if (prevKey != -1) {
                DefaultWeightedEdge e = graph.getEdge(prevKey, prevKey+1);
                if (e != null){
                    graph.setEdgeWeight(e, Constants.RETURN_EDGE);
                }
            }
        }
    }

    private int mapInstancesToMethods(List<Traces> tr, Map<String, Map<Integer, String>> mapTrace,
            Map<String, Long> mapThreadId, Map<Integer, Long> fieldMap) {
        int i=0;
        for (Traces t: tr) {
            if(!mapTrace.containsKey(t._method+t._class)) {
                Map <Integer, String> temp = new LinkedHashMap<>();
                temp.put(i, t._ins);
                mapTrace.put(t._method+t._class, temp);
            } else {
                Map <Integer, String> temp = mapTrace.get(t._method+t._class);
                temp.put(i, t._ins);
                mapTrace.put(t._method+t._class, temp);
            }
            if (t._tid != -1L) {
                threadIds.add(t._tid);
                mapThreadId.put(t._method+t._class, t._tid);
            }
            if (t._field != null) {
                fieldMap.put(i, t._field);
            }
            graph.addVertex(i);
            i++;
        }
        return i;
    }

    private void savePossibleCallback(){
        for (int i = 0; i < graph.vertexSet().size(); i++) {
            StatementInstance iu = mapNoUnits(i);
            if (iu == null) {
                continue;
            }
            if (Graphs.predecessorListOf(graph, iu.getLineNo()).isEmpty() && !iu.getMethod().getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS)) {
                addToPossibleCalbacks(iu.getLineNo(), iu);
            }
        }
    }

    private void saveStaticFields(){
        int prevUnit = -1;
        for (int i = 0; i < graph.vertexSet().size(); i++) {
            StatementInstance iu = mapNoUnits(i);
            if (iu == null) {
                continue;
            }
            Unit u = iu.getUnit();
            if (u instanceof AssignStmt) {
                AssignStmt stmt = (AssignStmt) u;
                Value left = stmt.getLeftOp();
                if (left instanceof FieldRef) {
                    if (((FieldRef) left).getUseBoxes().isEmpty()) {
                        putStaticFieldUnitEdge(prevUnit, iu.getLineNo());
                        prevUnit = iu.getLineNo();
                    }
                }
            }
        }
    }

    private void cleanGraphFromFalseEdges(Set<Pair<Integer, Integer>> connectedThreads){
        Set<DefaultWeightedEdge> removed = new HashSet<>();
        for (DefaultWeightedEdge e: graph.edgeSet()){
            Integer source = graph.getEdgeSource(e);
            Integer target = graph.getEdgeTarget(e);
            if (graph.getEdgeWeight(e) == Constants.CALL_EDGE || graph.getEdgeWeight(e) == Constants.RETURN_EDGE) {
                if (connectedThreads.contains(new Pair<>(source, target)) || mapNoUnits(source) == null || mapNoUnits(target) == null){
                    continue;
                }
                removeFlowEdgesAccrossThreads(removed, e, source, target);
            } else if (graph.getEdgeWeight(e) == Constants.FLOW_EDGE) {
                removeFlowEdgesAfterReturn(removed, e, source);
            }
        }
        graph.removeAllEdges(removed);
    }

    private void removeFlowEdgesAfterReturn(Set<DefaultWeightedEdge> removed, DefaultWeightedEdge e,
            Integer source) {
        if (mapNoUnits(source) != null && mapNoUnits(source).getUnit() != null && !mapNoUnits(source).getUnit().fallsThrough()) {
            // Removing false flow edges after returns 
            removed.add(e);
        }
    }

    private void removeFlowEdgesAccrossThreads(Set<DefaultWeightedEdge> removed, DefaultWeightedEdge e, Integer source,
            Integer target) {
        if (!mapNoUnits(source).getThreadID().equals( 
            mapNoUnits(target).getThreadID())) {
                removed.add(e);
        }
    }

    private void removeReturnEdges(){
        Set<DefaultWeightedEdge> removed = new HashSet<>();
        for (DefaultWeightedEdge e: graph.edgeSet()){
            Integer source = graph.getEdgeSource(e);
            if (graph.getEdgeWeight(e) == Constants.FLOW_EDGE) {
                // Removing false flow edges after returns 
                if (mapNoUnits(source) != null && mapNoUnits(source).getUnit() != null && mapNoUnits(source).isReturn()) {
                    removed.add(e);
                }
            }
        }
        graph.removeAllEdges(removed);
    }

    private void fixThreadsGraph(){
        Map<Long, ArrayList<StatementInstance>> threadTraces = separateThreads();
        for (Long tid: threadTraces.keySet()) {
            ArrayList<StatementInstance> unitsInThread = threadTraces.get(tid);
            for (int i = unitsInThread.size()-1; i >=0; i--) {
                StatementInstance iu = unitsInThread.get(i);
                if (iu == null) {
                    continue;
                }
                fixAccrossThreads(unitsInThread, i);
                fixConnectToCallers(unitsInThread, i);
                fixFlowEdges(unitsInThread, i);
            }
        }
    }

    private void fixFlowEdges(ArrayList<StatementInstance> unitsInThread, int i) {
        boolean fixed = false;
        if (Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).isEmpty()) {
            for (int j = i - 1; j >= 0; j--) {
                Unit possibleCaller = unitsInThread.get(j).getUnit();
                if (possibleCaller == null) {
                    continue;
                }
                fixed = connectFlowEdgesInSameMethod(unitsInThread, i, fixed, j, possibleCaller);
                if (fixed) {
                    break;
                }
            }
        }
        if (Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).isEmpty()) {
            conntectToPrev(unitsInThread, i);
        }
    }

    private boolean connectFlowEdgesInSameMethod(ArrayList<StatementInstance> unitsInThread, int i, boolean fixed,
            int j, Unit possibleCaller) {
        if (unitsInThread.get(j).getMethod().equals(unitsInThread.get(i).getMethod()) && isReturn(possibleCaller)){
            fixed = true;
        }
        if (unitsInThread.get(j).getMethod().equals(unitsInThread.get(i).getMethod()) && !isReturn(possibleCaller)){
            DefaultWeightedEdge e = graph.getEdge(unitsInThread.get(j).getLineNo(), unitsInThread.get(i).getLineNo());
            if (e == null){
                e = graph.addEdge(unitsInThread.get(j).getLineNo(), unitsInThread.get(i).getLineNo());
            }
            graph.setEdgeWeight(e, Constants.FLOW_EDGE);
            fixed = true;
        }
        return fixed;
    }

    private void fixConnectToCallers(ArrayList<StatementInstance> unitsInThread, int i) {
        if (Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).isEmpty()) {
            connectToCaller(unitsInThread, i);
        } else if (Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).size() == 1) {
            int posCaller = Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).get(0);
            DefaultWeightedEdge e = graph.getEdge(posCaller, unitsInThread.get(i).getLineNo());
            if (e != null && graph.getEdgeWeight(e) != Constants.CALL_EDGE) {
                connectToCaller(unitsInThread, i);
            }
        }
    }

    private void fixAccrossThreads(ArrayList<StatementInstance> unitsInThread, int i) {
        if (Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).isEmpty()) {
            conntectToPrevAcrossThread(unitsInThread.get(i).getLineNo());
        } else if (Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).size() == 1) {
            int posCaller = Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo()).get(0);
            DefaultWeightedEdge e = graph.getEdge(posCaller, unitsInThread.get(i).getLineNo());
            if (e != null && graph.getEdgeWeight(e) != Constants.CALL_EDGE) {
                conntectToPrevAcrossThread(unitsInThread.get(i).getLineNo());
            }
        }
    }

    private Map<Long, ArrayList<StatementInstance>> separateThreads() {
        Map<Long, ArrayList<StatementInstance>> threadTraces = new HashMap<>();
        for (int i = 0; i < graph.vertexSet().size(); i++) {
            StatementInstance iu = mapNoUnits(i);
            if (iu == null) {
                continue;
            }
            Long tid = iu.getThreadID();
            if (tid != null) {
                ArrayList<StatementInstance> l = threadTraces.get(tid);
                if (l == null) {
                    l = new ArrayList<>();
                }
                l.add(iu);
                threadTraces.put(tid, l);
            }
        }
        return threadTraces;
    }

    private boolean conntectToPrevAcrossThread (int lineNo) {
        Set<DefaultWeightedEdge> removed = new HashSet<>();
        StatementInstance iu = mapNoUnits(lineNo);
        if (iu == null) {
            return false;
        }
        int firstIu = getFirstUnit(lineNo);
        StatementInstance prev = null;
        int j = lineNo-1;
        prev = mapNoUnits(j);
        if (prev != null) {
            if (!prev.getMethod().equals(mapNoUnits(firstIu).getMethod())) {
                return false;
            }
            if (prev.containsInvokeExpr() && calledMethodInAppClasses(prev.getUnit())) {
                if (((Stmt) prev.getUnit()).getInvokeExpr().getMethod().getSubSignature().equals(iu.getMethod().getSubSignature())) {
                    if (!prev.getMethod().getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS)) {
                        DefaultWeightedEdge e = graph.getEdge(prev.getLineNo(), lineNo);
                        removeExistingEdges(lineNo, removed);
                        addCallEdge(lineNo, prev, e);
                        graph.removeAllEdges(removed);
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean calledMethodInAppClasses(Unit uu) {
        return Scene.v().getApplicationClasses().contains(((Stmt) uu).getInvokeExpr().getMethod().getDeclaringClass());
    }

    private void addCallEdge(int lineNo, StatementInstance prev, DefaultWeightedEdge e) {
        if (e == null){
            e = graph.addEdge(prev.getLineNo(), lineNo);
        }
        graph.setEdgeWeight(e, Constants.CALL_EDGE);
    }

    private void removeExistingEdges(int lineNo, Set<DefaultWeightedEdge> removed) {
        for (Integer pred: Graphs.predecessorListOf(graph, lineNo)){
            DefaultWeightedEdge p = graph.getEdge(pred, lineNo);
            if (p != null){
                removed.add(p);
            }
        }
    }

    private int getFirstUnit(int lineNo) {
        int firstIu = lineNo-1;
        for (int j = lineNo-1; j >= 0; j--) {
            StatementInstance first = mapNoUnits(j);
            if (first != null) {
                firstIu = j;
                break;
            }
        }
        return firstIu;
    }

    private boolean methodIsInAndroidLibs(ArrayList<StatementInstance> unitsInThread, int i) {
        return !mapNoUnits(unitsInThread.get(i).getLineNo()-1).getMethod().getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS);
    }

    private boolean conntectToPrev(ArrayList<StatementInstance> unitsInThread, int i) {
        Set<DefaultWeightedEdge> removed = new HashSet<>();
        boolean connected = false;
        if (mapNoUnits(unitsInThread.get(i).getLineNo()-1) == null){
            return connected;
        }
        Unit possibleCaller = mapNoUnits(unitsInThread.get(i).getLineNo()-1).getUnit();
        if (((Stmt) possibleCaller).containsInvokeExpr()) {
            if (((Stmt) possibleCaller).getInvokeExpr().getMethod().getSubSignature().equals(unitsInThread.get(i).getMethod().getSubSignature())) {
                connected = addCallEdgeToPrev(unitsInThread, i, removed, connected, possibleCaller);
            } else if (unitsInThread.get(i).getMethod().getName().equals("<clinit>")) {
                connected = addCallEdgeFromClassConstructorToPrev(unitsInThread, i, removed, connected, possibleCaller);
            }
        }
        graph.removeAllEdges(removed);
        return connected;
    }

    private boolean addCallEdgeFromClassConstructorToPrev(ArrayList<StatementInstance> unitsInThread, int i,
            Set<DefaultWeightedEdge> removed, boolean connected, Unit possibleCaller) {
        if (((Stmt) possibleCaller).getInvokeExpr().getMethod().getDeclaringClass().getName().equals(unitsInThread.get(i).getMethod().getDeclaringClass().getName())) {
            DefaultWeightedEdge e = graph.getEdge(unitsInThread.get(i).getLineNo()-1, unitsInThread.get(i).getLineNo());
            if (e == null){
                removePrevEdges(unitsInThread, i, removed);
                e = graph.addEdge(unitsInThread.get(i).getLineNo()-1, unitsInThread.get(i).getLineNo());
                graph.setEdgeWeight(e, Constants.CALL_EDGE);
                connected = true;
            }
        }
        return connected;
    }

    private boolean addCallEdgeToPrev(ArrayList<StatementInstance> unitsInThread, int i,
            Set<DefaultWeightedEdge> removed, boolean connected, Unit possibleCaller) {
        if (calledMethodInAppClasses(possibleCaller) && methodIsInAndroidLibs(unitsInThread, i)) {
            DefaultWeightedEdge e = graph.getEdge(unitsInThread.get(i-1).getLineNo(), unitsInThread.get(i).getLineNo());
            if (e == null){
                removePrevEdges(unitsInThread, i, removed);
                e = graph.addEdge(unitsInThread.get(i).getLineNo()-1, unitsInThread.get(i).getLineNo());
                graph.setEdgeWeight(e, Constants.CALL_EDGE);
                connected = true;
            }
        }
        return connected;
    }

    private void removePrevEdges(ArrayList<StatementInstance> unitsInThread, int i,
            Set<DefaultWeightedEdge> removed) {
        for (Integer pred: Graphs.predecessorListOf(graph, unitsInThread.get(i).getLineNo())){
            DefaultWeightedEdge p = graph.getEdge(pred, unitsInThread.get(i).getLineNo());
            if (p != null){
                removed.add(p);
            }
        }
    }

    private void connectToCaller(ArrayList<StatementInstance> unitsInThread, int i) {
        StatementInstance iIu = unitsInThread.get(i);
        SootMethod iIuMethod = iIu.getMethod();
        int iIuLineNo = iIu.getLineNo();
        Set<DefaultWeightedEdge> removed = new HashSet<>();
        int stop = i-Constants.SEARCH_LENGTH;
        if (stop < 0) {
            stop = 0;
        }

        for (int j = i-1; j >= stop; j--) {
            StatementInstance jIu = unitsInThread.get(j);
            if (jIu == null) {
                return;
            }

            int jIuLineNo = jIu.getLineNo();
            SootMethod jIuMethod = jIu.getMethod();

            boolean flowEdgeAdded = addFlowEdgeWithinMethod(iIuMethod, iIuLineNo, removed, jIuLineNo, jIuMethod);
            if (flowEdgeAdded) {
                break;
            }

            
            boolean callEdgeAdded = addCallEdgeBetweenMethods(iIuMethod, iIuLineNo, removed, jIuLineNo, jIuMethod, jIu);
            if (callEdgeAdded) {
                break;
            }
        }
        graph.removeAllEdges(removed);
    }

    private boolean addCallEdgeBetweenMethods(SootMethod iIuMethod, int iIuLineNo, Set<DefaultWeightedEdge> removed,
            int jIuLineNo, SootMethod jIuMethod, StatementInstance jIu) {
        boolean callEdgeAdded = false;
        SootMethod calledMethod = jIu.getCalledMethod();
        if (calledMethod != null && classInAndroidLibs(jIuMethod)) {
            if (calledMethod.getSubSignature().equals(iIuMethod.getSubSignature())) {
                callEdgeAdded = addCallEdgeToRegularCaller(iIuLineNo, removed, jIuLineNo, callEdgeAdded, iIuMethod);
            } else if (iIuMethod.getName().equals("<clinit>") && classNamesMatch(iIuMethod, calledMethod)) {
                callEdgeAdded = addCallEdgeToClassConstructor(iIuLineNo, removed, jIuLineNo, callEdgeAdded);
            }
        }
        return callEdgeAdded;
    }

    private boolean addCallEdgeToClassConstructor(int iIuLineNo, Set<DefaultWeightedEdge> removed, int jIuLineNo,
            boolean callEdgeAdded) {
        DefaultWeightedEdge e = graph.getEdge(jIuLineNo, iIuLineNo);
        if (e == null){
            removeNonCallerEdge(iIuLineNo, removed);
            e = graph.addEdge(jIuLineNo, iIuLineNo);
            graph.setEdgeWeight(e, Constants.CALL_EDGE);
            callEdgeAdded = true;
        }
        return callEdgeAdded;
    }

    private boolean addCallEdgeToRegularCaller(int iIuLineNo, Set<DefaultWeightedEdge> removed, int jIuLineNo,
            boolean callEdgeAdded, SootMethod iIuMethod) {
        if (Scene.v().getApplicationClasses().contains(iIuMethod.getDeclaringClass())) {
            callEdgeAdded = addCallEdgeToClassConstructor(iIuLineNo, removed, jIuLineNo, callEdgeAdded);
        }
        return callEdgeAdded;
    }

    private boolean addFlowEdgeWithinMethod(SootMethod iIuMethod, int iIuLineNo, Set<DefaultWeightedEdge> removed,
            int jIuLineNo, SootMethod jIuMethod) {
        boolean flowEdgeAdded = false;
        if (jIuMethod.equals(iIuMethod)) {
            DefaultWeightedEdge e = graph.getEdge(jIuLineNo, iIuLineNo);
            if (e == null){
                removeNonCallerEdge(iIuLineNo, removed);
                e = graph.addEdge(jIuLineNo, iIuLineNo);
                graph.setEdgeWeight(e, Constants.FLOW_EDGE);
                flowEdgeAdded = true;
            } else {
                flowEdgeAdded = true;
            }
        }
        return flowEdgeAdded;
    }

    private boolean classNamesMatch(SootMethod iIuMethod, SootMethod calledMethod) {
        return calledMethod.getDeclaringClass().getName().equals(iIuMethod.getDeclaringClass().getName());
    }

    private boolean classInAndroidLibs(SootMethod jIuMethod) {
        return !jIuMethod.getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS);
    }

    private void removeNonCallerEdge(int iIuLineNo, Set<DefaultWeightedEdge> removed) {
        for (Integer pred: Graphs.predecessorListOf(graph, iIuLineNo)){
            DefaultWeightedEdge p = graph.getEdge(pred, iIuLineNo);
            if (p != null){
                removed.add(p);
            }
        }
    }

    boolean isReturn(Unit u){
        return (u instanceof ReturnStmt) || (u instanceof ReturnVoidStmt);
    }

    public SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> getGraph() {
        return graph;
    }

    public StatementInstance mapNoUnits(Integer num){
        String key = mapNoKey.get(num);
        if (key != null) {
            return mapKeyUnits.get(key);
        }
        return null;
    }

    public void setLastLine(long lastLine) {
        this.lastLine = lastLine;
    }

    public long getLastLine() {
        return lastLine;
    }

    public List<StatementInstance> mapNoUnits(List<Integer> num){
        List<StatementInstance> l = new ArrayList<>();
        for (Integer n: num) {
            l.add(mapNoUnits(n));
        }
        return l;
    }

    public void addToPossibleCalbacks(Integer location, StatementInstance iu){
        this.possibleCallbacks.put(location, iu);
    }

    public void putStaticFieldUnitEdge(Integer v1, Integer v2) {
        this.nextUnitWithStaticField.put(v1, v2);
        this.previousUnitWithStaticField.put(v2, v1);
    }

    public Integer getNextUnitWithStaticField(Integer source) {
        return nextUnitWithStaticField.get(source);
    }

    public Integer getPreviousUnitWithStaticField(Integer source) {
        return previousUnitWithStaticField.get(source);
    }

    public Map<String, Integer> getMapKeyNo() {
        return mapKeyNo;
    }

    public Map<String, StatementInstance> getMapKeyUnits() {
        return mapKeyUnits;
    }

    public Map<Integer, String> getMapNoKey() {
        return mapNoKey;
    }
}

