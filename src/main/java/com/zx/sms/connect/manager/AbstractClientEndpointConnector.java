package com.zx.sms.connect.manager;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zx.sms.common.GlobalConstance;
import com.zx.sms.common.NotSupportedException;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.SocketUtils;

public abstract class AbstractClientEndpointConnector extends AbstractEndpointConnector {

	private static final Logger logger = LoggerFactory.getLogger(AbstractClientEndpointConnector.class);
	private Bootstrap bootstrap = new Bootstrap();
	private SslContext sslCtx = null;
	/**
	 * 下一次 open() 从 host 列表的哪一个开始。TCP 连不上会在本次 open() 里顺延到下一个；
	 * 连上了但会话没建立（登录被拒、还没登录就被对方关掉）也要顺延，否则每秒一次的重连
	 * 会永远撞同一个拒绝登录的地址，而列表里别的地址明明可用。登录成功就停在当前地址。
	 */
	private volatile int nextHostIdx = 0;
	
	public AbstractClientEndpointConnector(EndpointEntity endpoint) {
		super(endpoint);
		if(endpoint.isUseSSL())  
			this.sslCtx = createSslCtx();
		bootstrap.group(EventLoopGroupFactory.INS.getWorker())
		.channel(EventLoopGroupFactory.selectChannelClass())
		.option(ChannelOption.TCP_NODELAY, true)
		//使用操作系统默认缓冲区大小
//		.option(ChannelOption.SO_RCVBUF, 16384)
//		.option(ChannelOption.SO_SNDBUF, 8192)
		.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)   
		.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,endpoint.getConnectionTimeOut())
//		.option(ChannelOption.RCVBUF_ALLOCATOR,new FixedRecvByteBufAllocator(1024))
		.handler(initPipeLine());
	}

	@Override
	public ChannelFuture open() throws Exception {
		String host = getEndpointEntity().getHost();
		String localhost = getEndpointEntity().getLocalhost();
		Integer localport = getEndpointEntity().getLocalport();
		SocketAddress localaddr = null;
		
		if(StringUtils.isNotBlank(localhost) && localport!=null){
			localaddr = SocketUtils.socketAddress(localhost, localport);
		}
		
		if(StringUtils.isBlank(host)){
			logger.error("remote host is blank");
			return null;
		}
		
		String[] hosts = host.split(",");
		return doConnect(hosts, nextHostIdx % hosts.length, 0, getEndpointEntity().getPort(), localaddr);
	}

	/** 下一次 open() 会先连 host 列表里的第几个。给测试和诊断看。 */
	public int nextHostIndex() {
		return nextHostIdx;
	}

	/**
	 * @param idx   本次连第几个 host
	 * @param tried 本次 open() 里已经连不上的 host 个数；每个 host 最多试一次，试完一轮就停
	 */
	private ChannelFuture doConnect(final String[] hosts,final int idx ,final int tried, final int port ,final SocketAddress localaddress){
		final int nextIdx = (idx + 1) % hosts.length;
		ChannelFuture future = bootstrap.connect(SocketUtils.socketAddress(hosts[idx],port),localaddress);
		
		future.addListener(new GenericFutureListener<ChannelFuture>(){

			@Override
			public void operationComplete(ChannelFuture f) throws Exception {
				if(!f.isSuccess()){
					if(tried+1 < hosts.length){
						logger.info("connect {} faild .retry connect to next host {}:{}",hosts[idx],hosts[nextIdx],port);
						doConnect(hosts, nextIdx, tried+1, port,localaddress);
					}else{
						logger.error("Connect to {}:{} failed. cause by {}.",getEndpointEntity().getHost(),port,f.cause().getMessage());
					}
					return;
				}
				// TCP 通了，先认这个地址；登录成功后它就是以后重连的首选
				nextHostIdx = idx;
				final Channel ch = f.channel();
				ch.closeFuture().addListener(new GenericFutureListener<ChannelFuture>() {
					@Override
					public void operationComplete(ChannelFuture closed) throws Exception {
						// channelActiveTime 只在 addChannel（即登录成功）时设置，且断开后不清除：
						// 它为空说明这条连接从连上到关闭都没建立过会话——登录被拒或被对方直接关掉。
						if (ch.attr(GlobalConstance.channelActiveTime).get() == null && hosts.length > 1) {
							nextHostIdx = nextIdx;
							logger.warn("connected {}:{} but no session was established; next attempt goes to {}",
									hosts[idx], port, hosts[nextIdx]);
						}
					}
				});
			}});
		
		return future;
	}
	
	protected ChannelInitializer<?> initPipeLine() {

		return new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel ch) throws Exception {
				ChannelPipeline pipeline = ch.pipeline();
				EndpointEntity entity = getEndpointEntity();
				if ( StringUtils.isNotBlank(entity.getProxy())) {
					String uriString = entity.getProxy();
					try {
						URI uri = URI.create(uriString);
						addProxyHandler(ch, uri);
					} catch (Exception ex) {
						logger.error("parse Proxy URI {} failed.", uriString, ex);
					}
				}

				if (entity.isUseSSL() && sslCtx != null) {
					logger.info("EndpointEntity {} Use SSL.",entity);
					pipeline.addLast(sslCtx.newHandler(ch.alloc(), entity.getHost(), entity.getPort()));
				}
				doinitPipeLine(pipeline);
			}
		};
	};
	
	protected abstract void doinitPipeLine(ChannelPipeline pipeline) ;
	
	protected void addProxyHandler(Channel ch, URI proxy) throws NotSupportedException {
		if (proxy == null)
			return;
		String scheme = proxy.getScheme();
		String userinfo = proxy.getUserInfo();
		String host = proxy.getHost();
		int port = proxy.getPort();
		String username = null;
		String pass = null;

		if (StringUtils.isNotBlank(userinfo)) {
			int idx = userinfo.indexOf(":");
			if (idx > 0) {
				username = userinfo.substring(0, idx);
				pass = userinfo.substring(idx + 1);
			}
		}

		ChannelPipeline pipeline = ch.pipeline();

		if ("HTTP".equalsIgnoreCase(scheme) || "HTTPS".equalsIgnoreCase(scheme) ) {
		
			if("HTTPS".equalsIgnoreCase(scheme)) {
				if(port < 0) port = 443;  // https default port
				SslContext proxySSLCtx = createSslCtx();
				pipeline.addLast(proxySSLCtx.newHandler(ch.alloc(), host, port));
			}
			if(port < 0) port = 80;  // http default port
			if (username == null) {
				pipeline.addLast(new HttpProxyHandler(new InetSocketAddress(host, port)));
			} else {
				pipeline.addLast(new HttpProxyHandler(new InetSocketAddress(host, port), username, pass));
			}
		} else if ("SOCKS5".equalsIgnoreCase(scheme)) {
			if(port < 0) port = 1080;  // socks default port
			if (username == null) {
				pipeline.addLast(new Socks5ProxyHandler(new InetSocketAddress(host, port)));
			} else {
				pipeline.addLast(new Socks5ProxyHandler(new InetSocketAddress(host, port), username, pass));
			}
		} else if ("SOCKS4".equalsIgnoreCase(scheme) || "SOCKS".equalsIgnoreCase(scheme)) {
			if(port < 0) port = 1080;  // socks default port
			if (username == null) {
				pipeline.addLast(new Socks4ProxyHandler(new InetSocketAddress(host, port)));
			} else {
				pipeline.addLast(new Socks4ProxyHandler(new InetSocketAddress(host, port), username));
			}
		} else {
			throw new NotSupportedException("not support proxy protocol " + scheme);
		}
	}
	
	protected SslContext createSslCtx() {
		EndpointEntity entity = getEndpointEntity();
		try{
			SslContextBuilder builder = SslContextBuilder.forClient();
			if (entity.isSslTrustAll()) {
				logger.warn("endpoint {} : sslTrustAll is on, any server certificate is accepted. Do not use this in production.", entity.getId());
				builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
			} else if (StringUtils.isNotBlank(entity.getSslTrustCertPath())) {
				builder.trustManager(new File(entity.getSslTrustCertPath()));
			}
			// 都没配就用JDK默认的信任链
			return builder.build();
		}catch(Exception ex){
			//宁可连不上，也不要退回明文
			throw new IllegalStateException("create client SslContext failed for endpoint " + entity.getId(), ex);
		}
	}
}
