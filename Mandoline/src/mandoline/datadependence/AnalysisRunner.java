package mandoline.datadependence;

import java.util.Deque;



public class AnalysisRunner implements Runnable {

    private AliasAnalysis analysis;
    private Deque<AliasAnalysis> analysisStack;

    public AnalysisRunner(AliasAnalysis analysis, Deque<AliasAnalysis> analysisStack) {
        this.analysis = analysis;
        this.analysisStack = analysisStack;
    }

    @Override
    public void run() {
        if (this.analysis instanceof ForwardAliasAnalysis) {
            for (AliasAnalysis aliasAnalysis: ((ForwardAliasAnalysis) this.analysis).run()) {
                if (aliasAnalysis != null) {
                    analysisStack.add(aliasAnalysis);
                }
            }
        } else if (this.analysis instanceof BackwardAliasAnalysis) {
            for (AliasAnalysis aliasAnalysis: ((BackwardAliasAnalysis) this.analysis).run()) {
                if (aliasAnalysis != null) {
                    analysisStack.add(aliasAnalysis);
                }
            }
        } else {
            throw new Error("Invalid analysis type!");
        }
    }
}
