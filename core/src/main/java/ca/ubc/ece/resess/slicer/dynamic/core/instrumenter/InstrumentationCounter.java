package ca.ubc.ece.resess.slicer.dynamic.core.instrumenter;

import java.util.concurrent.atomic.AtomicLong;

public class InstrumentationCounter {
    AtomicLong counter = new AtomicLong(0L);
    public long inc(){
        return counter.incrementAndGet();
    }
    @Override
    public String toString() {
        return String.valueOf(counter.get());
    }
}
