package com.bharatpe.lending.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.bharatpe.common.service.delayedqueue.DelayedMessagePublisher;

@Service
public class RedisKafkaService {
	
	@Autowired
	DelayedMessagePublisher delayedMessagePublisher;
	
	public void publish() {
		try {
			delayedMessagePublisher.publish("test", "pKey", "Payload", "hashKey", 10);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@KafkaListener(topics = "test")
	public void listen(String message) {
	    System.out.println("Received Messasge in topic test: " + message);
	}
	
}
