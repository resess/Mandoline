package ca.ubc.ece.resess.slicer.dynamic.mandoline.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import soot.SootMethod;
import soot.SootClass;
import soot.Unit;
import soot.util.MultiMap;
import soot.toolkits.scalar.Pair;
import soot.jimple.infoflow.android.callbacks.AndroidCallbackDefinition;

public class ICDG  extends DynamicControlFlowGraph {
    private Map<SootClass, SootMethod> callbackMethods = new HashMap<>();
    private Set<String> threadMethods = new HashSet<>();
    public ICDG(Map<Pair<SootMethod, Unit>, SootClass> setterCallbackMap, MultiMap<SootClass, AndroidCallbackDefinition> callbackMethods, Map<Pair<SootMethod, Unit>, String> threadCallers) {
        super.setSetterCallbackMap(setterCallbackMap);
        for (AndroidCallbackDefinition acd: callbackMethods.values()){
            this.callbackMethods.put(acd.getTargetMethod().getDeclaringClass(), acd.getTargetMethod());
        }
        super.setCallbackMethods(this.callbackMethods);
        super.setThreadCallers(threadCallers);
        threadMethods.addAll(threadCallers.values());
        super.setThreadMethods(this.threadMethods);
    }
}
