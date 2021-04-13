package mandoline.slicer;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
        extends LinkedHashMap<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> {

    private static final long serialVersionUID = 1L;

    
    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> chopGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    private Map<Integer, Integer> methodOfStatement = new LinkedHashMap<>(); 

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

    @Override
    public Pair<StatementInstance, AccessPath> put(Pair<StatementInstance, AccessPath> key,
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
            return super.put(key, value);
        }
        
        return null;
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
        List<Pair<StatementInstance, AccessPath>> orderedList = new ArrayList<>();
        orderedList.addAll(this.keySet());
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getLineNo() > lhs.getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getLineNo() < lhs.getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<StatementInstance, AccessPath> iup: orderedList) {
            if (!ordered.containsKey(iup)) {
                ordered.put(iup, this.get(iup));
            }
        }
        return ordered;
    }

    public DynamicSlice traceOrder() {
        DynamicSlice ordered = new DynamicSlice();
        ordered.chopGraph = chopGraph;
        ordered.methodOfStatement = methodOfStatement;
        List<Pair<StatementInstance, AccessPath>> orderedList = new ArrayList<>();
        orderedList.addAll(this.keySet());
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getLineNo() < lhs.getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getLineNo() > lhs.getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<StatementInstance, AccessPath> iup: orderedList) {
            if (!ordered.containsKey(iup)) {
                ordered.put(iup, this.get(iup));
            }
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

    public DynamicSlice chop(int forwSlicePos, ICDG icdg) {

        AnalysisLogger.log(true, "Graph:");
        for (Integer v: chopGraph.vertexSet()) {
            List<Integer> nodes = Graphs.successorListOf(chopGraph, v);
            for (Integer node: nodes) {
                AnalysisLogger.log(true, "{} --> {}", v, node);
            }
        }

        DynamicSlice chop = new DynamicSlice();
        chop.chopGraph = chopGraph;
        chop.methodOfStatement = methodOfStatement;
        AnalysisLogger.log(true, "Chopping from {}", forwSlicePos);
        if (!chopGraph.containsVertex(forwSlicePos)) {
            AnalysisLogger.log(true, "No vertix :(");
            return chop;
        }
        
        for (Pair<StatementInstance, AccessPath> n: this.keySet()) {
            if (n.getO1().getLineNo() == forwSlicePos) {
                addToChop(n, chop, icdg);
            }
        }
        return chop.traceOrder();
    }

    private void addToChop(Pair<StatementInstance, AccessPath> start, DynamicSlice chop, ICDG icdg) {
        AnalysisLogger.log(true, "Adding to chop: {}", start);
        Pair<StatementInstance, AccessPath> next = this.get(start);
        chop.put(start, next);
        addCallerToChop(start, chop);
        int pos = start.getO1().getLineNo();
        List<Integer> nodes = Graphs.successorListOf(chopGraph, pos);
        for (Integer node: nodes) {
            for (Pair<StatementInstance, AccessPath> n: this.keySet()) {
                if (n.getO1().getLineNo() == node) {
                    addToChop(n, chop, icdg);
                }
            }
        }
    }

    private void addCallerToChop(Pair<StatementInstance, AccessPath> start, DynamicSlice chop) {
        try {
            int callerPos = methodOfStatement.get(start.getO1().getLineNo());
            for (Map.Entry<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> n: this.entrySet()) {
                if (n.getKey().getO1().getLineNo() == callerPos) {
                    chop.put(n.getKey(), n.getValue());
                    break;
                }
            }
        } catch (NullPointerException e) {
            // pass
        }
    }
}

