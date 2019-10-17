package com.mcg.tools.remoting.ex.server.impl.app;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.ex.server.api.services.PrintService;

@Service
@RemotingEndpoint(value=PrintService.class)
public class PrintServiceImpl implements PrintService {
	
	
	private static Log log = LogFactory.getLog(PrintService.class);

	@Override
	public void printSomething(String s) {
		log.info(s);
	}



}
