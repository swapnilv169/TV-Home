package com.veyra.tvmanager;
import java.io.IOException;
import java.util.Set;
public final class ProcessSnapshotTest {
    private static int cases;
    private static void check(boolean value){if(!value)throw new AssertionError("Process case "+cases);cases++;}
    public static void main(String[] args)throws Exception{
        Set<String> names=ProcessSnapshot.names("NAME\ncom.example.player\ncom.example.player:worker\ncom.example.player2\n");
        check(ProcessSnapshot.contains(names,"com.example.player"));
        check(!ProcessSnapshot.contains(names,"com.example.play"));
        check(ProcessSnapshot.contains(ProcessSnapshot.names("NAME\ncom.example.player:worker\n"),"com.example.player"));
        check(!ProcessSnapshot.contains(ProcessSnapshot.names("NAME\ncom.example.player2\n"),"com.example.player"));
        check(ProcessSnapshot.contains(ProcessSnapshot.names("USER PID PPID NAME\nu0_a1 123 1 com.example.player\n"),"com.example.player"));
        check(ProcessSnapshot.contains(ProcessSnapshot.names("USER PID CMD\nu0_a1 123 com.example.player\n"),"com.example.player"));
        check(ProcessSnapshot.contains(ProcessSnapshot.names("PID CMDLINE\n123 com.example.player\n"),"com.example.player"));
        for(String bad:new String[]{"", "permission denied", "PID COMM\n12 com.example.player", "NAME\n"}){
            boolean rejected=false;try{ProcessSnapshot.names(bad);}catch(IOException e){rejected=true;}check(rejected);
        }
        System.out.println("PASS: "+cases+" process snapshot cases");
    }
}
