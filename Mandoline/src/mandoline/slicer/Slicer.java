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
import mandoline.utils.Constants;
import soot.Local;
// import soot.ModulePathSourceLocator;
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
    private String pathApk;
    private String platformPath;
    private String callbackFile;
    private String outDir;
    private String staticLogFile;
    private String instrumenterMode;
    private String mandolineLoggerJar;
    private String fileToParse;
    private String outFile;
    private int backwardSlicePos;
    private int forwardSlicePos;
    private String variableString;
    private List<String> variables = new ArrayList<>();
    private SlicingWorkingSet workingSet;


    public Slicer(){

    }
    public static Slicer getInstance() {
        return instance;
    }

    public Set<String> getDynamicPrint() {
        return dynamicPrint;
    }

    public void setPathApk(String pathApk) {
        this.pathApk = pathApk;
    }


    public void setPlatformPath(String platformPath) {
        this.platformPath = platformPath;
    }

    public void setCallbackFile(String callbackFile) {
        this.callbackFile = callbackFile;
    }

    public void setOutDir(String outDir) {
        this.outDir = outDir;
        File outDirFile = new File(this.outDir);
        outDirFile.mkdirs();
        this.staticLogFile = this.outDir + File.separator + "static-log.log";
    }

    public void setInstrumenterMode(String instrumenterMode) {
        this.instrumenterMode = instrumenterMode;
    }

    public void setMandolineLoggerJar(String mandolineLoggerJar) {
        this.mandolineLoggerJar = mandolineLoggerJar;
    }

    public void setFileToParse(String fileToParse) {
        this.fileToParse = fileToParse;
        this.outFile = fileToParse+"_icdg.log";
    }

    public void setBackwardSlicePos(int backwardSlicePos) {
        this.backwardSlicePos = backwardSlicePos;
    }

    public void setVariableString(String variableString) {
        this.variableString = variableString;
    }

    public void setVariables(List<String> variables) {
        this.variables = variables;
    }

    public String getStaticLogFile() {
        return staticLogFile;
    }

    public Map<Pair<SootMethod, Unit>, String> getThreadCallers() {
        return threadCallers;
    }

    public MultiMap<SootClass, AndroidCallbackDefinition> getCallbackMethods() {
        return callbackMethods;
    }

    public Map<Pair<SootMethod, Unit>, SootClass> getSetterCallbackMap() {
        return setterCallbackMap;
    }

    public void setDebug(boolean flag) {
        Constants.DEBUG = flag;
    }

    public void setWorkingSet(SlicingWorkingSet workingSet) {
        this.workingSet = workingSet;
    }

    public SlicingWorkingSet getWorkingSet() {
        return workingSet;
    }


    void printList(List <String> list, String outFile) {
        try {
            AnalysisLogger.log(Constants.DEBUG, "Printing to {}", outFile);
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
            Slicer slicer = new Slicer();
            slicer.setPathApk(commands.get("a"));
            throwParseExceptionIfNull(slicer.pathApk, "Apk path not provided");

            slicer.setPlatformPath(commands.get("p"));

            slicer.setCallbackFile(commands.get("c"));
 
            slicer.setOutDir(commands.get("o"));
            throwParseExceptionIfNull(slicer.outDir, "Output directory path not provided");

            boolean instrumented = false;
            if(mode.equals("i")) {
                instrumented = slicer.instrument(mode);
                slicer.setInstrumenterMode(commands.get("im"));
                throwParseExceptionIfNull(slicer.instrumenterMode, "instrumenter mode not provided");
                slicer.setMandolineLoggerJar(commands.get("ml"));
                if (slicer.mandolineLoggerJar == null) {
                    throwParseExceptionIfNull(slicer.mandolineLoggerJar, "mandoline logger jar path not provided");
                }
            }

            if (instrumented) {
                terminate(slicer.outDir, slicer.instrumenterMode, startTime);
                return;
            }

            slicer.setFileToParse(commands.get("t"));
            throwParseExceptionIfNull(slicer.fileToParse, "Trace file path not provided");


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
            List <Traces> trs = Parser.readFile(slicer.fileToParse, slicer.staticLogFile);

            slicer.prepare();
            Slicer.instance = slicer;
            ICDG icdg = new ICDG(slicer.setterCallbackMap, slicer.callbackMethods, slicer.threadCallers);
            icdg.createDCFG(trs);

            slicer.printGraph(icdg);

            if(justTrace) {
                terminate(slicer.outDir, mode, startTime);
                return;
            }

            slicer.setBackwardSlicePos(Integer.parseInt(commands.get("sp")));
            slicer.setVariableString(commands.get("sv"));
            if (slicer.variableString == null) {
                slicer.variableString = "*";
            }
            String stubDroidPath = commands.get("sd");
            String taintWrapperPath = commands.get("tw");
            int forwardSlicePos = -1;
            if (commands.get("fw") != null) {
                forwardSlicePos = Integer.parseInt(commands.get("fw"));
            }
            
            

            boolean staticAnalysis = true;
            boolean dynamicAnalysis = false;
            boolean frameworkModel = true;
            boolean dataFlowsOnly = false;
            boolean controlFlowOnly = false;
            if (commands.containsKey("data")) {
                dataFlowsOnly = true;
            }
            if (commands.containsKey("ctrl")) {
                controlFlowOnly = true;
            }
            

            if (mode.equals("m")) {
                staticAnalysis = true;
                dynamicAnalysis = false;
                frameworkModel = true;
                AnalysisLogger.log(Constants.DEBUG, "Analysis type set to Mandoline static");
            } else if (mode.equals("md")){
                staticAnalysis = false;
                dynamicAnalysis = true;
                frameworkModel = true;
                AnalysisLogger.log(Constants.DEBUG, "Analysis type set to Mandoline dynamic");
            } else if (mode.equals("mf")){
                staticAnalysis = false;
                dynamicAnalysis = false;
                frameworkModel = true;
                AnalysisLogger.log(Constants.DEBUG, "Analysis type set to Mandoline framework");
            } else if (mode.equals("as")){
                staticAnalysis = false;
                dynamicAnalysis = false;
                frameworkModel = false;
                AnalysisLogger.log(Constants.DEBUG, "Analysis type set to AndroidSlicer");
            } else {
                throwParseException("Invalid mode " + mode);
            }
            String frameworkPath = commands.get("f");

            
            FrameworkModel.setStubDroidPath(stubDroidPath);
            FrameworkModel.setTaintWrapperFile(taintWrapperPath);
            if (frameworkPath != null) {
                FrameworkModel.setExtraPath(frameworkPath);
            }

            StatementInstance stmt = icdg.getMapKeyUnits().get(icdg.getMapNoKey().get(slicer.backwardSlicePos));
            
            List<String> variables = new ArrayList<>();
            if (!slicer.variableString.equals("*")) {
                String[] split = slicer.variableString.split("-");
                for (int i = 0; i < split.length; i++) {
                    variables.add("$"+split[i]);
                }
            }
            slicer.setVariables(variables);

            Set<AccessPath> accessPaths = new HashSet<>();
            for (String v: slicer.variables) {
                accessPaths.add(new AccessPath(v, new Type(){
                    private static final long serialVersionUID = 1L;
                    @Override
                    public String toString() {
                        return "SlicingCriterionType";
                    }
                }, slicer.backwardSlicePos, AccessPath.NOT_DEFINED, stmt));
            }

            AnalysisLogger.log(Constants.DEBUG, "Slicing criterion: (" + slicer.backwardSlicePos + ", " + variables + ")");
            AnalysisLogger.log(Constants.DEBUG, "size of the trace after loading:"+icdg.getMapKeyNo().keySet().size());
            AnalysisLogger.log(Constants.DEBUG, "Slicing from statement:"+ icdg.getMapNoKey().get(slicer.backwardSlicePos));


            slicer.setWorkingSet(new SlicingWorkingSet(false));
            DynamicSlice dynamicSlice = slicer.slice(icdg, staticAnalysis, dynamicAnalysis, frameworkModel, dataFlowsOnly, controlFlowOnly, stmt, accessPaths, slicer.getWorkingSet());
            if (forwardSlicePos != -1) {
                dynamicSlice = dynamicSlice.chop(forwardSlicePos, icdg);
            }
            slicer.dynamicPrint = new LinkedHashSet<>();
            SlicePrinter.printSlices(dynamicSlice);
            SlicePrinter.printSliceGraph(dynamicSlice);
            SlicePrinter.printDotGraph(slicer.outDir, dynamicSlice);
            SlicePrinter.printSliceLines(slicer.outDir, dynamicSlice);
            SlicePrinter.printRawSlice(slicer.outDir, dynamicSlice);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
            LocalDateTime now = LocalDateTime.now();
            String resultFileName = slicer.outDir + File.separator + "result_" +mode+"_"+ dtf.format(now) + ".csv";
            SlicePrinter.printToCSV(resultFileName, dynamicSlice);

        terminate(slicer.outDir, mode, startTime);
    }
    public DynamicSlice slice(ICDG icdg, boolean staticAnalysis, boolean dynamicAnalysis,
            boolean frameworkModel, boolean dataFlowsOnly, boolean controlFlowOnly, StatementInstance start, Set<AccessPath> variables, SlicingWorkingSet workingSet) {
        return new SliceMethod(icdg, staticAnalysis, dynamicAnalysis, frameworkModel, dataFlowsOnly, controlFlowOnly, workingSet).slice(start, variables);
    }


    private void printGraph(ICDG icdg) {
        AnalysisLogger.log(Constants.DEBUG, "Printing graph...");
        List <String> listTOPrint = new ArrayList<>();
        Iterator<Entry<String, Integer>> entries = icdg.getMapKeyNo().entrySet().iterator();
        while (entries.hasNext()) {
            Entry<String, Integer> thisEntry = entries.next();
            String key = thisEntry.getKey();
            listTOPrint.add(key + ":PRED:"+Graphs.predecessorListOf(icdg.getGraph(), thisEntry.getValue())+ ":SUCC:"+Graphs.successorListOf(icdg.getGraph(), thisEntry.getValue()) + ":TID:"+icdg.getMapKeyUnits().get(key).getThreadID());
        }
        printList(listTOPrint, outFile);
        AnalysisLogger.log(Constants.DEBUG, "Printing Complete.");
    }

    private boolean instrument(String mode) {
        boolean shouldTerminate = false;
        
        if (new File(SOOT_OUTPUT_STRING).isDirectory()) {
            deleteFolder(new File(SOOT_OUTPUT_STRING));
        }

        String instrumentOptions = parseInstrumentationMode(instrumenterMode);

        String[] instrumenterArgs = new String[0];
        if (pathApk.endsWith(".apk")) {
            String[] instrumenterArgsTemp = {instrumentOptions, staticLogFile, "-w", "-allow-phantom-refs", "-process-multiple-dex", "-android-jars", platformPath, "-src-prec", "apk", "-output-format", "dex", "-process-dir", pathApk, "-process-dir", mandolineLoggerJar};
            instrumenterArgs = instrumenterArgsTemp;
        } else if (pathApk.endsWith(".jar")) {
            String[] instrumenterArgsTemp = {instrumentOptions, staticLogFile, "-cp", "VIRTUAL_FS_FOR_JDK", "-pp", "-process-dir", pathApk, "-process-dir", mandolineLoggerJar};
            instrumenterArgs = instrumenterArgsTemp;
        } else {
            throwParseException("Not and apk or jar file!");
        }
        
        if (instrumentOptions.contains("jimple")) {
            JimpleWriter jimpleWriter = new JimpleWriter(outDir);
            jimpleWriter.start(pathApk);
            shouldTerminate = true;
        } else {
            if (pathApk.endsWith(".apk")) {
                throwParseExceptionIfNull(platformPath, "Platforms path not provided");
                throwParseExceptionIfNull(callbackFile, "Callback file path not provided");
                prepare();
                Instrumenter instrumenter = new AndroidInstrumenter(callbackMethods, threadCallers);
                instrumenter.start(instrumenterArgs);
            } else if (pathApk.endsWith(".jar")) {
                String jarName = outDir + File.separator + new File(pathApk).getName().replace(".jar", "_" +mode + ".jar");
                Instrumenter instrumenter = new JavaInstrumenter(jarName);
                instrumenter.start(instrumentOptions, staticLogFile, pathApk, mandolineLoggerJar);
            } else {
                throwParseException("Not and apk or jar file!");
            }
            shouldTerminate = true;
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
        AnalysisLogger.log(Constants.DEBUG, "Invalid command line options: {}", message);
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
        AnalysisLogger.log(Constants.DEBUG, "Time: {}", totalTime);
    }

    private static void deleteFolder(File folder) {
        try {
            FileUtils.forceDelete(folder);
        } catch (IOException e) {
            // pass
        }
    }

    public void prepare() {
        AnalysisCache.reset();
        if (pathApk.endsWith(".apk")) {
            prepareProcessingApk(pathApk, platformPath, callbackFile);
        } else if (pathApk.endsWith(".jar")) {
            prepareProcessingJAR(pathApk);
        } else {
            throwParseException("Not and apk or jar file!");
        }
        
    }

    private void prepareProcessingJAR(String apkPath) {
        
        AnalysisLogger.log(Constants.DEBUG, "Processing JAR: {}", apkPath);
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
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Scene.v().loadNecessaryClasses();
        PackManager.v().runPacks();
        AnalysisLogger.log(Constants.DEBUG, "Done processing the JAR");
    }

    public static void loadClassToSoot(String name, String module) {
        try {
            SootClass c = ModuleScene.v().loadClassAndSupport(name, Optional.of(module));
            c.setApplicationClass();
            AnalysisLogger.log(Constants.DEBUG, "Loaded Class: {}", name);
        } catch (Exception e) {
            AnalysisLogger.log(Constants.DEBUG, "Can't load class: {}", name);
        }
        
   }

    private void prepareProcessingApk(String apkPath, String platFormDir, String callbackFile) {
        AnalysisLogger.log(Constants.DEBUG, "Processing APK: {}", apkPath);
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
        AnalysisLogger.log(Constants.DEBUG, "Thread connections: {}", threadCallers);
        AnalysisLogger.log(Constants.DEBUG, "FlowDroid DummyMain: {}", entryPoint.getActiveBody());
    } 
}