package com.zx.sms.connect.manager;

import org.junit.Assert;
import org.junit.Test;

import com.zx.sms.connect.manager.cmpp.CMPPServerEndpointEntity;

/**
 * useSSL 的服务端缺证书配置时，要在 open 端点（构造连接器）时就以一句说清缺什么的错误失败，
 * 而不是临时自签名证书（JDK 21+ 上生成不出来，报的是内部错误）或者明文。
 */
public class TestServerSslConfigIsRequired {

	private static CMPPServerEndpointEntity sslServer(String id) {
		CMPPServerEndpointEntity server = new CMPPServerEndpointEntity();
		server.setId(id);
		server.setHost("127.0.0.1");
		server.setPort(0);
		server.setValid(true);
		server.setUseSSL(true);
		return server;
	}

	@Test
	public void missingPathsNameTheMissingFields() {
		CMPPServerEndpointEntity server = sslServer("ssl-no-paths");
		try {
			server.getSingletonConnector();
			Assert.fail("expected the connector to refuse to build");
		} catch (IllegalStateException expected) {
			Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("sslCertPath/sslKeyPath are not both set"));
			Assert.assertNull("a configuration error, not an internal one", expected.getCause());
		}
	}

	@Test
	public void aMissingFileNamesTheFile() {
		CMPPServerEndpointEntity server = sslServer("ssl-bad-file");
		server.setSslCertPath("/nonexistent/server-cert.pem");
		server.setSslKeyPath(TestConstants.sslKeyPath);
		try {
			server.getSingletonConnector();
			Assert.fail("expected the connector to refuse to build");
		} catch (IllegalStateException expected) {
			Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("sslCertPath does not exist"));
			Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("/nonexistent/server-cert.pem"));
		}
	}

	@Test
	public void bothFilesPresentBuilds() {
		CMPPServerEndpointEntity server = sslServer("ssl-ok");
		server.setSslCertPath(TestConstants.sslCertPath);
		server.setSslKeyPath(TestConstants.sslKeyPath);
		Assert.assertNotNull(server.getSingletonConnector());
	}
}
