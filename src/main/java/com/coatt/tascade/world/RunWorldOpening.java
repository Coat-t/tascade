package com.coatt.tascade.world;

// run worlds have a custom Nether seed, which 1.16 counts as experimental settings
public final class RunWorldOpening {
    private static boolean active;

    private RunWorldOpening() {}

    public static void run(Runnable open) {
        active = true;
        try {
            open.run();
        } finally {
            active = false;
        }
    }

    public static boolean active() {
        return active;
    }
}
