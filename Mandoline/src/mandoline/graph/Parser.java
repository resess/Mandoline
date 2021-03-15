package mandoline.graph;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;


import mandoline.utils.AnalysisLogger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Parser {

    private static final String DELEMITER = ":ZZZ:";


    private Parser() {
        throw new IllegalStateException("Utility class");
      }

    public static List <Traces> readFile(String fileName, String staticLogFile) throws IOException {
        List <String> traces = FileUtils.readLines(new File(fileName), Charset.defaultCharset());
        List<String> expandedTrace = expandTrace(traces, staticLogFile);
        List <Traces> listTraces = new ArrayList<>();
        AnalysisLogger.log(true, "Parsing file {}, trace size is {}", fileName, expandedTrace.size());
        for(String line : expandedTrace) {
            String [] tokens = line.split(DELEMITER);
            Traces tr = new Traces();
            if(tokens.length < 4) continue;
            tr._lineNo = Long.valueOf(tokens[0]);
            tr._class = "";
            tr._method = tokens[1];
            tr._type = "__inst__";
            tr._ins = tokens[2];
            tr._tid = Long.valueOf(tokens[3]);
            if(tokens.length > 4) {
                tr._field = Long.valueOf(tokens[4]);
            }
            listTraces.add(tr);
        }
        return listTraces;
    }

    public static List<String> expandTrace(List<String> trace, String staticLogFile) {
        List<String> expandedTrace = new ArrayList<>();
        JSONParser parser = new JSONParser();
        try {
            AnalysisLogger.log(true, "Abs path for static-log {}", new File(staticLogFile).getAbsolutePath());
            FileReader reader = new FileReader(staticLogFile);
            Object obj = parser.parse(reader);
            JSONObject jObj = (JSONObject) obj;
            Map<Long, List<String>> logMap = new HashMap<>();
            buildLogMap(jObj, logMap);
            String lastSlicingLine = "";
            for (String t: trace) {
                try {
                    t = t.split("SLICING:")[1];
                    if (lastSlicingLine.equals(t)) {
                        continue;
                    }
                    lastSlicingLine = t;
                } catch (ArrayIndexOutOfBoundsException e){
                    continue;
                }
                for (String s: t.split("-")) {
                    
                    Long fieldId = null;
                    Long lineNum = null;
                    Long threadNum = -1L;
                    String [] sSplit = s.split(":");
                    try {
                        lineNum = Long.valueOf(sSplit[0]);
                    } catch (java.lang.NumberFormatException e) {
                        continue;
                    }
                    boolean isField = checkIsField(sSplit);
                    String fieldLine = "";
                    if (isField) {
                        fieldId = Long.valueOf(sSplit[2]);
                        fieldLine = DELEMITER + fieldId;
                    } else {
                        threadNum = getThreadNum(threadNum, sSplit);
                    }
                    addToExpandedTrace(expandedTrace, logMap, lineNum, threadNum, fieldLine);
                }
            }
        } catch (IOException | ParseException e) {
            // Ignored
        }
        AnalysisLogger.log(true, "Done parsing");
        return expandedTrace;
    }

    private static boolean checkIsField(String[] sSplit) {
        boolean isField = false;
        if (sSplit.length > 1 && sSplit[1].equals("FIELD")) {
            isField = true;
        } else {
            isField = false;
        }
        return isField;
    }

    private static void buildLogMap(JSONObject jObj, Map<Long, List<String>> logMap) {
        for (Object o :jObj.keySet()) {
            String methodName = (String) o;
            JSONObject methodBody = (JSONObject) jObj.get(o);
            for (Object bb :methodBody.keySet()) {
                Long lineNum = Long.valueOf((String) bb);
                Object[] linesInBB = ((JSONArray) methodBody.get(bb)).toArray();
                ArrayList<String> expandedBody = new ArrayList<>();
                for (Object line: linesInBB) {
                    String payload = lineNum + DELEMITER + methodName + DELEMITER + ((String) line);
                    expandedBody.add(payload);
                }
                logMap.put(lineNum, expandedBody);
            }
        }
    }

    private static Long getThreadNum(Long threadNum, String[] sSplit) {
        try {
            threadNum = Long.valueOf(sSplit[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            // Ignored
        }
        return threadNum;
    }

    private static void addToExpandedTrace(List<String> expandedTrace, Map<Long, List<String>> logMap, Long lineNum,
            Long threadNum, String fieldLine) {
        try {
            for (String line : logMap.get(lineNum)) {
                line = line + DELEMITER + threadNum.toString() + fieldLine;
                expandedTrace.add(line);
            }
        } catch (NullPointerException e) {
            // Ignored
        }
    }

}
