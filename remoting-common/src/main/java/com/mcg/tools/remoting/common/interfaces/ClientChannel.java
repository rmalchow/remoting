package com.mcg.tools.remoting.common.interfaces;

public interface ClientChannel {

	public byte[] invoke(byte[] in) throws Exception;
	
}
