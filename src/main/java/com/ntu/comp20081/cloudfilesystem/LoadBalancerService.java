package com.ntu.comp20081.cloudfilesystem;

import org.eclipse.paho.client.mqttv3.*;
import java.util.*;

public class LoadBalancerService {
    private static final String BROKER = "tcp://mqtt-broker:1883";
    private static final String REQ_TOPIC = "loadbalancer/request";
    private static final String RES_TOPIC = "loadbalancer/response";

    private int rrIndex = 0;
    private final String[] storageNodes = { "server1", "server2" };

    public static void main(String[] args) {
        try {
            System.out.println("LOG: Load Balancer Service starting in HEADLESS mode...");
            new LoadBalancerService().start();
        } catch (Exception e) {
            System.err.println("CRITICAL: Load Balancer failed: " + e.getMessage());
        }
    }

    public void start() throws MqttException {
        MqttClient client = new MqttClient(BROKER, "LBC_LB_SERVICE");
        client.connect();
        client.subscribe(REQ_TOPIC, (topic, msg) -> {
            String algo = new String(msg.getPayload());
            String selected = (algo.equals("ROUND_ROBIN")) ? doRR() : "server1,server2";
            client.publish(RES_TOPIC, new MqttMessage(selected.getBytes()));
            System.out.println("Nodes assigned: " + selected);
        });
        System.out.println("Load Balancer ready on MQTT.");
    }

    private String doRR() {
        String n1 = storageNodes[rrIndex % 2];
        rrIndex++;
        String n2 = storageNodes[rrIndex % 2];
        rrIndex++;
        return n1 + "," + n2;
    }
}