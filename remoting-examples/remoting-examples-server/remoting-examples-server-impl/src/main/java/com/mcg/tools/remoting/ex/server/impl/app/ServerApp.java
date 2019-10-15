package com.mcg.tools.remoting.ex.server.impl.app;

import javax.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAutoConfiguration()
@ComponentScan(basePackages={"com.mcg"})
public class ServerApp {
	
	@PostConstruct
	public void init() {
	}

	public static void main(String[] args) {
	    SpringApplication.run(ServerApp.class, args);
	}
	
	
	
}
