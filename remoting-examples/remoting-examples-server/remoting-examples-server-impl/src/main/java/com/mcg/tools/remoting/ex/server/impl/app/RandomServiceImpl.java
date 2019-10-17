package com.mcg.tools.remoting.ex.server.impl.app;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.ex.server.api.services.RandomService;

@Service
@RemotingEndpoint(value=RandomService.class)
public class RandomServiceImpl implements RandomService {

	private static Log log = LogFactory.getLog(RandomServiceImpl.class);
	
	@Override
	public int getNumber() {
		return (int)(Math.random()*100d);
	}

	private void wait(int x) {
		log.info("sleeping for "+x+" seconds ... ");
		try {
			Thread.sleep(x*1000);
		} catch (InterruptedException e) {
			log.error("error sleeping: ",e);
		}
		log.info("sleeping for "+x+" seconds ... done");
	}	
	
	public void waitShort(int x) {
		wait(x);
	}

	public void waitLong(int x) {
		wait(x);
	}

	public void waitNormal(int x) {
		wait(x);
	}

	
}
