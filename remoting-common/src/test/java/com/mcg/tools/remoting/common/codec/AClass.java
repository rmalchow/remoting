package com.mcg.tools.remoting.common.codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.common.channel.util.HeaderHolder;

@RemotingEndpoint(value=AInterface.class)
public class AClass implements AInterface {
	
	@Override
	public List<String> echoList(List<String> in) {
		return in;
	}
	
	@Override
	public String combine(List<String> in, String... strings) {
		List<String> x = new ArrayList<>(in);
		x.addAll(Arrays.asList(strings));
		return StringUtils.join(x,",");
	}
	
	@Override
	public String combine(List<String> in, Integer... strings) {
		List<String> x = new ArrayList<>(in);
		for(Integer i : strings) {
			if(i!=null) {
				x.add(i+"");
			}
		}
		return StringUtils.join(x,",");
	}
	
	@Override
	public int add(int a, int b) {
		return a+b;
	}
	
	@Override
	public void set(int b) {
	}
	
	@Override
	public String getHeader(String n) {
		Map<String,String> m = HeaderHolder.headers();
		return m.get(n);
	}
	
	@Override
	public String getThreadName() {
		return Thread.currentThread().getName();
	}
	
	
}
