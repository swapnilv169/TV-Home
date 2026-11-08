package com.gathin.tvhome;
public class HomeIdlePolicyTest {
    public static void main(String[] args){
        long[] times={0,119999,120000,899999,900000,1800000};
        int[] expected={0,0,1,1,2,2};
        for(int i=0;i<times.length;i++)if(HomeIdlePolicy.action(times[i])!=expected[i])throw new AssertionError("idle boundary "+times[i]);
        if(HomeIdlePolicy.action(0)!=HomeIdlePolicy.WAIT)throw new AssertionError("activity resets deadline");
        System.out.println("PASS: idle dim/sleep boundaries and reset");
    }
}
