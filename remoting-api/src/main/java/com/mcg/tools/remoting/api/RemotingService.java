package com.mcg.tools.remoting.api;

import com.mcg.tools.remoting.api.annotations.RemotingException;

public interface RemotingService {
	
	public <T> T importService(Class<T> serviceInterface) throws RemotingException;

	public void exportService(Object service) throws RemotingException;

}
