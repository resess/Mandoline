package mandoline.slicer;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.io.FileUtils;
import org.jgrapht.Graphs;

import mandoline.accesspath.AccessPath;
import mandoline.exceptions.InvalidCommandsException;
import mandoline.framework.FrameworkModel;
import mandoline.graph.ICDG;
import mandoline.graph.Parser;
import mandoline.graph.Traces;
import mandoline.instrumenter.EfficientInstrumenter;
import mandoline.instrumenter.JimpleWriter;
import mandoline.sootcallgraphs.ThreadCalls;
import mandoline.statements.StatementInstance;
import mandoline.utils.AnalysisCache;
import mandoline.utils.AnalysisLogger;
import mandoline.utils.CommandParser;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.infoflow.android.SetupApplication;
import soot.options.Options;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.jimple.infoflow.android.callbacks.AndroidCallbackDefinition;
import soot.toolkits.scalar.Pair;
import soot.jimple.toolkits.callgraph.VirtualCallSite;

public class Slicer {
    static final String SOOT_OUTPUT_STRING = "sootOutput/";
    MultiMap<SootClass, AndroidCallbackDefinition> callbackMethods = new HashMultiMap<>();
    Map<Pair<SootMethod, Unit>, String> threadCallers = new HashMap<>();
    Map<Pair<SootMethod, Unit>, SootClass> setterCallbackMap = new HashMap<>();
    Map <String, List<StatementInstance>> mapMethodInst = new LinkedHashMap<>();
    Map <String, StatementInstance> mapUnits = new LinkedHashMap<>();
    Map <String, StatementInstance> mapInvokations = new LinkedHashMap<>();
    Set<String> dynamicPrint = new LinkedHashSet<>();
    static Slicer instance;

    public static Slicer getInstance() {
        return instance;
    }

    public Set<String> getDynamicPrint() {
        return dynamicPrint;
    }

    void printList(List <String> list, String outFile) {
        try {
            AnalysisLogger.log(true, "Printing to {}", outFile);
        FileUtils.writeLines(new File(outFile), list);
        } catch (IOException e) {
            AnalysisLogger.error("Unable to print file {}, {}", outFile, e);
        }
    }

    static void throwParseExceptionIfNull(Object object, String message){
        if (object == null) {
            throwParseException(message);
        }
    }

    public static void main(String [] args) {
            long startTime = System.nanoTime();
            boolean justTrace = false;
            Map<String, String> commands = CommandParser.parse(args);
            
            String mode = commands.get("m");
            if (mode == null || !(mode.equals("i") || mode.equals("g") || mode.equals("m") || mode.equals("md")|| mode.equals("as"))) {
                throwParseException("Mode not provided / invalid mode");
            }

            String pathApk = commands.get("a");
            throwParseExceptionIfNull(pathApk, "Apk path not provided");

            String platformPath = commands.get("p");
            throwParseExceptionIfNull(platformPath, "Platforms path not provided");

            String callbackFile = commands.get("c");
            throwParseExceptionIfNull(callbackFile, "Callback file path not provided");

            String outDir = commands.get("o");
            throwParseExceptionIfNull(outDir, "Output directory path not provided");

            File outDirFile = new File(outDir);
            outDirFile.mkdirs();
            String staticLogFile = commands.get("sl");
            throwParseExceptionIfNull(staticLogFile, "Static log file path not provided");


            boolean instrumented = instrument(startTime, commands, mode, pathApk, platformPath, callbackFile, outDir,
                    staticLogFile);

            if (instrumented) {
                return;
            }

            String fileToParse = commands.get("t");
            throwParseExceptionIfNull(fileToParse, "Trace file path not provided");

            String outFile = fileToParse+"_icdg.log";

            if(mode.equals("g")) {
                justTrace = true;
            } else {
                String sp = commands.get("sp");
                throwParseExceptionIfNull(sp, "No slicing criteria provided");
                String sv = commands.get("sv");
                throwParseExceptionIfNull(sv, "No variables to slice from are provided");
                String sd = commands.get("sd");
                throwParseExceptionIfNull(sd, "StubDroid path not provided");
                String tw = commands.get("tw");
                throwParseExceptionIfNull(tw, "Taint-wrapper path not provided");
            }
            List <Traces> trs = null;
            try {
                trs = Parser.readFile(fileToParse, staticLogFile);
            } catch (IOException e) {
                AnalysisLogger.error("Unable to read trace file {}", e);
            }
            Slicer slicer = new Slicer(pathApk, platformPath, callbackFile);
            Slicer.instance = slicer;
            ICDG icdg = new ICDG(slicer.setterCallbackMap, slicer.callbackMethods, slicer.threadCallers);
            icdg.createDCFG(trs);

            printGraph(outFile, slicer, icdg);

            if(justTrace) {
                terminate(outDir, mode, startTime);
                return;
            }

            int posToSlice = Integer.parseInt(commands.get("sp"));
            ArrayList<String> variables = new ArrayList<>();
            String variableString = commands.get("sv");
            String stubDroidPath = commands.get("sd");
            String taintWrapperPath = commands.get("tw");

            String[] split = variableString.split("-");
            for (int i = 0; i < split.length; i++) {
                variables.add("$"+split[i]);
            }

            boolean staticAnalysis = true;
            boolean dynamicAnalysis = false;
            boolean frameworkModel = true;

            if (mode.equals("m")) {
                staticAnalysis = true;
                dynamicAnalysis = false;
                frameworkModel = true;
                AnalysisLogger.log(true, "Analysis type set to Mandoline static");
            } else if (mode.equals("md")){
                staticAnalysis = false;
                dynamicAnalysis = true;
                frameworkModel = true;
                AnalysisLogger.log(true, "Analysis type set to Mandoline dynamic");
            } else if (mode.equals("mf")){
                staticAnalysis = false;
                dynamicAnalysis = false;
                frameworkModel = true;
                AnalysisLogger.log(true, "Analysis type set to Mandoline framework");
            } else if (mode.equals("as")){
                staticAnalysis = false;
                dynamicAnalysis = false;
                frameworkModel = false;
                AnalysisLogger.log(true, "Analysis type set to AndroidSlicer");
            } else {
                throwParseException("Invalid mode " + mode);
            }
            FrameworkModel.setStubDroidPath(stubDroidPath);
            FrameworkModel.setTaintWrapperFile(taintWrapperPath);
            
            AnalysisLogger.log(true, "Slicing criterion: (" + posToSlice + ", " + variables + ")");

            AnalysisLogger.log(true, "size of the trace after loading:"+icdg.getMapKeyNo().keySet().size());
            AnalysisLogger.log(true, "Slicing from statement:"+ icdg.getMapNoKey().get(posToSlice));

            
            StatementInstance stmt = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(posToSlice));
            Set<AccessPath> accessPaths = new HashSet<>();
            for (String v: variables) {
                accessPaths.add(new AccessPath(v, new Type(){
                    private static final long serialVersionUID = 1L;
                    @Override
                    public String toString() {
                        return "SlicingCriterionType";
                    }
                }, posToSlice, AccessPath.NOT_DEFINED, stmt));
            }
            SliceMethod sliceMethod = new SliceMethod(icdg, staticAnalysis, dynamicAnalysis, frameworkModel);
            DynamicSlice dynamicSlice = sliceMethod.slice(stmt, accessPaths);
            slicer.dynamicPrint = new LinkedHashSet<>();
            printSlices(slicer, dynamicSlice);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
            LocalDateTime now = LocalDateTime.now();
            String resultFileName = outDir + File.separator + "result_" +mode+"_"+ dtf.format(now) + ".csv";
            SlicePrinter.printToCSV(resultFileName, dynamicSlice);

        terminate(outDir, mode, startTime);
    }

    private static void printSlices(Slicer slicer, DynamicSlice dynamicSlice) {
        List<String> staticPrint = new ArrayList<>();
        Set<String> staticSlice = new LinkedHashSet<>();
        
        for(Map.Entry<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry: dynamicSlice.entrySet()) {
            Pair<StatementInstance, AccessPath> iup = entry.getKey();
            StatementInstance iu = iup.getO1();
            slicer.dynamicPrint.add(iu.toString());
            String toPrint = iu.getMethod().getSignature() + ":" + iu.getUnit().getJavaSourceStartLineNumber() + "-" + iu.getUnit().getJavaSourceStartColumnNumber() + ":" + iu.getUnit().toString(); 
            if (!staticSlice.contains(toPrint)) {
                staticSlice.add(toPrint);
                staticPrint.add(toPrint);
                staticPrint.add("   from:" + entry.getValue());
            }
        }
        AnalysisLogger.log(true, "Printing dynamic dependence:");
        for (String s: slicer.dynamicPrint) {
            AnalysisLogger.log(true, "{}", s);
        }
        AnalysisLogger.log(true, "Printing static dependence:");
        for (String s: staticPrint) {
            AnalysisLogger.log(true, "{}", s);
        }
    }

    private static void printGraph(String outFile, Slicer slicer, ICDG icdg) {
        AnalysisLogger.log(true, "Printing graph...");
        List <String> listTOPrint = new ArrayList<>();
        Iterator<Entry<String, Integer>> entries = icdg.getMapKeyNo().entrySet().iterator();
        while (entries.hasNext()) {
            Entry<String, Integer> thisEntry = entries.next();
            String key = thisEntry.getKey();
            listTOPrint.add(key + ":PRED:"+Graphs.predecessorListOf(icdg.getGraph(), thisEntry.getValue())+ ":SUCC:"+Graphs.successorListOf(icdg.getGraph(), thisEntry.getValue()) + ":TID:"+icdg.getMapKeyUnits().get(key).getThreadID());
        }
        slicer.printList(listTOPrint, outFile);
        AnalysisLogger.log(true, "Printing Complete.");
    }

    private static boolean instrument(long startTime, Map<String, String> commands, String mode, String pathApk,
            String platformPath, String callbackFile, String outDir, String staticLogFile) {
        boolean shouldTerminate = false;
        if(mode.equals("i")) {
            if (new File(SOOT_OUTPUT_STRING).isDirectory()) {
                deleteFolder(new File(SOOT_OUTPUT_STRING));
            }

            String instrumenterMode = commands.get("im");
            throwParseExceptionIfNull(instrumenterMode, "instrumenter mode not provided");
            
            String packageName = commands.get("pk");
            throwParseExceptionIfNull(packageName, "package name not provided");

            String mandolineLoggerJar = commands.get("ml");
            if (mandolineLoggerJar == null) {
                throwParseExceptionIfNull(mandolineLoggerJar, "mandoline logger jar path not provided");
            }
            String instrumentOptions = parseInstrumentationMode(instrumenterMode);

            Slicer slicer = new Slicer(pathApk, platformPath, callbackFile);
            String[] instrumenterArgs = {instrumentOptions, staticLogFile, packageName, "-w", "-allow-phantom-refs", "-process-multiple-dex", "-android-jars", platformPath, "-src-prec", "apk", "-output-format", "dex", "-process-dir", pathApk, "-process-dir", mandolineLoggerJar};
            if (instrumentOptions.contains("jimple")) {
                JimpleWriter jimpleWriter = new JimpleWriter(outDir);
                jimpleWriter.start(instrumenterArgs);
                terminate(outDir, instrumenterMode, startTime);
                shouldTerminate = true;
            } else {
                EfficientInstrumenter instrumenter = new EfficientInstrumenter(slicer.callbackMethods, slicer.threadCallers);
                instrumenter.start(instrumenterArgs);
                terminate(outDir, instrumenterMode, startTime);
                shouldTerminate = true;
            }
        }
        return shouldTerminate;
    }

    private static String parseInstrumentationMode(String instrumenterMode) {
        String instrumentOptions = "";
        if (instrumenterMode.startsWith("md")) {
            instrumentOptions = "thread_time_field";
        } else if (instrumenterMode.startsWith("m")) {
            instrumentOptions = "thread_time";
        } else {
            throwParseException("Invalid instrumenter mode");
        }
        if (instrumenterMode.endsWith("j")) {
            instrumentOptions += "_jimple";
        }
        return instrumentOptions;
    }

    private static void throwParseException(String message) {
        AnalysisLogger.log(true, "Invalid command line options: {}", message);
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(CommandParser.CMD_LINE_SYNTAX, CommandParser.getOptions());
        throw new InvalidCommandsException();
    }
    
    private static void terminate(String outDir, String mode, long startTime){
        try {
            File folder = new File(SOOT_OUTPUT_STRING);
            File[] listOfFiles = folder.listFiles();
            Map<File, File> filesToMove = new HashMap<>();
            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().contains(".apk")) {
                    filesToMove.put(file, new File(outDir + File.separator + file.getName().replace(".apk", "_" +mode + ".apk")));
                }
            }
            filesToMove.put(new File("apk-size.txt"), new File(outDir + File.separator + "apk-size.txt"));
            for (Map.Entry<File, File> entry: filesToMove.entrySet()) {
                if (entry.getKey().renameTo(entry.getValue())) {
                    // Ignore
                }
            }
            if (folder.isDirectory()) {
                deleteFolder(folder);
            }
        } catch (Exception e) {
            // Ignore
        }
        
        long endTime = System.nanoTime();
        double totalTime = (endTime-startTime)/1000000000.0;
        AnalysisLogger.log(true, "Time: {}", totalTime);
    }

    private static void deleteFolder(File folder) {
        try {
            FileUtils.forceDelete(folder);
        } catch (IOException e) {
            // pass
        }
    }

    public Slicer(String apkPath, String platFormDir, String callbackFile) {
        AnalysisCache.reset();
        InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
        config.getAnalysisFileConfig().setTargetAPKFile(apkPath);
        config.getAnalysisFileConfig().setAndroidPlatformDir(platFormDir);
        config.setMergeDexFiles(true);
        config.setExcludeSootLibraryClasses(false);
        config.getCallbackConfig().setCallbackAnalysisTimeout(60);
        SetupApplication app = new SetupApplication(config);
        app.setCallbackFile(callbackFile);
       
        try {
            app.constructCallgraph();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SootMethod entryPoint = app.getDummyMainMethod();
        Options.v().set_main_class(entryPoint.getSignature());
        Options.v().set_keep_line_number(true);
        app.printEntrypoints();
        this.callbackMethods = app.getCallbackMethods();
        this.setterCallbackMap = app.getCallbackAnalyzer().getSetterCallbackMap();
        ThreadCalls threadCalls = new ThreadCalls();
        threadCalls.process();
        Iterator<SootMethod> it =threadCalls.getMethodToReceivers().keyIterator();
        while(it.hasNext()) {
            SootMethod sm = it.next();
            for (Local l: threadCalls.getMethodToReceivers().get(sm)) {
                for (VirtualCallSite vc: threadCalls.getReceiverToSites().get(l)) {
                    threadCallers.put(new Pair<>(vc.getContainer(), vc.getStmt()), vc.subSig().getString());
                }
            }
        }
        AnalysisLogger.log(true, "Thread connections: {}", threadCallers);
        AnalysisLogger.log(true, "FlowDroid DummyMain: {}", entryPoint.getActiveBody());
    } 
}