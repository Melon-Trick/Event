package dev.vriege.eventfw.internal.diagnostic;

import jdk.jfr.Category;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

@Name("dev.vriege.eventfw.Dispatch")
@Label("Event framework dispatch")
@Category({"Event Framework"})
@StackTrace(false)
public final class DispatchRecordingEvent extends jdk.jfr.Event {
    public String eventType;
    public String channel;
    public String phase;
    public long sequence;
    public int invokedListeners;
    public int failureCount;
    public String failureType;

    @Timespan(Timespan.NANOSECONDS)
    public long elapsedNanos;
}
