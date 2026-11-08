package com.gathin.tvhome;
public class BootSessionTest {
    public static void main(String[] args) {
        if (!BootSession.fresh(10,11,25000)) throw new AssertionError("new boot");
        if (BootSession.fresh(11,11,40000)) throw new AssertionError("same-boot service reconnect");
        if (!BootSession.fresh(-1,11,25000)) throw new AssertionError("first boot after upgrade");
        if (BootSession.fresh(-1,11,300000)) throw new AssertionError("daytime first install");
        if (BootSession.fresh(11,-1,25000)) throw new AssertionError("missing boot counter");
        if (BootSession.fresh(-1,11,120000)) throw new AssertionError("initial grace boundary");
        System.out.println("PASS: 6 boot/session cases");
    }
}
