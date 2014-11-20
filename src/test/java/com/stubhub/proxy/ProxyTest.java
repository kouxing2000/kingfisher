package com.stubhub.proxy;

import org.junit.Test;

public class ProxyTest {

	@Test
	public void testProxy() {
		StubhubHttpProxy.main(null);
		
		try {
			Thread.sleep(100000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
