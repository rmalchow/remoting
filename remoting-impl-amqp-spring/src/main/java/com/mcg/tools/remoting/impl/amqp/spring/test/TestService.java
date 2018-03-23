package com.mcg.tools.remoting.impl.amqp.spring.test;

import java.util.ArrayList;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class TestService {

	@Autowired
	private AInterface ai;
	
	@Scheduled(fixedDelay=1000, initialDelay=5000)
	public void test() {
		System.err.println(ai.combine(new ArrayList<>(), new Date().toString()));
	}

	
	
}
