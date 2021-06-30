package ca.ubc.ece.resess.slicer.dynamic.core.accesspath;

import java.util.HashSet;


public class AliasSet extends HashSet<AccessPath> {
    
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    
    public AliasSet(){
        super();
    }

    public AliasSet(AliasSet other) {
        this.addAll(other);
    }

    public AliasSet(AccessPath ap) {
        if ((ap != null && !ap.getPath().isEmpty()) && (!((ap.getPath().size()==1) && ap.getPath().get(0).equals("")))) {
            this.add(ap);
        }
    }

    public AliasSet subtract(AliasSet other) {
        AliasSet ret = new AliasSet();
        for (AccessPath ap: this) {
            if (!other.contains(ap)) {
                ret.add(ap);
            }
        }
        return ret;
    }

    @Override
    public boolean add(AccessPath ap) {
        if (ap != null && !ap.getPath().isEmpty() && !((ap.getPath().size()==1) && ap.getPath().get(0).equals(""))) {
            return super.add(ap);
        }
        return false;
    }

    public void update(AccessPath add, AccessPath remove) {
        this.remove(remove);
        this.add(add);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}