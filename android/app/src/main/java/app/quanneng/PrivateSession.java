package app.quanneng;

/** 解锁状态只存在于当前进程；离开应用或进程重启后失效。 */
final class PrivateSession {
    private static boolean unlocked;
    private static long generation;
    static synchronized boolean isUnlocked(){return unlocked;}
    static synchronized long generation(){return generation;}
    static synchronized void lock(){unlocked=false;generation++;}
    static synchronized boolean unlock(long expected){if(expected!=generation)return false;unlocked=true;return true;}
}
