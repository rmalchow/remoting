package com.mcg.tools.remoting.ex.client.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.ex.server.api.services.RandomService;

@Service
public class ClientService {

	@Autowired
	private RandomService randomService;
	
	@Scheduled(fixedDelay = 10000)
	public void run() {
		try {
			System.err.println(randomService.getNumber()+" -----  "+randomService.getClass().getCanonicalName());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
