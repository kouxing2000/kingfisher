package com.kingfisher.proxy;

import org.junit.Test;

public class ProxyTest {

	//@Test
	public void testProxy() throws Exception {
		ProxyStarter.main(null);
		
		try {
			Thread.sleep(100000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
}
