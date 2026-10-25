package app.quanneng;

/** 解锁状态只存在于当前进程；离开应用或进程重启后失效。 */
final class PrivateSession {
    private static boolean unlocked;
    private static long generation;
    private static java.lang.ref.WeakReference<android.app.Activity> departure;
    private static long departureAt;
    static synchronized boolean isUnlocked(){return unlocked;}
    static synchronized long generation(){return generation;}
    static synchronized void lock(){unlocked=false;generation++;departure=null;}
    static synchronized void internalDeparture(android.app.Activity from){departure=new java.lang.ref.WeakReference<>(from);departureAt=android.os.SystemClock.uptimeMillis();}
    static synchronized boolean consumeInternalDeparture(android.app.Activity from){boolean allowed=departure!=null&&departure.get()==from&&android.os.SystemClock.uptimeMillis()-departureAt<3000;departure=null;return allowed;}
    static synchronized boolean unlock(long expected){if(expected!=generation)return false;unlocked=true;return true;}
}
