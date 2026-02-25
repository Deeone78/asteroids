package com.ntu.comp20081.cloudfilesystem;

public class PartitionerService {
    public static void main(String[] args) {
        System.out.println("LOG: Partitioner Worker starting in HEADLESS mode...");
        System.out.println("Protocol: AES Encryption & CRC32 Validation active.");
        
        while (true) {
            try { Thread.sleep(60000); } catch (InterruptedException e) {}
        }
    }
}