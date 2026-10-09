package com.digaddfix.core;

/** Uses elapsed time, never wall-clock time. A retry cannot extend the same overlay. */
public final class OverlayClock {
    public static final long DURATION_MS=5000;
    private String key="";
    private long deadline;
    public boolean start(String newKey,long now) {
        if(newKey.equals(key) && now<deadline) return false;
        key=newKey; deadline=now+DURATION_MS; return true;
    }
    public long remaining(long now) { return Math.max(0,deadline-now); }
    public int seconds(long now) { return (int)((remaining(now)+999)/1000); }
    public void clear() { key=""; deadline=0; }
}
