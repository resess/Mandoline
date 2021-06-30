package ca.ubc.ece.resess.slicer.dynamic.core.statements;
import java.util.ArrayList;

public class StatementList extends ArrayList<StatementInstance> {

    private static final long serialVersionUID = 1L;
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Instruction-list: \n");
        for (StatementInstance iu: this) {
            sb.append("    ");
            sb.append(iu.toString());
            sb.append("\n");
        }
        return sb.toString();
    }
}