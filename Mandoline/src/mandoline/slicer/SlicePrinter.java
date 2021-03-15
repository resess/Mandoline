package mandoline.slicer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import soot.Unit;
import mandoline.accesspath.AccessPath;
import mandoline.statements.StatementInstance;
import mandoline.utils.AnalysisLogger;
import soot.toolkits.scalar.Pair;

public class SlicePrinter {

    private SlicePrinter() {
        throw new IllegalStateException("Utility class");
    }

    public static void printToCSV (String fileName, DynamicSlice dynamicSlice) {
        ArrayList<String> printList = new ArrayList<>();
        Set<String> lines = new LinkedHashSet<>();
        printList.add("ID, Method, Class, Line, Source, Var");
        for (Map.Entry<Pair<StatementInstance, AccessPath>,Pair<StatementInstance, AccessPath>> elem : dynamicSlice.entrySet()) {
            StatementInstance iu = elem.getKey().getO1();
            int id = dynamicSlice.getOrder(elem.getKey());
            String method = iu.getMethod().getSubSignature().replace(',', ';');
            String clString = iu.getMethod().getDeclaringClass().getName().replace(',', ';');
            String sourceLineNo = getSourceLineNumber(iu);
            String line = iu.getUnit().toString().replace(',', ';');
            line = method + ", " + clString + ", " + line;
            if (!lines.contains(line)) {
                lines.add(line);
                Pair<StatementInstance, AccessPath> source = dynamicSlice.get(elem.getKey());
                int sourceId = dynamicSlice.getOrder(source);
                String toPrint = String.valueOf(id) + ", " + line + ", " + sourceLineNo + ", " + sourceId + ", " + source.getO2().toString();
                printList.add(toPrint);
            }
        }
        try {
            AnalysisLogger.log(true, "Number of lines: {}", printList.size()-1);
            FileUtils.writeLines(new File(fileName), printList);
        } catch (IOException e) {
            AnalysisLogger.warn(true, "Exception when writing csv: {}", e);
        }
        
    }

    private static String getSourceLineNumber(StatementInstance iu) {
        int lineNo = iu.getUnit().getJavaSourceStartLineNumber();
        if (lineNo == -1) {
            int counter = 0;
            for (Unit u: iu.getMethod().getActiveBody().getUnits()) {
                counter++;
                if (u.equals(iu.getUnit())) {
                    lineNo = counter;
                }
            }
        }
        return String.valueOf(lineNo);
    }
}
