package com.bharatpe.lending.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
public class ReceiverConfig {

  @Value("${kafka.servers:localhost:9092}")
  private String kafkaServers;

  @Value("${bootstrap.servers:localhost:9092}")
  private String confluentKafkaServers;

  @Value("${consumer.confluent.connection:false}")
  private boolean consumerConfluentConnection;

  @Value("${cluster.api.key:test}")
  private String clusterApiKey;

  @Value("${cluster.api.secret:test}")
  private String clusterApiSecret;

  @Bean
  public Map<String, Object> confluentConsumerConfigs() {
    Map<String, Object> props = new HashMap<>();
    // list of host:port pairs used for establishing the initial connections to the Kafka cluster
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaServers);
    if (consumerConfluentConnection) {
      log.info("Updating confluent configuration");
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
              confluentKafkaServers);
      props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
      props.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + clusterApiKey + "\" password=\"" + clusterApiSecret + "\";");
      props.put("sasl.mechanism", "PLAIN");
      props.put("acks", "all");
      props.put("client.dns.lookup", "use_all_dns_ips");
      props.put("session.timeout.ms", 45000);
    }

    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    // allows a pool of processes to divide the work of consuming and processing records
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "groupId");
    // automatically reset the offset to the earliest offset
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 5000);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 2);
    return props;
  }

  @Bean
  public ConsumerFactory<String, String> confluentConsumerFactory() {
    return new DefaultKafkaConsumerFactory<>(confluentConsumerConfigs());
  }

  @Bean
  @Primary
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(confluentConsumerFactory());

    return factory;
  }

  @Bean(name = "ConfluentKafkaListenerContainer")
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> confluentKafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(confluentConsumerFactory());

    return factory;
  }
}