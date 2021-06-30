package ca.ubc.ece.resess.slicer.dynamic.core.graph;

import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import soot.Type;

public class CalledChunk {
    StatementMap chunk;
    int retLine;
    String retVariable;
    Type retVarType;
    StatementInstance retIu;

    public CalledChunk(){
        chunk = new StatementMap();
        retLine = -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ret variable: ");
        sb.append(retVariable);
        sb.append("\n");
        sb.append("ret type: ");
        sb.append(retVarType);
        sb.append("\n");
        sb.append("chunk: ");
        sb.append(chunk);
        return sb.toString();
    }

    public StatementMap getChunk() {
        return chunk;
    }

    public StatementInstance getRetIu() {
        return retIu;
    }

    public int getRetLine() {
        return retLine;
    }

    public Type getRetVarType() {
        return retVarType;
    }

    public String getRetVariable() {
        return retVariable;
    }

    public void setRetVariable(String retVariable) {
        this.retVariable = retVariable;
    }

    public void setRetVarType(Type retVarType) {
        this.retVarType = retVarType;
    }

    public void setChunk(StatementMap chunk) {
        this.chunk = chunk;
    }

    public void setRetLine(int retLine) {
        this.retLine = retLine;
    }

    public void setRetIu(StatementInstance retIu) {
        this.retIu = retIu;
    }
}