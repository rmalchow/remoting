package com.mcg.tools.remoting.ex.server.api.services;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingTimeout;

@RemoteEndpoint(app="foo", name="bar")
public interface RandomService {

	public int getNumber();

	public void printSomething(String s);
	
	@RemotingTimeout(value = 2)
	public void waitShort(int x); 
	
	public void waitNormal(int x); 
	
	@RemotingTimeout(value = 20)
	public void waitLong(int x); 
	
}
