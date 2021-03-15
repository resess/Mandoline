package mandoline.datadependence;


import mandoline.accesspath.AccessPath;
import mandoline.accesspath.AliasSet;
import mandoline.graph.ICDG;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementSet;
import mandoline.utils.Constants;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.toolkits.scalar.Pair;

public class BackwardStaticFieldAnalysis extends AliasAnalysis{
    private StatementInstance startUnit;
    private AccessPath startField;
    private StatementSet aliasPath;
    
    public BackwardStaticFieldAnalysis(ICDG icdg, StatementInstance startUnit, AccessPath ap, StatementSet aliasPath) {
        super(icdg);
        this.startUnit = startUnit;
        this.startField = ap;
        this.aliasPath = aliasPath;
    }

    public void run() {
        AliasSet taintSet = new AliasSet();
        boolean found = findBackwards(taintSet);
        if (!found) {
            findForward(taintSet);
        }
    }

    private boolean findForward(AliasSet taintSet) {
        boolean found = false;
        int pos = startUnit.getLineNo()+1;
        long end = (long) (pos + Constants.SEARCH_LENGTH);
        end = (end> icdg.getLastLine())? icdg.getLastLine(): end;
        while (pos < end) {
            StatementInstance si = icdg.mapNoUnits(pos);
            if (si == null || !si.getMethod().getName().equals("<clinit>")) {
                pos++;
                continue;
            }
 
            Pair<Boolean, Boolean> flags = new Pair<>();
            flags.setO1(false);
            flags.setO2(false);
            pos = matchFieldForward(pos, si, taintSet, flags);
            found = flags.getO1();
            boolean newPosFound = flags.getO2();
            if (found) {
                break;
            }
            if (!newPosFound) {
                pos++;
            }
        }
        return found;
    }

    private int matchFieldForward(int pos, StatementInstance si, AliasSet taintSet, Pair<Boolean, Boolean> flags) {
        int newPos = pos;
        Unit u = si.getUnit();
        if (u instanceof AssignStmt) {
            AssignStmt stmt = (AssignStmt) u;
            Value left = stmt.getLeftOp();
            if ((left instanceof FieldRef) && ((FieldRef) left).getUseBoxes().isEmpty()) {
                AccessPath var = new AccessPath(((FieldRef) left).getField().getDeclaringClass().getName(), ((FieldRef) left).getField().getType(), AccessPath.NOT_USED, si.getLineNo(), si);
                var.add(((FieldRef) left).getField().getName(), ((FieldRef) left).getField().getType(), si);
                var.setStaticField();
                if (startField.startsWith(var)) {
                    Value right = stmt.getRightOp();
                    AccessPath rightAp = new AccessPath(right.toString(), right.getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                    AccessPath newAp = new AccessPath(rightAp, si).add(startField.getAfter(var).getO1(), startField.getAfter(var).getO2(), si); 
                    taintSet.add(newAp);
                    aliasPath.add(si);
                    flags.setO1(true);
                } else {
                    newPos = gotToNextStaticField(pos);
                    flags.setO2(true);
                }
            }
        }
        return newPos;
    }

    private int gotToNextStaticField(int pos) {
        int prevPos = pos;
        try {
            pos = this.icdg.getNextUnitWithStaticField(pos);
        } catch (NullPointerException e) {
            pos = prevPos + 1;
        }
        return pos;
    }

    private boolean findBackwards(AliasSet taintSet) {
        int pos = startUnit.getLineNo()-1;
        boolean found = false;

        while (pos > 0) {
            StatementInstance si = icdg.mapNoUnits(pos);
            if (si == null) {
                pos--;
                continue;
            }

            Pair<Boolean, Boolean> flags = new Pair<>();
            flags.setO1(false);
            flags.setO2(false);
            pos = matchFieldBackward(pos, si, taintSet, flags);
            found = flags.getO1();
            boolean newPosFound = flags.getO2();
            if (found) {
                break;
            }
            if (!newPosFound) {
                pos--;
            }
        }
        return found;
    }

    private int matchFieldBackward(int pos, StatementInstance si, AliasSet taintSet, Pair<Boolean, Boolean> flags) {
        int newPos = pos;
        Unit u = si.getUnit();
        if (u instanceof AssignStmt) {
            AssignStmt stmt = (AssignStmt) u;
            Value left = stmt.getLeftOp();
            if ((left instanceof FieldRef) && ((FieldRef) left).getUseBoxes().isEmpty()) {
                AccessPath var = new AccessPath(((FieldRef) left).getField().getDeclaringClass().getName(), ((FieldRef) left).getField().getType(), AccessPath.NOT_USED, si.getLineNo(), si);
                var.add(((FieldRef) left).getField().getName(), ((FieldRef) left).getField().getType(), si);
                var.setStaticField();
                if (startField.startsWith(var)) {
                    Value right = stmt.getRightOp();
                    AccessPath rightAp = new AccessPath(right.toString(), right.getType(), si.getLineNo(), AccessPath.NOT_DEFINED, si);
                    AccessPath newAp = new AccessPath(rightAp, si).add(startField.getAfter(var).getO1(), startField.getAfter(var).getO2(), si); 
                    taintSet.add(newAp);
                    aliasPath.add(si);
                    flags.setO1(true);
                } else {
                    newPos = icdg.getPreviousUnitWithStaticField(pos);
                    flags.setO2(true);
                }
            }
        }
        return newPos;
    }
}
