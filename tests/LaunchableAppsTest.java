package com.gathin.tvhome;

public final class LaunchableAppsTest {
    private static int cases;
    private static void check(String pkg, boolean system, boolean tv, boolean banner, boolean expected) {
        boolean actual = LaunchableApps.showInLibrary(pkg, "com.gathin.tvhome", system, tv, banner);
        if (actual != expected) throw new AssertionError("Library policy: " + pkg);
        cases++;
    }
    public static void main(String[] args) {
        check("org.example.sideloaded", false, false, false, true);
        check("org.example.systemtv", true, true, false, true);
        check("org.example.bannertv", true, false, true, true);
        check("org.example.systemhelper", true, false, false, false);
        for (String pkg : new String[]{"com.amazon.gamehub", "com.amazon.ftv.profilepicker",
                "com.amazon.ftv.screensaver", "com.amazon.ssm", "com.amazon.vizzini.ftvcds",
                "com.android.providers.downloads.ui", "com.amazon.hedwig"})
            check(pkg, true, false, false, false);
        for (String pkg : new String[]{"com.amazon.cloud9", "com.amazon.venezia",
                "com.amazon.bueller.photos", "com.amazon.bueller.music", "com.amazon.tahoe"})
            check(pkg, true, false, false, true);
        check("com.amazon.minitv.android.app", true, false, true, true);
        check("com.amazon.tv.launcher", true, true, true, false);
        check("com.gathin.tvhome", false, true, true, false);
        check(null, false, true, true, false);
        System.out.println("PASS: " + cases + " app-library cases");
    }
}
