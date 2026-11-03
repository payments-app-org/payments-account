package com.paymentsapp.account.outbox;

import com.paymentsapp.account.reservation.ReservationService;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    @Bean
    NewTopic reservationsTopic() {
        return TopicBuilder.name(ReservationService.TOPIC).partitions(3).replicas(1).build();
    }
}
