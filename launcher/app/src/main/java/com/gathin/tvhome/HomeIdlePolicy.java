package com.gathin.tvhome;

final class HomeIdlePolicy {
    static final long DIM_MS=2*60*1000L, SLEEP_MS=15*60*1000L;
    static final int WAIT=0,DIM=1,SLEEP=2;
    static int action(long elapsed){return elapsed>=SLEEP_MS?SLEEP:elapsed>=DIM_MS?DIM:WAIT;}
}
