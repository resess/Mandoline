package ca.ubc.ece.resess.slicer.dynamic.core.controldependence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import soot.toolkits.graph.pdg.EnhancedUnitGraph;
import soot.toolkits.graph.pdg.HashMutablePDG;
import soot.toolkits.graph.pdg.PDGNode;
import soot.toolkits.graph.pdg.PDGRegion;
import soot.SootMethod;
import soot.jimple.GotoStmt;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public class ControlDominator{

    static Set<SootMethod> outOfMemMethods = new LinkedHashSet<>();
    static Map<SootMethod, EnhancedUnitGraph> computedGraphs = new LinkedHashMap<>();
    private ControlDominator() {
        throw new IllegalStateException("Utility class");
    }

    static int getIntFromString(String s) {
        Matcher matcher = Pattern.compile("\\d+").matcher(s);
        matcher.find();
        return Integer.valueOf(matcher.group());
    }

    public static StatementInstance getControlDominator(StatementInstance stmt, StatementMap chunk, DynamicControlFlowGraph icdg){
        StatementInstance candidateIu = null;
        if (outOfMemMethods.contains(stmt.getMethod())) {
            return candidateIu;
        }
        ControlDomRunner cdr = new ControlDomRunner(stmt, chunk, icdg, candidateIu);
        Thread t = new Thread(cdr);
        try {
            t.start();
            t.join(30*1000);
            t.interrupt();
        } catch (InterruptedException e) {
            // pass
        }
        candidateIu = cdr.getCandidateIu();
        // candidateIu = getControlDom(stmt, chunk, icdg, candidateIu);
        return candidateIu;
    }


    static class ControlDomRunner implements Runnable {
        StatementInstance stmt; StatementMap chunk; DynamicControlFlowGraph icdg; StatementInstance candidateIu;
        ControlDomRunner (StatementInstance stmt, StatementMap chunk, DynamicControlFlowGraph icdg, StatementInstance candidateIu){
            this.stmt = stmt;
            this.chunk = chunk;
            this.icdg = icdg;
            this.candidateIu = candidateIu;
        }
        @Override
        public void run() {
            this.candidateIu = getControlDom(stmt, chunk, icdg, candidateIu);
        }

        public StatementInstance getCandidateIu() {
            return candidateIu;
        }
    }


    private static StatementInstance getControlDom(StatementInstance stmt, StatementMap chunk, DynamicControlFlowGraph icdg, StatementInstance candidateIu) {
        try {
            EnhancedUnitGraph cug;
            if (computedGraphs.containsKey(stmt.getMethod())) {
                cug = computedGraphs.get(stmt.getMethod());
            } else {
                // AnalysisLogger.log(true, "New method: {}", stmt.getMethod());
                cug = new EnhancedUnitGraph(stmt.getMethod().getActiveBody());
                computedGraphs.put(stmt.getMethod(), cug);
            }
            
            HashMutablePDG pdg = new HashMutablePDG(cug);
            for(PDGRegion r: pdg.getPDGRegions()) {
                PDGNode p = r.getCorrespondingPDGNode();
                if (r.getUnits().contains(stmt.getUnit())) {
                    // AnalysisLogger.log(true, "Units here are: {}", r.getUnits());
                    if (r.getUnits().toString().contains(":= @caughtexception") || r.getUnits().toString().contains("goto [?= throw")) {
                        candidateIu = previousTraceLine(icdg, stmt.getLineNo());
                    } else {
                        candidateIu = matchControlDom(stmt, chunk, pdg, candidateIu, p);
                    }
                }
            }
        } catch (OutOfMemoryError e1) {
            AnalysisLogger.warn(true, "Could not compute control dom");
            outOfMemMethods.add(stmt.getMethod());
        } catch (Exception e2) {
            AnalysisLogger.warn(true, "Could not compute control dom");
        }
        return candidateIu;
    }


    private static StatementInstance matchControlDom(StatementInstance stmt, StatementMap chunk,
            HashMutablePDG pdg, StatementInstance candidateIu, PDGNode p) {
        if (!pdg.getPredsOf(p).isEmpty()) {
            String [] lines = ((PDGNode) pdg.getPredsOf(p).get(0)).toString().split("\\r?\\n");
            String conditionLine = lines[lines.length-1];
            if (conditionLine.endsWith(";")) {
                conditionLine = conditionLine.substring(0, conditionLine.length()-1);
            }
            candidateIu = compareUnits(stmt, chunk, candidateIu, conditionLine);
        }
        return candidateIu;
    }


    private static StatementInstance compareUnits(StatementInstance stmt, StatementMap chunk, StatementInstance candidateIu, String candidate) {
        for (StatementInstance iu: chunk.values()) {
            if (iu.getLineNo() > stmt.getLineNo()) {
                continue;
            }
            if (iu.getUnit().toString().equals(candidate)) {
                candidateIu = iu;
                break;
            }
        }
        return candidateIu;
    }


    private static StatementInstance previousTraceLine(DynamicControlFlowGraph icdg, int pos) {
        StatementInstance prev = null;
        int newPos = pos;
        while (newPos > 0) {
            newPos--;
            prev = icdg.mapNoUnits(newPos);
            if ((prev!=null) && !((prev.getUnit()) instanceof GotoStmt)) {
                return prev;
            }
        }
        return prev;
    }
}