package mandoline.utils;

import java.util.Map;
import java.util.Set;

import mandoline.accesspath.AccessPath;
import mandoline.accesspath.AliasSet;
import mandoline.graph.CalledChunk;
import mandoline.graph.CallerContext;
import mandoline.statements.StatementInstance;
import mandoline.statements.StatementMap;
import mandoline.statements.StatementSet;

import java.util.HashMap;


import soot.toolkits.scalar.Pair;

public class AnalysisCache{

    static Map<Pair<StatementInstance, Integer>, AliasSet> aliasAnalysisCache = new HashMap<>();
    static Map<Integer, Integer> callerCache = new HashMap<>();
    static Map<Integer, StatementMap> bwChunkCache = new HashMap<>();
    static Map<Integer, StatementMap> fwChunkCache = new HashMap<>();
    static Map<Pair<StatementInstance, AccessPath>, Set<CallerContext>> nextCallbackCache = new HashMap<>();
    static Map<StatementInstance, CallerContext> callerForwardChunk = new HashMap<>();
    static Map<StatementInstance, StatementInstance> previousCallbackCache = new HashMap<>();
    static Map<StatementInstance, CalledChunk> calledChunkCache = new HashMap<>();
    static Map<AccessPath, StatementSet> fastAliasPathCache = new HashMap<>();
    static Map<AccessPath, StatementSet> slowAliasPathCache = new HashMap<>();
    static Map<Pair<StatementMap, StatementInstance>, StatementMap> inTraceOrderChunkCache = new HashMap<>();

    private AnalysisCache() {
        throw new IllegalStateException("Utility class");
      }

    public static void reset() {
        aliasAnalysisCache = new HashMap<>();
        callerCache = new HashMap<>();
        bwChunkCache = new HashMap<>();
        fwChunkCache = new HashMap<>();
        nextCallbackCache = new HashMap<>();
        callerForwardChunk = new HashMap<>();
        previousCallbackCache = new HashMap<>();
        calledChunkCache = new HashMap<>();
        fastAliasPathCache = new HashMap<>();
        slowAliasPathCache = new HashMap<>();
        inTraceOrderChunkCache = new HashMap<>();
    }

    public static synchronized void putInInTraceOrderChunkCache(StatementMap k1, StatementInstance k2, StatementMap v) {
        inTraceOrderChunkCache.put(new Pair<>(k1, k2), v);
    }

    public static StatementMap getFromInTraceOrderChunkCache(StatementMap k1, StatementInstance k2){
        return inTraceOrderChunkCache.get(new Pair<>(k1, k2));
    }

    public static synchronized void putInCalledChunkCache(StatementInstance iu, CalledChunk cc){
        calledChunkCache.put(iu, cc);
    }

    public static CalledChunk getFromCalledChunkCache(StatementInstance iu){
        return calledChunkCache.get(iu);
    }

    public static StatementInstance getFromPreviousCallbackCache(StatementInstance k) {
        return previousCallbackCache.get(k);
    }

    public static synchronized void putInPreviousCallbackCache(StatementInstance k, StatementInstance v) {
        previousCallbackCache.put(k, v);
    }

    public static CallerContext getFromCallerForwardChunk(StatementInstance key){
        return callerForwardChunk.get(key);
    }

    public static synchronized void putInCallerForwardChunk(StatementInstance key, CallerContext value){
        callerForwardChunk.put(key, value);
    }

    public static Set<CallerContext> getFromNextCallbackCache(StatementInstance k, AccessPath ap) {
        return nextCallbackCache.get(new Pair<>(k, ap));
    }

    public static synchronized void putInNextCallbackCache(StatementInstance k, AccessPath ap, Set<CallerContext> v){
        nextCallbackCache.put(new Pair<>(k, ap), v);
    }

    public static Integer getFromCallerCache(int pos) {
        return callerCache.get(pos);
    }

    public static synchronized void putInCallerCache(int pos, int callerPos) {
        callerCache.put(pos, callerPos);
    }

    public static StatementMap getFromBwChunkCache(int pos) {
        return bwChunkCache.get(pos);
    }

    public static synchronized void putInBwChunkCache(int pos, StatementMap chunk) {
        bwChunkCache.put(pos, chunk);
    }

    public static StatementMap getFromFwChunkCache(int pos) {
        return fwChunkCache.get(pos);
    }

    public static synchronized void putInFwChunkCache(int pos, StatementMap chunk) {
        fwChunkCache.put(pos, chunk);
    }

    public static synchronized AliasSet filterByAnalysisCache(StatementInstance iu, AliasSet as, Integer direction){
        Pair<StatementInstance, Integer> key = new Pair<>(iu, direction);
        if (!aliasAnalysisCache.containsKey(key)) {
            aliasAnalysisCache.put(key, as);
            return as;
        }
        AliasSet removedSet = new AliasSet();
        AliasSet cachedSet = aliasAnalysisCache.get(key);
        for (AccessPath ap: as) {
            for (AccessPath cached: cachedSet) {
                // if (cached.equals(ap)) {
                // if (cached.isPrefixOf(ap) || cached.pathEquals(ap)) {
                if (cached.pathEquals(ap)) {
                    removedSet.add(ap);
                }
                // }
            }
        }
        AliasSet toAdd = as.subtract(removedSet);
        cachedSet.addAll(as);
        aliasAnalysisCache.put(key, cachedSet);
        return toAdd;
    }
}