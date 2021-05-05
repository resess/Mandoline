package mandoline.controldependence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.Graphs;

import org.jgrapht.graph.DefaultWeightedEdge;

import mandoline.graph.ICDG;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementMap;
import mandoline.utils.AnalysisLogger;
import mandoline.utils.Constants;
import soot.toolkits.graph.pdg.EnhancedUnitGraph;
import soot.toolkits.graph.pdg.HashMutablePDG;
import soot.toolkits.graph.pdg.PDGNode;
import soot.toolkits.graph.pdg.PDGRegion;
import soot.SootMethod;
import soot.jimple.GotoStmt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class ControlDominator{

    static Set<SootMethod> outOfMemMethods = new LinkedHashSet<>();
    private ControlDominator() {
        throw new IllegalStateException("Utility class");
    }

    static int getIntFromString(String s) {
        Matcher matcher = Pattern.compile("\\d+").matcher(s);
        matcher.find();
        return Integer.valueOf(matcher.group());
    }

    public static StatementInstance getControlDominator(StatementInstance stmt, StatementMap chunk, ICDG icdg){
        StatementInstance candidateIu = null;
        if (outOfMemMethods.contains(stmt.getMethod())) {
            return candidateIu;
        }
        try {
            EnhancedUnitGraph cug = new EnhancedUnitGraph(stmt.getMethod().getActiveBody());
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


    private static StatementInstance previousTraceLine(ICDG icdg, int pos) {
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