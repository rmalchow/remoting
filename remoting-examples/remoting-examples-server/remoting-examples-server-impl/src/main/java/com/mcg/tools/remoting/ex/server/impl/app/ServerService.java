package com.mcg.tools.remoting.ex.server.impl.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.ex.server.api.services.RandomService;

@Service
public class ServerService {

	@Autowired
	private RandomService randomService;
	
	@Scheduled(fixedDelay = 10000)
	public void run() {
		try {
			System.err.println(randomService.getNumber());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
