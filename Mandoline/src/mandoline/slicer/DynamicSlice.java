package mandoline.slicer;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import mandoline.accesspath.AccessPath;
import mandoline.graph.ICDG;
import mandoline.statements.StatementInstance;
import mandoline.utils.AnalysisLogger;
import soot.jimple.ReturnVoidStmt;
import soot.toolkits.scalar.Pair;

public class DynamicSlice
        extends ArrayList<Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>> {

    private static final long serialVersionUID = 1L;

    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> chopGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    private Map<Integer, Integer> methodOfStatement = new LinkedHashMap<>();
    private Set<Triple<Integer, Integer, String>> edgeTypes = new LinkedHashSet<>();
    class OrderedSlice extends ArrayList<Pair<StatementInstance, AccessPath>> {

        private static final long serialVersionUID = 7286928364073066546L;

    }

    OrderedSlice order = new OrderedSlice();


    public DynamicSlice(){
        super();
        // String s = "";
        // for (StackTraceElement ste: Thread.currentThread().getStackTrace())  {
        //     s += ste.toString();
        // }
        // AnalysisLogger.log(true, "Called at {}", s);
    }

    public boolean hasEdge(Integer src, Integer dst, String edgeType) {
        // AnalysisLogger.log(true, "Checking edge: {}-{}-{}", src, dst, edgeType);
        if (edgeTypes.contains(Triple.of(src, dst, edgeType))) {
            // AnalysisLogger.log(true, "Found it!");
            return true;
        }
        return false;
    }

    public String getEdges(Integer src, Integer dst) {
        String edges = "";
        if (edgeTypes.contains(Triple.of(src, dst, "data"))) {
            edges += "data";
        }
        if (edgeTypes.contains(Triple.of(src, dst, "control"))) {
            edges += "control";
        }
        return edges;
    }

    public boolean add(Pair<StatementInstance, AccessPath> key,
            Pair<StatementInstance, AccessPath> value, String edgeType) {
                edgeTypes.add(Triple.of(key.getO1().getLineNo(), value.getO1().getLineNo(), edgeType));
                // AnalysisLogger.log(true, "Added edge: {}-{}-{}", key.getO1().getLineNo(), value.getO1().getLineNo(), edgeType);
                return this.add(key, value);
    }

    @Override
    public boolean add(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> val) {
        throw new IllegalStateException("Should not call this method");
    }
    public boolean add(Pair<StatementInstance, AccessPath> key,
            Pair<StatementInstance, AccessPath> value) {
        

        // AnalysisLogger.log(true, "Graph: {} of Slice {}", System.identityHashCode(chopGraph), System.identityHashCode(this));
        // for (Integer v: chopGraph.vertexSet()) {
        //     List<Integer> nodes = Graphs.successorListOf(chopGraph, v);
        //     for (Integer node: nodes) {
        //         AnalysisLogger.log(true, "{} --> {}", v, node);
        //     }
        // }
        

        chopGraph.addVertex(key.getO1().getLineNo());
        // chopGraph.addVertex(value.getO1().getLineNo());
        
        if (key.getO1().getLineNo() != value.getO1().getLineNo()) {
            // AnalysisLogger.log(true, "Added edge {} --> {}", key.getO1().getLineNo(), value.getO1().getLineNo());
            chopGraph.addEdge(key.getO1().getLineNo(), value.getO1().getLineNo());
        }
        
        if (!(key.getO1().getUnit() instanceof ReturnVoidStmt)) {
            order.add(key);
            return super.add(new Pair<>(key, value));
        }
        
        return false;
    }



    public void addMethod(Integer pos, Integer method){
        methodOfStatement.put(pos, method);
        // AnalysisLogger.log(true, "Added Method {} --> {}", pos, method);
    }

    public Integer getOrder(Pair<StatementInstance, AccessPath> key) {
        return order.indexOf(key);
    }

    public DynamicSlice reorder() {
        DynamicSlice ordered = new DynamicSlice();
        ordered.chopGraph = chopGraph;
        ordered.methodOfStatement = methodOfStatement;
        ordered.edgeTypes = edgeTypes;
        List<Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>> orderedList = new ArrayList<>();
        orderedList.addAll(this);
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getO1().getLineNo() > lhs.getO1().getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getO1().getLineNo() <= lhs.getO1().getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> iup: orderedList) {
            // if (!ordered.containsKey(iup)) {
                ordered.add(iup.getO1(), iup.getO2());
            // }
        }
        return ordered;
    }


    public DynamicSlice traceOrder() {
        DynamicSlice ordered = new DynamicSlice();
        ordered.chopGraph = chopGraph;
        ordered.methodOfStatement = methodOfStatement;
        ordered.edgeTypes = edgeTypes;
        List<Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>> orderedList = new ArrayList<>();
        orderedList.addAll(this);
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getO1().getLineNo() < lhs.getO1().getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getO1().getLineNo() > lhs.getO1().getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> iup: orderedList) {
            // if (!ordered.containsKey(iup)) {
                ordered.add(iup.getO1(), iup.getO2());
            // }
        }
        return ordered;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> getChopGraph() {
        return chopGraph;
    }

    public DynamicSlice chop(int forwSlicePos, ICDG icdg) {

        // AnalysisLogger.log(true, "Graph:");
        // for (Integer v: chopGraph.vertexSet()) {
        //     List<Integer> nodes = Graphs.successorListOf(chopGraph, v);
        //     for (Integer node: nodes) {
        //         // AnalysisLogger.log(true, "{} --> {}", v, node);
        //     }
        // }

        DynamicSlice chop = new DynamicSlice();
        chop.chopGraph = chopGraph;
        chop.methodOfStatement = methodOfStatement;
        chop.edgeTypes = edgeTypes;
        // AnalysisLogger.log(true, "Chopping from {}", forwSlicePos);
        if (!chopGraph.containsVertex(forwSlicePos)) {
            // AnalysisLogger.log(true, "No vertix :(");
            return chop;
        }
        
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> n: this) {
            if (n.getO1().getO1().getLineNo() == forwSlicePos) {
                addToChop(n, chop, icdg);
            }
        }
        return chop.traceOrder();
    }

    private void addToChop(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>start, DynamicSlice chop, ICDG icdg) {
        // AnalysisLogger.log(true, "Adding to chop: {}", start);
        Pair<StatementInstance, AccessPath> next = start.getO2();
        if (!chop.chopGraph.containsEdge(start.getO1().getO1().getLineNo(), next.getO1().getLineNo())) {
            chop.add(start.getO1(), next);
            addCallerToChop(start, chop);
        } else {
            return;
        }
        int pos = start.getO1().getO1().getLineNo();
        List<Integer> nodes = Graphs.successorListOf(chopGraph, pos);
        for (Integer node: nodes) {
            for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> n: this) {
                if (n.getO1().getO1().getLineNo() == node) {
                    addToChop(n, chop, icdg);
                }
            }
        }
    }

    private void addCallerToChop(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> start, DynamicSlice chop) {
        try {
            int callerPos = methodOfStatement.get(start.getO1().getO1().getLineNo());
            for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> n: this) {
                if (n.getO1().getO1().getLineNo() == callerPos) {
                    chop.add(n.getO1(), n.getO2());
                    break;
                }
            }
        } catch (NullPointerException e) {
            // pass
        }
    }
}

