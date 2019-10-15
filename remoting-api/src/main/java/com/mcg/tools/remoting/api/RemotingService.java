package com.mcg.tools.remoting.api;

import com.mcg.tools.remoting.api.annotations.RemotingException;

public interface RemotingService {
	
	public void exportService(Object service) throws RemotingException;
	
	public void startClients();

}
