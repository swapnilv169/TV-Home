package com.gathin.tvhome;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** App library, not an inventory of every exported Fire OS system screen. */
final class LaunchableApps {
    static boolean showInLibrary(String pkg, String ownPackage, boolean system,
            boolean tvEntry, boolean tvBanner) {
        if (pkg == null || pkg.equals(ownPackage) || pkg.equals("com.amazon.tv.launcher")) return false;
        // Keep normal installed/sideloaded apps even without a TV-specific entry point.
        // System LAUNCHER entries alone also include profile pickers, diagnostics and
        // screensaver settings. Require a TV entry/banner instead of blacklisting them.
        if (!system || tvEntry || tvBanner) return true;
        // Legacy Fire TV consumer apps predate Leanback and omit TV banners. These
        // positive compatibility entries never add an app unless it is launchable.
        switch (pkg) {
            case "com.amazon.cloud9":
            case "com.amazon.venezia":
            case "com.amazon.bueller.photos":
            case "com.amazon.bueller.music":
            case "com.amazon.tahoe": return true;
            default: return false;
        }
    }

    static List<ResolveInfo> query(PackageManager pm, String ownPackage) {
        Map<String, ResolveInfo> apps = new LinkedHashMap<>();
        // Prefer the TV entry point when a package advertises both categories.
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo result : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                ActivityInfo activity = result.activityInfo;
                if (activity == null || !activity.exported || activity.applicationInfo == null) continue;
                String pkg = activity.packageName;
                ApplicationInfo app = activity.applicationInfo;
                boolean system = (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                if (!showInLibrary(pkg, ownPackage, system,
                        Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category),
                        activity.banner != 0 || app.banner != 0)) continue;
                if (activity.permission != null && !activity.permission.isEmpty()
                        && pm.checkPermission(activity.permission, ownPackage) != PackageManager.PERMISSION_GRANTED) continue;
                // PackageManager's default query excludes disabled components, while still
                // honoring explicit enable overrides for a manifest-disabled component.
                if (!apps.containsKey(pkg)) apps.put(pkg, result);
            }
        }
        List<ResolveInfo> result = new ArrayList<>(apps.values());
        result.sort((a, b) -> {
            int byName = a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            return byName != 0 ? byName : a.activityInfo.packageName.compareTo(b.activityInfo.packageName);
        });
        return result;
    }
}
