package mandoline.controldependence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mandoline.statements.StatementInstance;
import mandoline.statements.StatementMap;
import mandoline.utils.AnalysisLogger;
import soot.toolkits.graph.pdg.EnhancedUnitGraph;
import soot.toolkits.graph.pdg.HashMutablePDG;
import soot.toolkits.graph.pdg.PDGNode;
import soot.toolkits.graph.pdg.PDGRegion;



public class ControlDominator{

    private ControlDominator() {
        throw new IllegalStateException("Utility class");
    }

    static int getIntFromString(String s) {
        Matcher matcher = Pattern.compile("\\d+").matcher(s);
        matcher.find();
        return Integer.valueOf(matcher.group());
    }

    public static StatementInstance getControlDominator(StatementInstance stmt, StatementMap chunk){
        StatementInstance candidateIu = null;
        try {
            EnhancedUnitGraph cug = new EnhancedUnitGraph(stmt.getMethod().getActiveBody());
            HashMutablePDG pdg = new HashMutablePDG(cug);
            for(PDGRegion r: pdg.getPDGRegions()) {
                PDGNode p = r.getCorrespondingPDGNode();
                if (r.getUnits().contains(stmt.getUnit())) {
                    candidateIu = matchControlDom(stmt, chunk, pdg, candidateIu, p);
                }
            }
        } catch (Exception e) {
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
}