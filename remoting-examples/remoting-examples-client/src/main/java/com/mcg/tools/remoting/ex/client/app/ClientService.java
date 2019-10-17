package com.mcg.tools.remoting.ex.client.app;

import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.ex.server.api.services.PrintService;
import com.mcg.tools.remoting.ex.server.api.services.RandomService;

@Service
public class ClientService {
	
	private static Log log = LogFactory.getLog(ClientService.class);
	

	@Autowired
	private RandomService randomService;
	
	@Autowired
	private PrintService printService;
	
	@Scheduled(fixedDelay = 10000)
	public void run() {
		try {
			log.info("my random number is: "+randomService.getNumber());
			printService.printSomething("client service says: "+UUID.randomUUID());
			
			long s = 0;
			
			int count = 100000;
			
			long start = System.currentTimeMillis();
			
			for(int i=1;i<count;i++) {
				s = s + randomService.getNumber();
				if(i%1000 == 0) {
					log.info("average at "+i+": "+(s/i)+" / "+((double)(System.currentTimeMillis()-start)/(double)i)+" ms/req");
				}
			}
			
			
			
			try {
				log.info("short: wait 5 seconds ... (should fail)");
				randomService.waitShort(5);
			} catch (Exception e) {
				log.info("short: wait 5 seconds ... ERROR (as expected)");
			}
			try {
				log.info("short: wait one second (should be ok) ... ");
				randomService.waitShort(1);
				log.info("short: wait one second ... OK!");
			} catch (Exception e) {
				log.error("error (UNEXPECTED): "+e.getMessage()+" ("+e.getClass()+")",e);
			}
			try {
				log.info("long: wait 21 seconds ... (should fail)");
				randomService.waitLong(21);
			} catch (Exception e) {
				log.info("long: wait 21 seconds ... ERROR (as expected)");
			}
			try {
				log.info("long: wait 18 seconds (should be ok) ... ");
				randomService.waitLong(18);
				log.info("long: wait 18 seconds ... OK!");
			} catch (Exception e) {
				log.error("error (UNEXPECTED): "+e.getMessage()+" ("+e.getClass()+")",e);
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
