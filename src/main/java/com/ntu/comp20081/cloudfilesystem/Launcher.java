package com.ntu.comp20081.cloudfilesystem;

public class Launcher {

    public static void main(String[] args) {
        String headlessMode = System.getenv("HEADLESS_MODE");

        if (headlessMode != null && headlessMode.equalsIgnoreCase("true")) {
            startWorkerMode();
        } else {
            System.out.println("LOG: Starting Secure Authentication Gateway...");
            App.main(args);
        }
    }

    private static void startWorkerMode() {
        System.out.println("==========================================");
        System.out.println("INFRASTRUCTURE NODE: Headless Mode Active");
        System.out.println("==========================================");

        try {
            while (true) {
                Thread.sleep(10000);
                System.out.println("STATUS: Infrastructure Node Heartbeat - OK");
            }
        } catch (InterruptedException e) {
            System.err.println("CRITICAL: Infrastructure Node Interrupted.");
        }
    }
}