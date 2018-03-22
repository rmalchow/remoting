package com.mcg.tools.remoting.common.channel.util;

import java.util.HashMap;
import java.util.Map;

public class HeaderHolder {
	
	private static ThreadLocal<Map<String,String>> headers = new ThreadLocal<>();
	
	public static Map<String,String> headers() {
		Map<String,String> m = headers.get();
		if(m==null) {
			m = new HashMap<>();
		}
		headers.set(m);
		return m;
	}
	
	public static void headers(Map<String,String> m) {
		headers.set(m);
	}
	
	public static void clear() {
		headers.remove();
	}
	

}
