package com.mcg.tools.remoting.impl.amqp.spring.test;

import java.util.List;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;

@RemoteEndpoint(app="test",name="a")
public interface AInterface {

	String combine(List<String> in, Integer... strings);

	String combine(List<String> in, String... strings);

	List<String> echoList(List<String> in);

	int add(int a, int b);

	void set(int b);

	String getHeader(String n);

	String getThreadName();

}
