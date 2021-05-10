package mandoline.instrumenter;

import java.util.Set;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;

import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.util.Chain;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import mandoline.slicer.Slicer;
import mandoline.utils.AnalysisLogger;
import soot.Body;
import soot.BodyTransformer;

import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.options.Options;

public class JimpleWriter {
    // private boolean isWritten = false;
    private String outFilePath;
    private Set<SootClass> classes = new HashSet<>();

    public JimpleWriter(String outDir) {
        this.outFilePath = outDir + "/jimple_code/";
    }

    
    void initialize(String apkPath) {
        AnalysisLogger.log(true, "Initializing Instrumenter");
        if (apkPath.endsWith(".apk")) {
            Options.v().set_src_prec(Options.src_prec_apk);
        } else {
            Options.v().set_prepend_classpath(true);
            // Options.v().set_soot_classpath("VIRTUAL_FS_FOR_JDK");
            Options.v().set_no_bodies_for_excluded(true);
            Options.v().set_allow_phantom_refs(true);
            Options.v().set_process_dir(Arrays.asList(apkPath));
            Options.v().set_output_format(Options.output_format_jimple);
            Options.v().set_output_dir(this.outFilePath);
        }
        

        
        AnalysisLogger.log(true, "Initialization done");
    }

    

    public void start (String apkPath) {
        soot.G.reset();
        initialize(apkPath);
        Scene.v().loadNecessaryClasses();
        AnalysisLogger.log(true, "Running packs ... ");
        PackManager.v().runPacks();
        AnalysisLogger.log(true, "Writing output ... ");
        PackManager.v().writeOutput();
        AnalysisLogger.log(true, "Output written ... ");
    }


    // private synchronized void putClass(SootClass sc) {
    //     classes.add(sc);
    // }
    // private boolean isWritten(){
    //     return isWritten;
    // }
    // private void dumpJimple() {
    //     File directory = new File(outFilePath);
    //     if (!directory.exists()){
    //         directory.mkdirs();
    //     }
    //     for (SootClass sc: classes) {
    //         String classPath = outFilePath + "/" + sc.getName().replace(".", "/") + ".jimple";
    //         String classString = sc.getName();
    //         Chain <SootClass> interfaces = sc.getInterfaces();
    //         classString += " extends " + sc.getSuperclass();
    //         if (!interfaces.isEmpty()) {
    //             classString += " implements";
    //             for (SootClass in: interfaces) {
    //                 classString += " " + in.getName();
    //             }
    //         }
    //         classString += "\n";
    //         for (SootField sf: sc.getFields()) {
    //             classString += sf.getDeclaration() + "\n";
    //         }
    //         for (SootMethod sm: sc.getMethods()) {
    //             if (sm.hasActiveBody()) {
    //                 classString += sm.getActiveBody().toString();
    //             } else {
    //                 classString += sm.getDeclaration() + " { \n";
    //                 classString += "// no body\n";
    //                 classString += "} \n";
    //             }
    //         }
            
    //         File classFile = new File(classPath);
    //         if (!classFile.getParentFile().exists()) {
    //             classFile.getParentFile().mkdirs();
    //         }
    //         try{
    //             FileWriter fw = new FileWriter(classFile.getAbsoluteFile());
    //             BufferedWriter bw = new BufferedWriter(fw);
    //             bw.write(classString);
    //             bw.close();
    //         }
    //         catch (IOException e){
    //             e.printStackTrace();
    //         }
    //     }
    //     isWritten = true;
    // }
}


