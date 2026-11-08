package com.gathin.tvhome;

/** A reconnect in the same boot must not take the screen from a playing app. */
final class BootSession {
    static boolean fresh(int previous, int current, long elapsed) {
        if (current < 0) return false;
        return previous < 0 ? elapsed < 120000 : current != previous;
    }
}
