package com.mcg.tools.remoting.api.annotations;

public class RemotingException extends Exception {

	private static final long serialVersionUID = -8157891185823451019L;

	public RemotingException(String string, Exception e) {
		super(string,e);
	}

}
