package com.mcg.tools.remoting.impl.amqp.spring;

import javax.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages={"com.mcg"})
public class TestApp {
	
	@PostConstruct
	public void init() {
	}

	public static void main(String[] args) {
	    SpringApplication.run(TestApp.class, args);
	}
	
	
	
}
