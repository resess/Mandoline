package mandoline.datadependence;

import mandoline.accesspath.AliasSet;
import mandoline.statements.StatementInstance;

public class AliasAnalysisResult {
    private StatementInstance nextStatement;
    private AliasSet backwardAliasSet;
    private AliasSet forwardAliasSet;

    AliasAnalysisResult(StatementInstance t, AliasSet vb, AliasSet vf) {
        this.nextStatement = t;
        this.backwardAliasSet = vb;
        this.forwardAliasSet = vf;
    }

    public StatementInstance getNextStatement() {
        return nextStatement;
    }

    public AliasSet getBackwardAliasSet() {
        return backwardAliasSet;
    }

    public AliasSet getForwardAliasSet() {
        return forwardAliasSet;
    }

    @Override
    public String toString() {
        return "{ " + nextStatement + ", " + backwardAliasSet + ", " + forwardAliasSet + "}";
    }
}
