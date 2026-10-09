package com.digaddfix.core;

/** Persist the checkpoint after each check; clock rollback never shortens a wait. */
public final class RetryGate {
    public static final long MAX_WAIT=15*60*1000;
    private RetryGate() {}
    public static long delay(int failures) {
        if(failures<5) return 0;
        return Math.min(MAX_WAIT,30000L<<Math.min(5,failures-5));
    }
    public static long remaining(long savedRemaining,long savedWall,long savedElapsed,int savedBoot,
                                 long wall,long elapsed,int boot) {
        long spent=boot>=0 && boot==savedBoot && elapsed>=savedElapsed?elapsed-savedElapsed:
            (wall>=savedWall?wall-savedWall:0);
        return Math.max(0,Math.min(MAX_WAIT,savedRemaining)-Math.min(MAX_WAIT,spent));
    }
}
