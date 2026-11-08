package com.veyra.tvmanager;

public final class ReleaseVersionTest {
    public static void main(String[] args) {
        check(ReleaseVersion.newer("v1.10.0","1.9"));
        check(ReleaseVersion.newer("1.9.1","1.9"));
        check(!ReleaseVersion.newer("v1.9.0","1.9"));
        check(!ReleaseVersion.newer("1.8.0","1.9"));
        check(ReleaseVersion.newer("2.0","1.99.99"));
        for(String value:new String[]{"v1.9-beta","../1.9","https://evil.example","1.9999999",""}) {
            try {ReleaseVersion.clean(value);throw new AssertionError("Accepted invalid version");}
            catch(IllegalArgumentException expected) {}
        }
        System.out.println("10 release-version checks passed");
    }
    private static void check(boolean value) {if(!value)throw new AssertionError();}
}
