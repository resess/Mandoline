package mandoline.slicer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.google.common.base.Optional;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.io.FileUtils;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import mandoline.accesspath.AccessPath;
import mandoline.exceptions.InvalidCommandsException;
import mandoline.framework.FrameworkModel;
import mandoline.graph.ICDG;
import mandoline.graph.Parser;
import mandoline.graph.Traces;
import mandoline.instrumenter.AndroidInstrumenter;
import mandoline.instrumenter.Instrumenter;
import mandoline.instrumenter.JavaInstrumenter;
import mandoline.instrumenter.JimpleWriter;
import mandoline.sootcallgraphs.ThreadCalls;
import mandoline.statements.StatementInstance;
import mandoline.utils.AnalysisCache;
import mandoline.utils.AnalysisLogger;
import mandoline.utils.CommandParser;
import soot.Local;
import soot.ModulePathSourceLocator;
import soot.ModuleScene;
import soot.PackManager;
import soot.Scene;
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
    public static final String SOOT_OUTPUT_STRING = System.getProperty("user.dir") + File.separator + "sootOutput/";
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

            String callbackFile = commands.get("c");

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
            String sootClassPath = commands.get("scp");
            if (sootClassPath == null) {
                sootClassPath = ".";
            }

            Slicer slicer = new Slicer(pathApk, platformPath, callbackFile, sootClassPath);
            Slicer.instance = slicer;
            ICDG icdg = new ICDG(slicer.setterCallbackMap, slicer.callbackMethods, slicer.threadCallers);
            icdg.createDCFG(trs);

            printGraph(outFile, slicer, icdg);

            if(justTrace) {
                terminate(outDir, mode, startTime);
                return;
            }

            int posToSlice = Integer.parseInt(commands.get("sp"));
            String variableString = commands.get("sv");
            if (variableString == null) {
                variableString = "*";
            }
            String stubDroidPath = commands.get("sd");
            String taintWrapperPath = commands.get("tw");
            int forwSlicePos = -1;
            if (commands.get("fw") != null) {
                forwSlicePos = Integer.parseInt(commands.get("fw"));
            }
            
            

            boolean staticAnalysis = true;
            boolean dynamicAnalysis = false;
            boolean frameworkModel = true;
            boolean dataFlowsOnly = false;
            boolean controlFlowOnly = false;
            boolean skipFirstCtrl = false;
            boolean sliceOnce = false;
            if (commands.containsKey("data")) {
                dataFlowsOnly = true;
            }
            if (commands.containsKey("ctrl")) {
                controlFlowOnly = true;
            }
            if (commands.containsKey("skip-first-ctrl")) {
                skipFirstCtrl = true;
            }
            if (commands.containsKey("slice-once")) {
                sliceOnce = true;
            }

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

            StatementInstance stmt = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(posToSlice));
            ArrayList<String> variables = new ArrayList<>();
            if (!variableString.equals("*")) {
                String[] split = variableString.split("-");
                for (int i = 0; i < split.length; i++) {
                    variables.add("$"+split[i]);
                }
            }
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

            AnalysisLogger.log(true, "Slicing criterion: (" + posToSlice + ", " + variables + ")");
            AnalysisLogger.log(true, "size of the trace after loading:"+icdg.getMapKeyNo().keySet().size());
            AnalysisLogger.log(true, "Slicing from statement:"+ icdg.getMapNoKey().get(posToSlice));

            SliceMethod sliceMethod = new SliceMethod(icdg, staticAnalysis, dynamicAnalysis, frameworkModel, dataFlowsOnly, controlFlowOnly, skipFirstCtrl, sliceOnce);
            DynamicSlice dynamicSlice = sliceMethod.slice(stmt, accessPaths);
            if (forwSlicePos != -1) {
                dynamicSlice = dynamicSlice.chop(forwSlicePos, icdg);
            }
            slicer.dynamicPrint = new LinkedHashSet<>();
            printSlices(slicer, dynamicSlice);
            printSliceGraph(dynamicSlice);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
            LocalDateTime now = LocalDateTime.now();
            String resultFileName = outDir + File.separator + "result_" +mode+"_"+ dtf.format(now) + ".csv";
            SlicePrinter.printToCSV(resultFileName, dynamicSlice);

        terminate(outDir, mode, startTime);
    }

    private static void printSliceGraph(DynamicSlice dynamicSlice) {
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = dynamicSlice.getChopGraph();
        AnalysisLogger.log(true, "Graph:");
        for (Integer v: graph.vertexSet()) {
            List<Integer> nodes = Graphs.predecessorListOf(graph, v);
            for (Integer node: nodes) {
                AnalysisLogger.log(true, "{} --> {}", v, node);
            }
        }
        AnalysisLogger.log(true, "Graph End");
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
            

            String mandolineLoggerJar = commands.get("ml");
            if (mandolineLoggerJar == null) {
                throwParseExceptionIfNull(mandolineLoggerJar, "mandoline logger jar path not provided");
            }
            String instrumentOptions = parseInstrumentationMode(instrumenterMode);
            
            String sootClassPath = commands.get("scp");
            if (sootClassPath == null) {
                sootClassPath = ".";
            }

            
            String[] instrumenterArgs = new String[0];
            if (pathApk.endsWith(".apk")) {
                throwParseExceptionIfNull(packageName, "package name not provided");
                String[] instrumenterArgsTemp = {instrumentOptions, staticLogFile, packageName, "-w", "-allow-phantom-refs", "-process-multiple-dex", "-android-jars", platformPath, "-src-prec", "apk", "-output-format", "dex", "-process-dir", pathApk, "-process-dir", mandolineLoggerJar};
                instrumenterArgs = instrumenterArgsTemp;
            } else if (pathApk.endsWith(".jar")) {

                String[] instrumenterArgsTemp = {instrumentOptions, staticLogFile, packageName, "-cp", "VIRTUAL_FS_FOR_JDK", "-pp", "-process-dir", pathApk, "-process-dir", mandolineLoggerJar};
                instrumenterArgs = instrumenterArgsTemp;
            } else {
                throwParseException("Not and apk or jar file!");
            }
            
            if (instrumentOptions.contains("jimple")) {
                JimpleWriter jimpleWriter = new JimpleWriter(outDir);
                jimpleWriter.start(instrumenterArgs);
                terminate(outDir, instrumenterMode, startTime);
                shouldTerminate = true;
            } else {
                String instrumentationPath = commands.get("instr");
                if (pathApk.endsWith(".apk")) {
                    throwParseExceptionIfNull(platformPath, "Platforms path not provided");
                    throwParseExceptionIfNull(callbackFile, "Callback file path not provided");
                    Slicer slicer = new Slicer(pathApk, platformPath, callbackFile, sootClassPath);
                    Instrumenter instrumenter = new AndroidInstrumenter(instrumentationPath, slicer.callbackMethods, slicer.threadCallers);
                    instrumenter.start(instrumenterArgs);
                } else if (pathApk.endsWith(".jar")) {
                    String jarName = outDir + File.separator + new File(pathApk).getName().replace(".jar", "_" +mode + ".jar");
                    Instrumenter instrumenter = new JavaInstrumenter(jarName, instrumentationPath);
                    instrumenter.start(instrumentOptions, staticLogFile, pathApk, mandolineLoggerJar);
                } else {
                    throwParseException("Not and apk or jar file!");
                }
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
                } else if (file.isFile() && file.getName().contains(".jar")) {
                    filesToMove.put(file, new File(outDir + File.separator + file.getName().replace(".jar", "_" +mode + ".jar")));
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

    public Slicer(String apkPath, String platFormDir, String callbackFile, String sootClassPath) {
        AnalysisCache.reset();
        if (apkPath.endsWith(".apk")) {
            prepareProcessingApk(apkPath, platFormDir, callbackFile);
        } else if (apkPath.endsWith(".jar")) {
            prepareProcessingJAR(apkPath, sootClassPath);
        } else {
            throwParseException("Not and apk or jar file!");
        }
        
    }

    private void prepareProcessingJAR(String apkPath, String sootClassPath) {
        
        AnalysisLogger.log(true, "Processing JAR: {}", apkPath);
        soot.G.reset();
        Options.v().set_prepend_classpath(true);
        // Options.v().set_soot_classpath("VIRTUAL_FS_FOR_JDK");
        String [] excList = {"org.slf4j.impl.*"};
        List<String> excludePackagesList = Arrays.asList(excList);
        Options.v().set_exclude(excludePackagesList);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_dir(Arrays.asList(apkPath));
        Options.v().set_output_format(Options.output_format_jimple);
        Options.v().set_keep_line_number(true);
        
        // Options.v().set_whole_program(true);
        // Options.v().set_allow_phantom_refs(true);
        // Options.v().setPhaseOption("cg.spark", "on");
        Scene.v().loadNecessaryClasses();
        PackManager.v().runPacks();
        AnalysisLogger.log(true, "Done processing the JAR");
    }

    public static void loadClassToSoot(String name, String module) {
        try {
            SootClass c = ModuleScene.v().loadClassAndSupport(name, Optional.of(module));
            c.setApplicationClass();
            AnalysisLogger.log(true, "Loaded Class: {}", name);
        } catch (Exception e) {
            AnalysisLogger.log(true, "Can't load class: {}", name);
        }
        
   }

    private void prepareProcessingApk(String apkPath, String platFormDir, String callbackFile) {
        AnalysisLogger.log(true, "Processing APK: {}", apkPath);
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