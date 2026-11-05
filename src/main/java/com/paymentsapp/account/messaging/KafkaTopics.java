package com.paymentsapp.account.messaging;

import com.paymentsapp.account.reservation.ReservationService;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    // Имя, число партиций и реплик — из конфигурации (messaging.account-reservations.*)
    @Bean
    NewTopic reservationsTopic(@Value("${messaging.account-reservations.topic}") String name,
                        @Value("${messaging.account-reservations.partitions}") int partitions,
                        @Value("${messaging.account-reservations.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
