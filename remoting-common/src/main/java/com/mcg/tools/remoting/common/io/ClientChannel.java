package com.mcg.tools.remoting.common.io;

public interface ClientChannel {

	public byte[] invoke(byte[] in) throws Exception;
	
}
