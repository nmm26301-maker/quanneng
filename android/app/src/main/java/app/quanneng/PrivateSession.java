package app.quanneng;

/** 解锁状态只存在于当前进程；后台 5 分钟、锁屏或手动锁定后失效。 */
final class PrivateSession {
    private static boolean unlocked;
    private static long generation;
    private static long backgroundUntil;
    static final long GRACE_MILLIS=5*60*1000L;
    static synchronized boolean isUnlocked(){if(unlocked&&backgroundUntil>0&&android.os.SystemClock.elapsedRealtime()>=backgroundUntil)lock();return unlocked;}
    static synchronized void background(){if(unlocked&&backgroundUntil==0)backgroundUntil=android.os.SystemClock.elapsedRealtime()+GRACE_MILLIS;}
    static synchronized void foreground(){isUnlocked();backgroundUntil=0;}
    static synchronized long generation(){return generation;}
    static synchronized void lock(){unlocked=false;backgroundUntil=0;generation++;}
    static synchronized boolean unlock(long expected){if(expected!=generation)return false;unlocked=true;backgroundUntil=0;return true;}
}
