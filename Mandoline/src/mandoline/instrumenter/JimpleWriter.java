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

import mandoline.utils.AnalysisLogger;
import soot.Body;
import soot.BodyTransformer;

import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.options.Options;

public class JimpleWriter {
    private boolean isWritten = false;
    private String outFilePath;
    private Set<SootClass> classes = new HashSet<>();

    public JimpleWriter(String outDir) {
        this.outFilePath = outDir + "/jimple_code/";
    }

    private void initialize() {
        Options.v().set_src_prec(Options.src_prec_apk);
    }

    private void runMethodTransformationPack() {
        PackManager.v().getPack("jtp").add(new Transform("jtp.classprinter", new BodyTransformer() {
            @Override
            protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {
                if (!isWritten()) {
                    Chain<SootClass> chain = Scene.v().getApplicationClasses();
                    for (SootClass sc: chain) {
                        putClass(sc);
                    }
                    dumpJimple();
                }
            }
        }));
    }

    public void start (String args[]) {
        soot.G.reset();
        AnalysisLogger.log(true, "Soot args: {}", Arrays.asList(args));
        initialize();
        runMethodTransformationPack();
        int argLen = args.length-3;
        String newArgs[] = new String [argLen];
        for (int ii = 3; ii < args.length; ii++) {
            newArgs[ii-3]=args[ii];
        }
        soot.Main.main(newArgs);
        try {
            FileUtils.deleteDirectory(new File(args[0] + "/sootOutput/"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private synchronized void putClass(SootClass sc) {
        classes.add(sc);
    }
    private boolean isWritten(){
        return isWritten;
    }
    private void dumpJimple() {
        File directory = new File(outFilePath);
        if (!directory.exists()){
            directory.mkdirs();
        }
        for (SootClass sc: classes) {
            String classPath = outFilePath + "/" + sc.getName().replace(".", "/") + ".jimple";
            String classString = sc.getName();
            Chain <SootClass> interfaces = sc.getInterfaces();
            classString += " extends " + sc.getSuperclass();
            if (!interfaces.isEmpty()) {
                classString += " implements";
                for (SootClass in: interfaces) {
                    classString += " " + in.getName();
                }
            }
            classString += "\n";
            for (SootField sf: sc.getFields()) {
                classString += sf.getDeclaration() + "\n";
            }
            for (SootMethod sm: sc.getMethods()) {
                if (sm.hasActiveBody()) {
                    classString += sm.getActiveBody().toString();
                } else {
                    classString += sm.getDeclaration() + " { \n";
                    classString += "// no body\n";
                    classString += "} \n";
                }
            }
            
            File classFile = new File(classPath);
            if (!classFile.getParentFile().exists()) {
                classFile.getParentFile().mkdirs();
            }
            try{
                FileWriter fw = new FileWriter(classFile.getAbsoluteFile());
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(classString);
                bw.close();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
        isWritten = true;
    }
}


