package com.mcg.tools.remoting.ex.client.app;

import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.ex.server.api.services.RandomService;

@Service
public class ClientService {
	
	private static Log log = LogFactory.getLog(ClientService.class);
	

	@Autowired
	private RandomService randomService;
	
	@Scheduled(fixedDelay = 10000)
	public void run() {
		try {
			log.info("my random number is: "+randomService.getNumber());
			randomService.printSomething("client service says: "+UUID.randomUUID());
			
			try {
				log.info("short: wait three seconds ... (should fail)");
				randomService.waitShort(3);
			} catch (Exception e) {
				log.info("short: wait three seconds ... ERROR (as expected)");
			}
			try {
				log.info("short: wait one second (should be ok) ... ");
				randomService.waitShort(1);
				log.info("short: wait one second ... OK!");
			} catch (Exception e) {
				log.error("error (UNEXPECTED): "+e.getMessage()+" ("+e.getClass()+")");
			}
			try {
				log.info("long: wait 21 seconds ... (should fail)");
				randomService.waitLong(21);
			} catch (Exception e) {
				log.info("long: wait 21 seconds ... ERROR (as expected)");
			}
			try {
				log.info("long: wait 19 seconds (should be ok) ... ");
				randomService.waitLong(19);
				log.info("long: wait 19 seconds ... OK!");
			} catch (Exception e) {
				log.error("error (UNEXPECTED): "+e.getMessage()+" ("+e.getClass()+")");
			}
			try {
				log.info("long: wait 9 seconds (depends on global setting) ... ");
				randomService.waitLong(9);
				log.info("long: wait 9 seconds ... NO TIMEOUT");
			} catch (Exception e) {
				log.info("long: wait 9 seconds ... TIMEOUT");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
