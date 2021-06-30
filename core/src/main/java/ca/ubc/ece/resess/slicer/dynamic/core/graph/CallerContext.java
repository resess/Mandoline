package ca.ubc.ece.resess.slicer.dynamic.core.graph;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;

public class CallerContext {
    StatementInstance caller;
    StatementMap callerChunk;
    AliasSet aliasedArgs;
    boolean isForwardChunk = false;
    boolean isCallback = false;
    @Override
    public String toString() {
        return  "Caller: " + caller + "\n" +
                "Caller body: " + callerChunk + "\n" + 
                "Aliased args: " + aliasedArgs + "\n" +
                "IsForwardChunk: " + isForwardChunk + "\n" +
                "IsCallback: " + isCallback;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CallerContext)) {
            return false;
        }
        CallerContext other = (CallerContext) obj;
        return this.caller.equals(other.caller)
            && this.callerChunk.equals(other.callerChunk)
            && this.aliasedArgs.equals(other.aliasedArgs)
            && this.isForwardChunk == other.isForwardChunk
            && this.isCallback == other.isCallback;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + caller.hashCode();
        result = 31 * result + callerChunk.hashCode();
        result = 31 * result + aliasedArgs.hashCode();
        result = 31 * result + ((isForwardChunk)? 1:0);
        result = 31 * result + ((isCallback)? 1:0);
        return result;
    }

    public void setForwardChunk(boolean isForwardChunk) {
        this.isForwardChunk = isForwardChunk;
    }
    public boolean isForwardChunk() {
        return isForwardChunk;
    }
    public void setCallback(boolean isCallback) {
        this.isCallback = isCallback;
    }
    public boolean isCallback() {
        return isCallback;
    }

    public StatementInstance getCaller() {
        return caller;
    }

    public StatementMap getCallerChunk() {
        return callerChunk;
    }

    public AliasSet getAliasedArgs() {
        return aliasedArgs;
    }

    public void setCaller(StatementInstance caller) {
        this.caller = caller;
    }

    public void setCallerChunk(StatementMap callerChunk) {
        this.callerChunk = callerChunk;
    }

    public void setAliasedArgs(AliasSet aliasedArgs) {
        this.aliasedArgs = aliasedArgs;
    }
}