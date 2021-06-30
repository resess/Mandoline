package ca.ubc.ece.resess.slicer.dynamic.core.statements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisCache;


public class StatementMap extends LinkedHashMap<String, StatementInstance> {
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, StatementInstance> entry : this.entrySet()) {
            sb.append("    ");
            sb.append(entry.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }

    public StatementMap inTraceOrder(StatementInstance startNode) {
        StatementMap newChunk = AnalysisCache.getFromInTraceOrderChunkCache(this, startNode);
        if (newChunk != null) {
            return newChunk;
        }
        newChunk = new StatementMap();
        ArrayList<StatementInstance> orderedTrace = new ArrayList<>();
        for (Map.Entry<String, StatementInstance> entry : this.entrySet()) {
            orderedTrace.add(entry.getValue());
        }
        Collections.sort(orderedTrace, (lhs, rhs) -> {
            if (rhs.getLineNo() < lhs.getLineNo()) {
                return 1;
            } else if (rhs.getLineNo() > lhs.getLineNo()) {
                return -1;
            }
            return 0;
        });

        // for (InstructionUnits iu : orderedTrace) {
        //     if (iu.equals(startNode)) {
        //         continue;
        //     }
        //     newChunk.put(iu.toString(), iu);
        // }
        if (startNode!=null) {
            newChunk.remove(startNode.toString());
        }
        AnalysisCache.putInInTraceOrderChunkCache(this, startNode, newChunk);
        return newChunk;
    }

    public StatementMap reverseTraceOrder(StatementInstance startNode) {
        StatementMap newChunk = new StatementMap();
        ArrayList<StatementInstance> orderedTrace = new ArrayList<>();
        for (Map.Entry<String, StatementInstance> entry : this.entrySet()) {
            orderedTrace.add(entry.getValue());
        }
        Collections.sort(orderedTrace, (lhs, rhs) -> {
            if (rhs.getLineNo() > lhs.getLineNo()) {
                return 1;
            } else if (rhs.getLineNo() < lhs.getLineNo()) {
                return -1;
            }
            return 0;
        });

        for (StatementInstance iu: orderedTrace){
            if (iu.equals(startNode)) {
                continue;
            }
            newChunk.put(iu.toString(), iu);
        }
        return newChunk;
    }
}