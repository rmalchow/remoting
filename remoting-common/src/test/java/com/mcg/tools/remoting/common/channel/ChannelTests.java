package com.mcg.tools.remoting.common.channel;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.common.channel.util.TestInterceptorA;
import com.mcg.tools.remoting.common.channel.util.TestInterceptorB;
import com.mcg.tools.remoting.common.codec.AClass;
import com.mcg.tools.remoting.common.codec.AInterface;

public class ChannelTests {

	@Test
	public void testLocalChannel() throws RemotingException {
		LocalChannelRemotingService rs = new LocalChannelRemotingService();
		
		AClass a = new AClass();
		rs.exportService(a);
		
		AInterface x = rs.importService(AInterface.class);
		
		
		{
			String s = x.combine(Collections.singletonList("a"), "b", "c");
			Assert.assertEquals("a,b,c", s);
		}
		{
			String s = x.combine(Collections.singletonList("a"), 1, 2, 3);
			Assert.assertEquals("a,1,2,3", s);
		}
		{
			int m = x.add(4, 5);
			Assert.assertEquals(9, m);
		}
		{
			String s = x.combine(Collections.singletonList("a"), null, "1");
			Assert.assertEquals("a,1", s);
		}
		{
			x.set(0);
		}
		
		rs.getInterceptors().clear();
		
		rs.getInterceptors().add(new TestInterceptorA());
		rs.getInterceptors().add(new TestInterceptorB());
		
		{
			String s = x.getHeader("A-Value");
			Assert.assertEquals("AAAA", s);
		}
		{
			String s = x.getThreadName();
			Assert.assertNotEquals(Thread.currentThread().getName(), x.getThreadName());
		}

		
		
		
	}
	
}
