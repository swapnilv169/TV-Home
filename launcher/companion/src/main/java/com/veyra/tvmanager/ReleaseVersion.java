package com.veyra.tvmanager;

/** Numeric stable-release comparison; no lexicographic 1.10/1.9 ordering. */
final class ReleaseVersion {
    static String clean(String value) {
        if(value==null||!value.matches("v?[0-9]{1,6}\\.[0-9]{1,6}(\\.[0-9]{1,6})?"))
            throw new IllegalArgumentException("Unrecognized release version");
        return value.startsWith("v")?value.substring(1):value;
    }
    static boolean newer(String latest,String installed) {
        String[] a=clean(latest).split("\\."),b=clean(installed).split("\\.");
        for(int i=0;i<3;i++) {
            int x=i<a.length?Integer.parseInt(a[i]):0,y=i<b.length?Integer.parseInt(b[i]):0;
            if(x!=y)return x>y;
        }
        return false;
    }
}
