package com.mcg.tools.remoting.api.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemotingResponse {

	private Map<String,String> headers = new HashMap<>();
	private boolean success = false;
	private Object returnValue;
	private List<String> stackTrace = new ArrayList<String>();
	
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

	public List<String> getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(List<String> stackTrace) {
		this.stackTrace = stackTrace;
	}
	
	public void exception(Throwable t) {
		if(t==null) return;
		stackTrace.add("caused by --- "+t.getClass()+": "+t.getMessage());
		for(StackTraceElement ste : t.getStackTrace()) {
			stackTrace.add(String.format("error: \t %s.%s (%s %s) ",ste.getClassName(),ste.getMethodName(),ste.getFileName(),ste.getLineNumber()));
		}
		exception(t.getCause());
	}
	
	
}
