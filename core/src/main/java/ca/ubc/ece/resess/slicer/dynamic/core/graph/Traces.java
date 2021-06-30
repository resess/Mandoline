package ca.ubc.ece.resess.slicer.dynamic.core.graph;

public class Traces {
    public Long _lineNo;
    public Long _field;
    public String _class;
    public String _method;
    public String _type;
    public String _ins;
    public Long _tid;
    public String _unitString;

    @Override
    public String toString() {
        String line = _lineNo + ", " + _method + ", " + _ins + ", " + _tid;
        if (_field != null) {
            line += ", FIELD:" + _field;
        }
        return line;
    }
}

