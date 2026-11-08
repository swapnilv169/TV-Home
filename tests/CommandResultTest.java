package com.veyra.tvmanager;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Execute the real generated batch, not a hand-written approximation of it. */
public final class CommandResultTest {
    static String shell(String script)throws Exception{
        String executable=System.getenv("TVHOME_TEST_SHELL");
        if(executable==null)executable=System.getProperty("os.name").startsWith("Windows")
                ?"C:/Program Files/Git/bin/bash.exe":"/bin/sh";
        Process p=new ProcessBuilder(executable,"-c",script).redirectErrorStream(true).start();
        String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        p.waitFor();return output;
    }
    static void check(boolean ok){if(!ok)throw new AssertionError("Command confirmation regression");}
    public static void main(String[] args)throws Exception{
        String stub="dumpsys() { printf '  User 0: installed=true enabled=3\\n'; }; ";
        String batch=stub+TvController.packageStateCommand(Arrays.asList("com.example.one","com.example.two"));
        check(!shell(batch+"; printf '\\n__TVSTUDIO_RC:%s\\n' $?").contains("__TVSTUDIO_RC:0"));
        String result=shell(TvController.commandWithResult(batch));
        check(result.contains("__TVSTUDIO_PACKAGE:com.example.one"));
        check(result.contains("__TVSTUDIO_PACKAGE:com.example.two"));
        check(result.trim().endsWith("__TVSTUDIO_RC:0"));
        check(result.split("enabled=3",-1).length==3);
        for(String command:new String[]{"printf hello","printf hello; ","printf hello\n"})
            check(shell(TvController.commandWithResult(command)).trim().endsWith("__TVSTUDIO_RC:0"));
        check(shell(TvController.commandWithResult("false; ")).trim().endsWith("__TVSTUDIO_RC:1"));
        System.out.println("PASS: 1.9 malformed batch reproduced; connection batch and exit-status confirmation pass.");
    }
}
