package mandoline.slicer;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import mandoline.accesspath.AccessPath;
import mandoline.statements.StatementInstance;
import soot.jimple.ReturnVoidStmt;
import soot.toolkits.scalar.Pair;

public class DynamicSlice
        extends LinkedHashMap<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> {

    private static final long serialVersionUID = 1L;

    class OrderedSlice extends ArrayList<Pair<StatementInstance, AccessPath>> {

        private static final long serialVersionUID = 7286928364073066546L;

    }

    OrderedSlice order = new OrderedSlice();

    @Override
    public Pair<StatementInstance, AccessPath> put(Pair<StatementInstance, AccessPath> key,
            Pair<StatementInstance, AccessPath> value) {
        if (!(key.getO1().getUnit() instanceof ReturnVoidStmt)) {
            order.add(key);
            return super.put(key, value);
        }
        return null;
    }

    public Integer getOrder(Pair<StatementInstance, AccessPath> key) {
        return order.indexOf(key);
    }

    public DynamicSlice reorder() {
        DynamicSlice ordered = new DynamicSlice();
        List<Pair<StatementInstance, AccessPath>> orderedList = new ArrayList<>();
        orderedList.addAll(this.keySet());
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getLineNo() > lhs.getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getLineNo() < lhs.getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<StatementInstance, AccessPath> iup: orderedList) {
            if (!ordered.containsKey(iup)) {
                ordered.put(iup, this.get(iup));
            }
        }
        return ordered;
    }

    public DynamicSlice traceOrder() {
        DynamicSlice ordered = new DynamicSlice();
        List<Pair<StatementInstance, AccessPath>> orderedList = new ArrayList<>();
        orderedList.addAll(this.keySet());
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getLineNo() < lhs.getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getLineNo() > lhs.getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<StatementInstance, AccessPath> iup: orderedList) {
            if (!ordered.containsKey(iup)) {
                ordered.put(iup, this.get(iup));
            }
        }
        return ordered;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

