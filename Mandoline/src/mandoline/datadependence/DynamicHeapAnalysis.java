package mandoline.datadependence;

import soot.Value;
import mandoline.accesspath.AccessPath;
import mandoline.framework.FrameworkModel;
import mandoline.graph.ICDG;
import mandoline.graph.Traversal;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementSet;
import mandoline.utils.AnalysisLogger;
import mandoline.utils.Constants;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

public class DynamicHeapAnalysis {
    private ICDG icdg;
    private Traversal traversal;
    public DynamicHeapAnalysis(ICDG icdg) {
        this.icdg = icdg;
        this.traversal = new Traversal(icdg);
    }

    public StatementSet reachingDefinitions(StatementInstance si, AccessPath ap) {
        StatementInstance def = null;
        Long fieldId = si.getFieldId();
        String fieldName = ap.getField();
        int pos = si.getLineNo()-1;
        StatementInstance prev = null;
        StatementInstance possibleIu = null;
        AnalysisLogger.log(true, "Getting heap def of {}", si);
        while (pos >= 0 && def == null) {
            prev = possibleIu;
            possibleIu = icdg.mapNoUnits(pos);
            if (possibleIu != null && possibleIu.getMethod().getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS)) {
                pos--;
                continue;
            }
            def = matchFieldAddress(si, possibleIu, prev, fieldId, fieldName);
            pos--;
        }

        StatementSet ret = new StatementSet();
        ret.add(def);
        return ret;
    }

    private StatementInstance matchFieldAddress(StatementInstance si, StatementInstance possibleIu, StatementInstance prev, Long fieldId, String fieldName) {
        StatementInstance def = null;
        if (possibleIu != null && possibleIu.getFieldId() != null && possibleIu.getFieldId().equals(fieldId)) {
            Unit u = possibleIu.getUnit();
            if (u instanceof AssignStmt) {
                AssignStmt assignStmt = ((AssignStmt) u);
                Value left = assignStmt.getLeftOp();
                Value right = assignStmt.getRightOp();
                if (left instanceof FieldRef) {
                    def = matchFieldDefintion(possibleIu, fieldName, def, left);
                } else if (right instanceof FieldRef) {
                    def = matchReferenceVaraibleDefintion(si, prev, fieldName, def, left, right);
                }
            }
        }
        return def;
    }

    private StatementInstance matchReferenceVaraibleDefintion(StatementInstance si, StatementInstance prev,
            String fieldName, StatementInstance def, Value left, Value right) {
        String usedField = ((FieldRef) right).getField().getName();
        if (usedField.equals(fieldName) && prev != null) {
            Stmt prevStmt = (Stmt) prev.getUnit();
            if (prevStmt.containsInvokeExpr() && traversal.isFrameworkMethod(prev)) {
                InvokeExpr expr = prevStmt.getInvokeExpr();
                if (expr instanceof InstanceInvokeExpr) {
                    AccessPath instance = new AccessPath(left.toString(), left.getType(), si.getLineNo(), si.getLineNo(), si);
                    if (FrameworkModel.definesInstance(prev, instance)) {
                        def = prev;
                    }
                }
            }
        }
        return def;
    }

    private StatementInstance matchFieldDefintion(StatementInstance possibleIu, String fieldName, StatementInstance def,
            Value left) {
        String definedField = ((FieldRef) left).getField().getName();
        if (definedField.equals(fieldName)) {
            def = possibleIu;
        }
        return def;
    }
}
