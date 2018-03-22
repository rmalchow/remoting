package com.mcg.tools.remoting.common.codec;

import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

public class CodecTests {
	
	@Test
	public void testRemotingCodec() {
		AClass a = new AClass();
		ProxyCodec<AInterface> c =  new ProxyCodec<AInterface>(a, AInterface.class);  
		AInterface x = c.getProxy();
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
			String s = x.combine(Collections.singletonList("a"), null, "1A");
			
			String cs =  StringUtils.join( new String[] { "a" , null, "1A" } , "," );
			
			Assert.assertEquals(cs, s);
		}
		{
			x.set(0);
		}
	}
	

}
