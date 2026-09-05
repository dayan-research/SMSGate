package com.zx.sms.connect.manager.cmpp;

import java.net.ServerSocket;
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Test;

import com.zx.sms.connect.manager.AbstractClientEndpointConnector;
import com.zx.sms.connect.manager.EndpointManager;

/**
 * 多 IP 的客户端以前只在 TCP 连不上时才切地址：连上了但登录被拒，下一秒重连还是同一个地址，
 * 列表里别的地址永远轮不到。现在连上而没建立会话就关闭的连接，会把下一次重连指向下一个地址。
 *
 * 服务端就是 SMSGate 自己：两个 host 都是 127.0.0.1，同一个服务端，用错密码让它拒绝登录，
 * 观察连接器的 nextHostIndex 从 0 走到 1。只 open() 一次、不开重连任务，结果是确定的。
 */
public class TestClientHostFailoverOnLoginFailure {

	private static final String USER = "test01";
	private static final String PASSWORD = "1qaz2wsx";

	private static int freePort() throws Exception {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static CMPPServerEndpointEntity server(int port) {
		CMPPServerEndpointEntity server = new CMPPServerEndpointEntity();
		server.setId("failover-server");
		server.setHost("127.0.0.1");
		server.setPort(port);
		server.setValid(true);
		server.setUseSSL(false);

		CMPPServerChildEndpointEntity child = new CMPPServerChildEndpointEntity();
		child.setId("failover-child");
		child.setChartset(Charset.forName("utf-8"));
		child.setGroupName("test");
		child.setUserName(USER);
		child.setPassword(PASSWORD);
		child.setValid(true);
		child.setVersion((short) 0x20);
		child.setMaxChannels((short) 4);
		child.setReSendFailMsg(false);
		server.addchild(child);
		return server;
	}

	private static CMPPClientEndpointEntity client(String id, int port, String password) {
		CMPPClientEndpointEntity client = new CMPPClientEndpointEntity();
		client.setId(id);
		client.setHost("127.0.0.1,127.0.0.1");
		client.setPort(port);
		client.setChartset(Charset.forName("utf-8"));
		client.setGroupName("test");
		client.setUserName(USER);
		client.setPassword(password);
		client.setMaxChannels((short) 1);
		client.setVersion((short) 0x20);
		client.setRetryWaitTimeSec((short) 30);
		client.setMaxRetryCnt((short) 1);
		client.setCloseWhenRetryFailed(false);
		client.setUseSSL(false);
		client.setWindow(16);
		client.setReSendFailMsg(false);
		return client;
	}

	private static void awaitNextHostIndex(AbstractClientEndpointConnector conn, int expected) throws Exception {
		long deadline = System.currentTimeMillis() + 10_000;
		while (conn.nextHostIndex() != expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		Assert.assertEquals("next host index", expected, conn.nextHostIndex());
	}

	@Test
	public void aRefusedLoginMovesTheNextAttemptToTheNextHost() throws Exception {
		EndpointManager.INS.removeAll();
		int port = freePort();
		CMPPServerEndpointEntity server = server(port);
		EndpointManager.INS.openEndpoint(server);
		try {
			CMPPClientEndpointEntity bad = client("failover-bad-client", port, "wrong-password");
			EndpointManager.INS.openEndpoint(bad);
			AbstractClientEndpointConnector badConn = (AbstractClientEndpointConnector) bad.getSingletonConnector();
			awaitNextHostIndex(badConn, 1);
			Assert.assertEquals("a refused login leaves no connection", 0, badConn.getConnectionNum());

			// 对照：登录成功就停在当前地址，后续重连不会无故换地址
			CMPPClientEndpointEntity good = client("failover-good-client", port, PASSWORD);
			EndpointManager.INS.openEndpoint(good);
			AbstractClientEndpointConnector goodConn = (AbstractClientEndpointConnector) good.getSingletonConnector();
			long deadline = System.currentTimeMillis() + 10_000;
			while (goodConn.getConnectionNum() < 1 && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}
			Assert.assertEquals(1, goodConn.getConnectionNum());
			Thread.sleep(200);
			Assert.assertEquals("a successful login stays on its host", 0, goodConn.nextHostIndex());
		} finally {
			EndpointManager.INS.close();
			EndpointManager.INS.removeAll();
		}
	}
}
