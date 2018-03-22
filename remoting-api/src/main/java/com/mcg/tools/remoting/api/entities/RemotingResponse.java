package com.mcg.tools.remoting.api.entities;

import java.util.HashMap;
import java.util.Map;

public class RemotingResponse {

	private Map<String,String> headers = new HashMap<>();
	private boolean success = false;
	private Object returnValue;
	
	public Map<String,String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String,String> headers) {
		this.headers = headers;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public Object getReturnValue() {
		return returnValue;
	}

	public void setReturnValue(Object returnValue) {
		this.returnValue = returnValue;
	}
	
	
}
