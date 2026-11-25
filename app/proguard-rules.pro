# Release builds (and the dev benchmark build, which starts from release) drop verbose, debug and info calls to the
# system log, the app's and every library's (DESIGN 7.5 and 12.2, control 11). `return 0` drops the calls whose result
# is used, too. Warnings and errors stay. Crash reports don't use the system log: Crashlytics catches uncaught
# exceptions itself and uploads through its own transport.
-assumenosideeffects class android.util.Log {
    public static int v(...) return 0;
    public static int d(...) return 0;
    public static int i(...) return 0;
}
