package com.watermonitor.ingest.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttClientConfig {

    @Bean(destroyMethod = "close")
    public MqttClient mqttClient(
            @Value("${wtm.mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
            @Value("${wtm.mqtt.client-id:ingest-gateway}") String clientId) throws MqttException {

        // Connection is deliberately owned by the inbound adapter. A
        // persistent broker session may deliver queued records immediately
        // after CONNECT, so the message callback must exist first.
        return new MqttClient(brokerUrl, clientId, new MemoryPersistence());
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions(
            @Value("${wtm.mqtt.clean-session:false}") boolean cleanSession) {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(cleanSession);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(30);
        opts.setMaxInflight(100);

        return opts;
    }
}
