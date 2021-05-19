package mandoline.framework;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InstanceInvokeExpr;
import org.json.XML;

import mandoline.accesspath.AccessPath;
import mandoline.accesspath.AliasSet;
import mandoline.framework.FrameworkAssignment.VariableType;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementSet;
import mandoline.utils.AnalysisUtils;
import mandoline.utils.Constants;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



public class FrameworkModel {


    private FrameworkModel() {
        throw new IllegalStateException("Utility class");
    }

    private static final String ACCESS_PATH = "AccessPath";
    static String stubDroidPath;
    static String extraPath;
    static String taintWrapperPath;
    static Map<String, Set<FrameworkAssignment>> stubDroidModel = new HashMap<>();
    static Map<String, Set<FrameworkAssignment>> taintWrapperModel = new HashMap<>();
    static Map<String, String> taintWrapperType = new HashMap<>();

    public static void readTaintWrapperFile(){
        try (BufferedReader br = new BufferedReader(new FileReader(FrameworkModel.taintWrapperPath))) {
            String line = "";
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("%") && !line.startsWith("^")) {
                    if (line.startsWith("~")) {
                        addExcludeModel(line.substring(1));
                    } else if (line.startsWith("-")) {
                        addKillModel(line.substring(1));
                    } else {
                        addGenModel(line);
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private static Set<FrameworkAssignment> getIdentityAssignements(String methodSignature) {
        Set<FrameworkAssignment> model = new HashSet<>();

        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        for (int i = 0; i < numArgs; i++) {
            FrameworkAssignment assignIdentity = new FrameworkAssignment(i, i);
            model.add(assignIdentity);
        }
        return model;
    }


    private static void addExcludeModel(String methodSignature) {
        Set<FrameworkAssignment> model = new HashSet<>();
        FrameworkAssignment thisAssign = new FrameworkAssignment(Constants.THIS, Constants.THIS);
        model.add(thisAssign);
        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        for (int i = 0; i < numArgs; i++) {
            FrameworkAssignment assignIdentity = new FrameworkAssignment(i, i);
            model.add(assignIdentity);
        }
        FrameworkAssignment retAssign = new FrameworkAssignment(Constants.RETURN, Constants.CLEAR);
        model.add(retAssign);
        taintWrapperModel.put(methodSignature, model);
        taintWrapperType.put(methodSignature, "Exclude");
    }

    private static void addKillModel(String methodSignature) {
        Set<FrameworkAssignment> model = new HashSet<>();
        FrameworkAssignment thisAssign = new FrameworkAssignment(Constants.THIS, Constants.CLEAR);
        model.add(thisAssign);
        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        if (StringUtils.countMatches(methodSignature, "()")==1) {
            numArgs = 0;
        }
        for (int i = 0; i < numArgs; i++) {
            FrameworkAssignment assign = new FrameworkAssignment(i, Constants.CLEAR);
            model.add(assign);
        }
        FrameworkAssignment retAssign = new FrameworkAssignment(Constants.RETURN, Constants.CLEAR);
        model.add(retAssign);
        taintWrapperModel.put(methodSignature, model);
        taintWrapperType.put(methodSignature, "Kill");
    }

    private static void addGenModel(String methodSignature) {
        Set<FrameworkAssignment> model = new HashSet<>();
        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        if (StringUtils.countMatches(methodSignature, "()")==1) {
            numArgs = 0;
        }
        for (int i = 0; i < numArgs; i++) {
            FrameworkAssignment assignThis = new FrameworkAssignment(Constants.THIS, i);
            FrameworkAssignment assignReturn = new FrameworkAssignment(Constants.RETURN, i);
            FrameworkAssignment assignIdentity = new FrameworkAssignment(i, i);
            model.add(assignThis);
            model.add(assignReturn);
            model.add(assignIdentity);
        }
        FrameworkAssignment retAssignFromThis = new FrameworkAssignment(Constants.RETURN, Constants.THIS);
        model.add(retAssignFromThis);
        taintWrapperModel.put(methodSignature, model);
        taintWrapperType.put(methodSignature, "Generate");
    }

    private static void addDefaultModel(String methodSignature) {
        Set<FrameworkAssignment> model = new HashSet<>();
        String[] splitted = methodSignature.split("\\s+");
        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        if (StringUtils.countMatches(methodSignature, "()")==1) {
            numArgs = 0;
        }
        if (splitted[1].equals("void")) {
            FrameworkAssignment assignThis = new FrameworkAssignment(Constants.THIS, Constants.THIS);
            model.add(assignThis);
        }
        for (int i = 0; i < numArgs; i++) {
            FrameworkAssignment assignReturn = new FrameworkAssignment(Constants.RETURN, i);
            model.add(assignReturn);
            if (splitted[1].equals("void")) {
                FrameworkAssignment assignThis = new FrameworkAssignment(Constants.THIS, i);
                model.add(assignThis);
            }
            FrameworkAssignment assignIdentity = new FrameworkAssignment(i, i);
            model.add(assignIdentity);
        }
        FrameworkAssignment retAssignFromThis = new FrameworkAssignment(Constants.RETURN, Constants.THIS);
        model.add(retAssignFromThis);
        taintWrapperModel.put(methodSignature, model);
        taintWrapperType.put(methodSignature, "Default");
    }

    public static void setStubDroidPath(String stubDroidPath) {
        FrameworkModel.stubDroidPath = stubDroidPath;
    }

    public static void setExtraPath(String extraPath) {
        FrameworkModel.extraPath = extraPath;
    }

    public static void setTaintWrapperFile(String taintWrapperPath) {
        FrameworkModel.taintWrapperPath = taintWrapperPath;
        readTaintWrapperFile();
    }

    private static Set<FrameworkAssignment> convertMethod(InvokeExpr expr){
        String methodSignature = expr.getMethod().getSignature();
        if (!taintWrapperType.containsKey(methodSignature)) {
            loadStubroidModel(expr, FrameworkModel.stubDroidPath);
            loadStubroidModel(expr, FrameworkModel.extraPath);
        }
        Set<FrameworkAssignment> model = stubDroidModel.get(methodSignature); // stubdroid model
        if (model == null) {
            model = taintWrapperModel.get(methodSignature); // taintWrapper model
        }
        if (model == null) {
            // default taint wrapper model
            addDefaultModel(methodSignature);
            model = taintWrapperModel.get(methodSignature);
        }
        return model;
    }


    private static void loadStubroidModel(InvokeExpr expr, String path) {
        String methodSignature = expr.getMethod().getSignature();
        if (stubDroidModel.containsKey(methodSignature)) {
            return;
        }
        String className = expr.getMethod().getDeclaringClass().getName();
        String fileName = path + File.separator + className + ".xml";
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line = "";
            String xmlStr = "";
            while ((line = br.readLine()) != null) {
                xmlStr += line;
            }
            JSONArray jsonArray = XML.toJSONObject(xmlStr).getJSONObject("summary").getJSONObject("methods").optJSONArray("method");
            if (jsonArray == null) {
                jsonArray = new JSONArray();
                jsonArray.put(XML.toJSONObject(xmlStr).getJSONObject("summary").getJSONObject("methods").getJSONObject("method"));
            }
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Set<FrameworkAssignment> model = new HashSet<>();
                String modelId = "<"+className+ ": " + jsonObject.getString("id") +">";
                model.add(new FrameworkAssignment(Constants.THIS, Constants.THIS));
                extractClearsOrFlowsFromStubDroidModel(jsonObject, model);
                model.addAll(getIdentityAssignements(modelId));
                if (!model.isEmpty()) {
                    stubDroidModel.put(modelId, model);
                    taintWrapperType.put(modelId, "StubDroid");
                }
            }
        } catch (IOException | JSONException e) {
            // Ignored
        }
    }

    private static void extractClearsOrFlowsFromStubDroidModel(JSONObject jsonObject, Set<FrameworkAssignment> model) {
        try {
            JSONObject clearObject = jsonObject.getJSONObject("clears").optJSONObject("clear");
            if (clearObject == null) {
                JSONArray clearArray = jsonObject.getJSONObject("clears").getJSONArray("clear");
                for (int j = 0; j < clearArray.length(); j++) {
                    JSONObject clear = clearArray.getJSONObject(j);
                    model.add(getClear(clear));
                }
            } else {
                model.add(getClear(clearObject));
            }
        } catch (JSONException e) {
            extractFlowsFromStubDroidModel(jsonObject, model);
        }
    }

    private static void extractFlowsFromStubDroidModel(JSONObject jsonObject, Set<FrameworkAssignment> model) {
        try {
            JSONObject flowObject = jsonObject.getJSONObject("flows").optJSONObject("flow");
            if (flowObject == null) {
                JSONArray flowArray = jsonObject.getJSONObject("flows").getJSONArray("flow");
                for (int j = 0; j < flowArray.length(); j++) {
                    JSONObject flow = flowArray.getJSONObject(j);
                    model.add(getFlow(flow));
                }
            } else {
                model.add(getFlow(flowObject));
            }
        } catch (JSONException e2) {
            // pass
        }
    }


    private static FrameworkAssignment getClear(JSONObject clear){
        String leftPath = "";
        Integer leftParam = Constants.RETURN;
        Integer rightParam = Constants.CLEAR;

        String leftType = clear.getString("sourceSinkType");
        if (leftType.equals("Field")) {
            leftParam = Constants.THIS;
        }
        
        String dstAp = clear.optString(ACCESS_PATH);
        if (dstAp != null && !dstAp.isEmpty()) {
            leftPath = dstAp;
        }

        return new FrameworkAssignment(leftParam, rightParam, leftPath, "");
    }

    private static FrameworkAssignment getFlow(JSONObject flow){
        String leftPath = "";
        String rightPath = "";
        Integer leftParam = Constants.RETURN;
        Integer rightParam = Constants.THIS;
        try {
            rightParam = flow.getJSONObject("from").getInt("ParameterIndex");
        } catch (JSONException e) {
            // pass
        }
        String srcAp = flow.getJSONObject("from").optString(ACCESS_PATH);
        if (srcAp != null && !srcAp.isEmpty()) {
            rightPath = srcAp;
        }

        
        try {
            leftParam = flow.getJSONObject("to").getInt("ParameterIndex");
        } catch (JSONException e) {
            String leftType = flow.getJSONObject("to").getString("sourceSinkType");
            if (leftType.equals("Field")) {
                leftParam = Constants.THIS;
            }
        }
        String dstAp = flow.getJSONObject("to").optString(ACCESS_PATH);
        if (dstAp != null && !dstAp.isEmpty()) {
            leftPath = dstAp;
        }

        return new FrameworkAssignment(leftParam, rightParam, leftPath, rightPath);
    }

    public static boolean localWrapper(StatementInstance si, AccessPath ap, StatementSet defSet, AliasSet usedVars){
        boolean defintionFound = false;
        InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(si);
        if (invokeExpr == null) {
            return false;
        }
        if (si.getUnit() instanceof IfStmt) {
            return false;
        }
        Set<FrameworkAssignment> model = convertMethod(invokeExpr);
        for (FrameworkAssignment frameworkAssignment: model) {
            if (frameworkAssignment.leftType.equals(VariableType.PARAM)) {
                defintionFound = localParamToParamFlow(si, ap, defSet, usedVars, defintionFound, invokeExpr, frameworkAssignment);
            } else if (frameworkAssignment.leftType.equals(VariableType.RETURN)) {
                defintionFound = localReturnToParamFlow(si, ap, defSet, usedVars, defintionFound, invokeExpr, frameworkAssignment);
            } else if (frameworkAssignment.leftType.equals(VariableType.INSTANCE) && !invokeExpr.getMethod().isStatic()) {
                defintionFound = localReferenceToParamFlow(si, ap, defSet, usedVars, defintionFound, invokeExpr, frameworkAssignment);
            }
        }
        return defintionFound;
    }

    private static boolean localReferenceToParamFlow(StatementInstance si, AccessPath ap, StatementSet defSet, AliasSet usedVars,
            boolean defintionFound, InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        if (ap.baseEquals(((InstanceInvokeExpr) invokeExpr).getBase().toString())) {
            defSet.add(si);
            usedVars.add(frameworkAssignment.getRightAccessPath(invokeExpr, si.getLineNo(), si));
            defintionFound = true;
        }
        return defintionFound;
    }

    private static boolean localReturnToParamFlow(StatementInstance si, AccessPath ap, StatementSet defSet, AliasSet usedVars,
            boolean defintionFound, InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        if ((si.getUnit() instanceof AssignStmt)) {
            if (ap.baseEquals(((AssignStmt) si.getUnit()).getLeftOp().toString())) {
                defSet.add(si);
                usedVars.add(frameworkAssignment.getRightAccessPath(invokeExpr, si.getLineNo(), si));
                defintionFound = true;
            }
        }
        return defintionFound;
    }

    private static boolean localParamToParamFlow(StatementInstance si, AccessPath ap, StatementSet defSet, AliasSet usedVars,
            boolean defintionFound, InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        List<Value> args = invokeExpr.getArgs();
        for(int argPos = 0; argPos < args.size(); argPos++) {
            if (frameworkAssignment.leftParam == argPos) {
                if (ap.baseEquals(frameworkAssignment.getLeftParamAccessPath(invokeExpr, si.getLineNo(), si).getBase().getO1())) {
                    defSet.add(si);
                    usedVars.add(frameworkAssignment.getRightAccessPath(invokeExpr, si.getLineNo(), si));
                    defintionFound = true;
                }
            }
        }
        return defintionFound;
    }

    public static void forwardWrapper(AliasSet taintSet, AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath) {
        InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(si);
        if (invokeExpr == null) {
            return;
        }
        if (si.getUnit() instanceof IfStmt) {
            return;
        }
        Set<FrameworkAssignment> model = convertMethod(invokeExpr);
        for (FrameworkAssignment frameworkAssignment: model) {
            if (frameworkAssignment.rightType.equals(VariableType.INSTANCE)) {
                forwardReferenceFlows(taintSet, newTaintSet, si, aliasPath, invokeExpr, frameworkAssignment);
            } else if (frameworkAssignment.rightType.equals(VariableType.PARAM)) {
                forwardParamFlows(taintSet, newTaintSet, si, aliasPath, invokeExpr, frameworkAssignment);
            } else if (frameworkAssignment.rightType.equals(VariableType.CLEAR)) {
                forwardClearFlows(newTaintSet, si, invokeExpr, frameworkAssignment);
            }
        }
    }

    private static void forwardReferenceFlows(AliasSet taintSet, AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        if (invokeExpr instanceof InstanceInvokeExpr) {
            for (AccessPath ap: taintSet) {
                if (ap.startsWith(frameworkAssignment.getInstanceAccessPath(invokeExpr, si.getLineNo(), si))) {
                    if (frameworkAssignment.leftType.equals(VariableType.RETURN)) {
                        forwardReferenceToReturnFlow(newTaintSet, si, invokeExpr, frameworkAssignment, ap);
                    } else if (frameworkAssignment.leftType.equals(VariableType.INSTANCE)) {
                        forwardReferenceToReferenceFlow(newTaintSet, si, aliasPath, invokeExpr, frameworkAssignment, ap);
                    } else if (frameworkAssignment.leftType.equals(VariableType.PARAM)) {
                        forwardReferenceToParamFlow(newTaintSet, si, aliasPath, invokeExpr, frameworkAssignment, ap);
                    }
                }
            }
        }
    }

    private static void forwardClearFlows(AliasSet newTaintSet, StatementInstance si, InvokeExpr invokeExpr,
            FrameworkAssignment frameworkAssignment) {
        if (frameworkAssignment.leftType.equals(VariableType.RETURN)) {
            forwardReturnClear(newTaintSet, si, frameworkAssignment);
        } else if (frameworkAssignment.leftType.equals(VariableType.INSTANCE) && !invokeExpr.getMethod().isStatic()) {
            forwardReferenceClear(newTaintSet, si, invokeExpr, frameworkAssignment);
        } else if (frameworkAssignment.leftType.equals(VariableType.PARAM)) {
            forwardParamClear(newTaintSet, si, invokeExpr, frameworkAssignment);
        }
    }

    private static void forwardParamFlows(AliasSet taintSet, AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        List<Value> args = invokeExpr.getArgs();
        for(int argPos = 0; argPos < args.size(); argPos++) {
            if (frameworkAssignment.rightParam == argPos) {
                forwardArgFlows(taintSet, newTaintSet, si, aliasPath, invokeExpr, frameworkAssignment);
            }
        }
    }

    private static void forwardArgFlows(AliasSet taintSet, AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        for (AccessPath ap: taintSet) {
            if (ap.startsWith(frameworkAssignment.getRightAccessPath(invokeExpr, si.getLineNo(), si))) {
                if (frameworkAssignment.leftType.equals(VariableType.RETURN)) {
                    forwardParamToReturnFlow(newTaintSet, si, invokeExpr, frameworkAssignment, ap);
                } else if (frameworkAssignment.leftType.equals(VariableType.INSTANCE) && !invokeExpr.getMethod().isStatic()) {
                    forwardParamToReferenceFlow(newTaintSet, si, invokeExpr, frameworkAssignment, ap);
                } else if (frameworkAssignment.leftType.equals(VariableType.PARAM)) {
                    forwardParamToParamFlow(newTaintSet, si, aliasPath, invokeExpr, frameworkAssignment, ap);
                }
            }
        }
    }

    private static void forwardParamClear(AliasSet newTaintSet, StatementInstance si, InvokeExpr invokeExpr,
            FrameworkAssignment frameworkAssignment) {
        AccessPath leftAccessPath = frameworkAssignment.getLeftParamAccessPath(invokeExpr, si.getLineNo(), si);
        newTaintSet.remove(leftAccessPath);
    }

    private static void forwardReferenceClear(AliasSet newTaintSet, StatementInstance si, InvokeExpr invokeExpr,
            FrameworkAssignment frameworkAssignment) {
        AccessPath instanceAp = frameworkAssignment.getInstanceAccessPath(invokeExpr, si.getLineNo(), si);
        newTaintSet.remove(instanceAp);
    }

    private static void forwardReturnClear(AliasSet newTaintSet, StatementInstance si, FrameworkAssignment frameworkAssignment) {
        if ((si.getUnit() instanceof AssignStmt)) {
            AccessPath returnAp = frameworkAssignment.getReturnAccessPath((AssignStmt) si.getUnit(), si.getLineNo(), si);
            newTaintSet.remove(returnAp);
        }
    }

    private static void forwardParamToParamFlow(AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment, AccessPath ap) {
        Value v = invokeExpr.getArg(frameworkAssignment.leftParam);
        if (!(v instanceof Constant) && !(AnalysisUtils.isPrimitiveType(v))) {
            AccessPath leftAccessPath = frameworkAssignment.getLeftParamAccessPath(invokeExpr, ap.getUsedLine(), si);
            leftAccessPath.add(ap.getAfter(frameworkAssignment.getRightAccessPath(invokeExpr, ap.getUsedLine(), si)), si);
            if (ap.getUsedLine() > si.getLineNo()) {
                aliasPath.add(si);
            }
            newTaintSet.add(leftAccessPath);
        }
    }

    private static void forwardParamToReferenceFlow(AliasSet newTaintSet, StatementInstance si, InvokeExpr invokeExpr,
            FrameworkAssignment frameworkAssignment, AccessPath ap) {
        AccessPath instanceAp = frameworkAssignment.getInstanceAccessPath(invokeExpr, ap.getUsedLine(), si);
        instanceAp.add(ap.getAfter(frameworkAssignment.getRightAccessPath(invokeExpr, ap.getUsedLine(), si)), si);
        newTaintSet.add(instanceAp);
    }

    private static void forwardParamToReturnFlow(AliasSet newTaintSet, StatementInstance si, InvokeExpr invokeExpr,
            FrameworkAssignment frameworkAssignment, AccessPath ap) {
        if ((si.getUnit() instanceof AssignStmt)) {
            Value v = ((AssignStmt) si.getUnit()).getLeftOp();
            if (!(v instanceof Constant) && !(AnalysisUtils.isPrimitiveType(v))) {
                AccessPath returnAp = frameworkAssignment.getReturnAccessPath((AssignStmt) si.getUnit(), ap.getUsedLine(), si);
                returnAp.add(ap.getAfter(frameworkAssignment.getRightAccessPath(invokeExpr, ap.getUsedLine(), si)), si);
                newTaintSet.add(returnAp);
            }
        }
    }

    private static void forwardReferenceToParamFlow(AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment, AccessPath ap) {
        Value v = ((InstanceInvokeExpr) si.getUnit()).getArg(frameworkAssignment.leftParam);
        if (!(v instanceof Constant) && !(AnalysisUtils.isPrimitiveType(v))) {
            AccessPath leftAccessPath = frameworkAssignment.getLeftParamAccessPath(invokeExpr, ap.getUsedLine(), si);
            if (ap.getUsedLine() > si.getLineNo()) {
                aliasPath.add(si);
            }
            newTaintSet.add(leftAccessPath);
        }
    }

    private static void forwardReferenceToReferenceFlow(AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment, AccessPath ap) {
        AccessPath instanceAp = frameworkAssignment.getInstanceAccessPath(invokeExpr, ap.getUsedLine(), si);
        if (ap.getUsedLine() > si.getLineNo()) {
            aliasPath.add(si);
        }
        newTaintSet.add(instanceAp);
    }

    private static void forwardReferenceToReturnFlow(AliasSet newTaintSet, StatementInstance si, InvokeExpr invokeExpr,
            FrameworkAssignment frameworkAssignment, AccessPath ap) {
        if ((si.getUnit() instanceof AssignStmt)) {
            Value v = ((AssignStmt) si.getUnit()).getLeftOp();
            if (!(v instanceof Constant) && !(AnalysisUtils.isPrimitiveType(v))) {
                AccessPath returnAp = frameworkAssignment.getReturnAccessPath((AssignStmt) si.getUnit(), ap.getUsedLine(), si);
                returnAp.add(ap.getAfter(frameworkAssignment.getInstanceAccessPath(invokeExpr, ap.getUsedLine(), si)), si);
                newTaintSet.add(returnAp);
            }
        }
    }

    public static void backwardWrapper(AliasSet fwSet, AliasSet taintSet, AliasSet newTaintSet, StatementInstance si, StatementSet aliasPath) {
        InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(si);
        if (invokeExpr == null || (si.getUnit() instanceof IfStmt)) {
            return;
        }
        Set<FrameworkAssignment> model = convertMethod(invokeExpr);
        for (FrameworkAssignment frameworkAssignment: model) {
            if (frameworkAssignment.leftType.equals(VariableType.INSTANCE)) {
                backwardReferenceFlow(fwSet, taintSet, newTaintSet, si, aliasPath, invokeExpr, frameworkAssignment);
            } else if (frameworkAssignment.leftType.equals(VariableType.RETURN)) {
                backwardReturnFlow(fwSet, taintSet, newTaintSet, si, invokeExpr, frameworkAssignment);
            } else if (frameworkAssignment.leftType.equals(VariableType.PARAM)) {
                backwardParamFlow(fwSet, taintSet, newTaintSet, si, invokeExpr, frameworkAssignment);
            }
        }
    }

    private static void backwardParamFlow(AliasSet fwSet, AliasSet taintSet, AliasSet newTaintSet, StatementInstance si,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        List<Value> args = invokeExpr.getArgs();
        for(int argPos = 0; argPos < args.size(); argPos++) {
            if (frameworkAssignment.rightParam == argPos) {
                for (AccessPath ap: taintSet) {
                    if (ap.startsWith(frameworkAssignment.getLeftParamAccessPath(invokeExpr, ap.getUsedLine(), si))) {
                        AccessPath rightAp = frameworkAssignment.getRightAccessPath(invokeExpr, ap.getUsedLine(), si);
                        if (!rightAp.isEmpty()) {
                            rightAp.add(ap.getAfter(frameworkAssignment.getLeftParamAccessPath(invokeExpr, ap.getUsedLine(), si)), si);
                            newTaintSet.add(rightAp);
                            fwSet.add(rightAp);
                        }
                    }
                }
            }
        }
    }

    private static void backwardReturnFlow(AliasSet fwSet, AliasSet taintSet, AliasSet newTaintSet, StatementInstance si,
            InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        if (si.getUnit() instanceof AssignStmt) {
            for (AccessPath ap: taintSet) {
                if (ap.startsWith(frameworkAssignment.getReturnAccessPath((AssignStmt) si.getUnit(), ap.getUsedLine(), si))) {
                    AccessPath rightAp = frameworkAssignment.getRightAccessPath(invokeExpr, ap.getUsedLine(), si);
                    if (!rightAp.isEmpty()) {
                        rightAp.add(ap.getAfter(frameworkAssignment.getReturnAccessPath((AssignStmt) si.getUnit(), ap.getUsedLine(), si)), si);
                        newTaintSet.add(rightAp);
                        fwSet.add(rightAp);
                    }
                }
            }
        }
    }

    private static void backwardReferenceFlow(AliasSet fwSet, AliasSet taintSet, AliasSet newTaintSet, StatementInstance si,
            StatementSet aliasPath, InvokeExpr invokeExpr, FrameworkAssignment frameworkAssignment) {
        if (invokeExpr instanceof InstanceInvokeExpr) {
            for (AccessPath ap: taintSet) {
                if (ap.startsWith(frameworkAssignment.getInstanceAccessPath(invokeExpr, ap.getUsedLine(), si))) {
                    AccessPath rightAp = frameworkAssignment.getRightAccessPath(invokeExpr, ap.getUsedLine(), si);
                    if (!rightAp.isEmpty()) {
                        rightAp.add(ap.getAfter(frameworkAssignment.getInstanceAccessPath(invokeExpr, ap.getUsedLine(), si)), si);
                        newTaintSet.add(rightAp);
                        fwSet.add(rightAp);
                        if (ap.getUsedLine() > si.getLineNo()) {
                            aliasPath.add(si);
                        }
                    }
                }
            }
        }
    }


    public static boolean definesInstance(StatementInstance iu, AccessPath ap) {
        InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(iu);
        if (invokeExpr == null) {
            return false;
        }
        if (iu.getUnit() instanceof IfStmt) {
            return false;
        }
        Set<FrameworkAssignment> model = convertMethod(invokeExpr);
        for (FrameworkAssignment frameworkAssignment: model) {
            if (frameworkAssignment.leftType.equals(VariableType.INSTANCE) && !frameworkAssignment.rightType.equals(VariableType.INSTANCE) && !invokeExpr.getMethod().isStatic() && ap.baseEquals(((InstanceInvokeExpr) invokeExpr).getBase().toString())) {
                return true;
            }
        }
        return false;
    }
}