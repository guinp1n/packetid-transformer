package com.hivemq.extensions.kafka.customizations.packetid;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;
import com.hivemq.extensions.kafka.api.builders.KafkaRecordBuilder;
import com.hivemq.extensions.kafka.api.model.KafkaCluster;
import com.hivemq.extensions.kafka.api.transformers.mqtttokafka.MqttToKafkaInitInput;
import com.hivemq.extensions.kafka.api.transformers.mqtttokafka.MqttToKafkaInput;
import com.hivemq.extensions.kafka.api.transformers.mqtttokafka.MqttToKafkaOutput;
import com.hivemq.extensions.kafka.api.transformers.mqtttokafka.MqttToKafkaTransformer;
import com.hivemq.extensions.kafka.api.services.KafkaTopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CustomMqttToKafkaTransformer implements MqttToKafkaTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(CustomMqttToKafkaTransformer.class);

    @Override
    public void transformMqttToKafka(@NotNull final MqttToKafkaInput mqttToKafkaInput, @NotNull final MqttToKafkaOutput mqttToKafkaOutput) {
        final PublishPacket publishPacket = mqttToKafkaInput.getPublishPacket();
        final String topic = publishPacket.getTopic();

        final String kafkaTopic;
        if (topic.contains("0x")) {
            String[] topicLevels = topic.split("/");
            for (String level : topicLevels) {
                if (level.contains("0x")) {
                    if (level.contains("0x03014")) {
                        kafkaTopic = "azure-packml-fct-datapool-0";
                        LOG.info("Mapping MQTT topic '{}' to Kafka topic '{}' (datapool)", topic, kafkaTopic);
                    } else {
                        kafkaTopic = "azure-packml-fct-" + level + "-0";
                        LOG.info("Mapping MQTT topic '{}' to Kafka topic '{}'", topic, kafkaTopic);
                    }
                    
                    // Create the Kafka topic if it does not exist
                    final KafkaTopicService kafkaTopicService = mqttToKafkaInput.getKafkaTopicService();
                    final KafkaTopicService.KafkaTopicState kafkaTopicState = kafkaTopicService.getKafkaTopicState(kafkaTopic);
                    if (kafkaTopicState != KafkaTopicService.KafkaTopicState.EXISTS) {
                        LOG.info("Topic '{}' does not exist, creating topic", kafkaTopic);
                        kafkaTopicService.createKafkaTopic(kafkaTopic);
                        KafkaTopicService.KafkaTopicState checkAgainState = kafkaTopicService.getKafkaTopicState(kafkaTopic);
                        if (checkAgainState == KafkaTopicService.KafkaTopicState.MISSING) {
                            LOG.info("The Kafka topic '{}' does not exist on the Kafka cluster.", kafkaTopic);
                        } else {
                            LOG.info("Successfully created the Kafka topic '{}' on the Kafka cluster. State: {}", kafkaTopic, checkAgainState);
                        }
                    }

                    // Get a new Kafka record builder
                    final KafkaRecordBuilder recordBuilder = mqttToKafkaOutput.newKafkaRecordBuilder();
                    // Set the Kafka topic
                    recordBuilder.topic(kafkaTopic);
                    // Set the Kafka key
                    recordBuilder.key(topic); // Using 'topic' from PublishPacket
                    // Set the Kafka Headers
                    recordBuilder.header("mqtt.message.id", publishPacket.getUserProperties().getFirst("mqtt.message.id").orElse("0"));
                    recordBuilder.header("mqtt.qos", publishPacket.getUserProperties().getFirst("mqtt.qos").orElse("0")); // Using QoS from PublishPacket
                    recordBuilder.header("mqtt.retained", publishPacket.getUserProperties().getFirst("mqtt.retained").orElse("false"));
                    recordBuilder.header("mqtt.duplicate", publishPacket.getUserProperties().getFirst("mqtt.duplicate").orElse("false"));
                    
                    publishPacket.getPayload().ifPresent(recordBuilder::value);

                    // Build and set the Kafka record that the HiveMQ Enterprise Extension for Kafka will push
                    mqttToKafkaOutput.setKafkaRecords(List.of(recordBuilder.build()));

                    return;
                }
            }
        } else {
            LOG.info("Skipping MQTT topic '{}' as it does not contain '0x'", topic);
        }
    }

    @Override
    public void init(@NotNull final MqttToKafkaInitInput input) {
        final KafkaCluster kafkaCluster = input.getKafkaCluster();

        LOG.info(
                "VELUX-Transformer for Kafka cluster '{}' with boot strap servers '{}' initialized.",
                kafkaCluster.getId(),
                kafkaCluster.getBootstrapServers());

    }
}
