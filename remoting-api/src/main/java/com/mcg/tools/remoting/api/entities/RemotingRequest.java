package com.mcg.tools.remoting.api.entities;

import java.util.HashMap;
import java.util.Map;

public class RemotingRequest {

	private Map<String,String> headers = new HashMap<>();
	private String methodName;
	private Object[] params = new Object[] {};
	
	public Map<String,String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String,String> headers) {
		this.headers = headers;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public Object[] getParams() {
		return params;
	}

	public void setParams(Object[] params) {
		this.params = params;
	}
	
	
}
