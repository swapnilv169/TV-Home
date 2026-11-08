package com.gathin.tvhome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow framework Home signals. No app allowlist and no raw log retention. */
final class HomeSignal {
    static final int NONE=0, SYSTEM_HOME=1, APP_HOME=2, RESUMED_HOME=3;
    private static final String HOME="com\\.amazon\\.tv\\.launcher/(?:com\\.amazon\\.tv\\.launcher)?\\.ui\\.HomeActivity(?:_[A-Za-z0-9]+)?";
    private static final Pattern COMPONENT=Pattern.compile("\\bcmp="+HOME+"(?=[\\s},\\]])");
    private static final Pattern CATEGORY=Pattern.compile("android\\.intent\\.category\\.HOME(?=[ \\],])");
    private static final Pattern UID=Pattern.compile("\\bfrom uid (\\d+)(?=\\s|$)");
    private static final Pattern RESUME=Pattern.compile("^I/(?:am_resume_activity|wm_resume_activity)\\s*\\(\\s*\\d+\\):\\s*\\[0,\\d+,\\d+,"+HOME+"\\]\\s*$");
    static int kind(String line) {
        if(RESUME.matcher(line).matches())return RESUMED_HOME;
        if(!(line.startsWith("I/ActivityManager")||line.startsWith("I/ActivityTaskManager"))
                ||!line.contains("START u0 ")||!line.contains("act=android.intent.action.MAIN ")
                ||!CATEGORY.matcher(line).find()||!COMPONENT.matcher(line).find())return NONE;
        Matcher uid=UID.matcher(line);if(!uid.find())return NONE;
        try{int value=Integer.parseInt(uid.group(1));return value==1000||value==0?SYSTEM_HOME:APP_HOME;}
        catch(NumberFormatException ignored){return NONE;}
    }
    static boolean matches(String line){return kind(line)!=NONE;}
    static boolean shouldRedirect(int kind,boolean launching,boolean settingsGrace,boolean bypass){
        return kind!=NONE&&!launching&&(kind==SYSTEM_HOME||(!settingsGrace&&!bypass));
    }
}
