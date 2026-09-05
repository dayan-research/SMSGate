package com.zx.sms.connect.manager;

import java.io.File;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zx.sms.handler.HAProxyMessageHandler;
import com.zx.sms.session.AbstractSessionStateManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * @author Lihuanghe(18852780@qq.com)
 */
public abstract class AbstractServerEndpointConnector extends AbstractEndpointConnector {
	private static final Logger logger = LoggerFactory.getLogger(AbstractServerEndpointConnector.class);
	private ServerBootstrap bootstrap = new ServerBootstrap();
	private Channel acceptorChannel = null;
	private final DefaultChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	private SslContext sslCtx = null;
	
	public AbstractServerEndpointConnector(EndpointEntity e) {
		super(e);
		this.sslCtx = createSslCtx();
		bootstrap.group(EventLoopGroupFactory.INS.getBoss(), EventLoopGroupFactory.INS.getWorker())
				.channel(EventLoopGroupFactory.selectServerChannelClass())
				.option(ChannelOption.SO_BACKLOG, 100)
				.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.handler(new LoggingHandler(LogLevel.DEBUG))
				.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				//使用操作系统默认缓冲区大小
//				.childOption(ChannelOption.SO_RCVBUF, 16384)
//				.childOption(ChannelOption.SO_SNDBUF, 8192)
//				.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(1024))
				.childOption(ChannelOption.TCP_NODELAY, true)
				.childHandler(initPipeLine());
	}

	@Override
	public ChannelFuture open() throws Exception {
		logger.debug("Open Entity {}", getEndpointEntity());
		ChannelFuture future = null;

		if (getEndpointEntity().getHost() == null)
			future = bootstrap.bind(getEndpointEntity().getPort()).sync();
		else
			future = bootstrap.bind(getEndpointEntity().getHost(), getEndpointEntity().getPort()).sync();
		acceptorChannel = future.channel();
		return future;
	}

	@Override
	public void close() throws Exception {
		super.close();
		if (acceptorChannel != null)
			acceptorChannel.close();
		acceptorChannel = null;
		allChannels.close();
	}
	
	protected ChannelInitializer<?> initPipeLine() {

		return new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel ch) throws Exception {
				ChannelPipeline pipeline = ch.pipeline();
				EndpointEntity entity = getEndpointEntity();

				if (entity.isProxyProtocol()) {
					logger.info ("add HAProxyMessageHandler .");
					pipeline.addLast(new HAProxyMessageDecoder());
					pipeline.addLast(new HAProxyMessageHandler());
				}

				if (entity.isUseSSL() && sslCtx != null) {
					logger.info("EndpointEntity {} Use SSL.", entity);
					pipeline.addLast(sslCtx.newHandler(ch.alloc()));
				}

				pipeline.addLast(new ChannelInboundHandlerAdapter() {
					@Override
					public void channelActive(ChannelHandlerContext ctx) {
						allChannels.add(ctx.channel());
						ctx.fireChannelActive();
					}
				});
				
				doinitPipeLine(pipeline);
			}
		};
	};

	/**
	 * useSSL 的服务端必须配 sslCertPath 与 sslKeyPath。以前缺了就临时生成自签名证书顶上，
	 * 但 Netty 的 SelfSignedCertificate 在 JDK 21+ 上已经生成不出来（依赖被移除的内部 API），
	 * 报的是一个和配置无关的内部错误；而且一个每次启动都变的证书在生产里本来就不该存在。
	 * 现在缺配置直接说缺什么、文件不存在直接说哪个文件，都在 open 端点时抛出，不等到有客户端连上。
	 */
	protected SslContext createSslCtx() {
		EndpointEntity entity = getEndpointEntity();
		if (!entity.isUseSSL()) {
			return null;
		}
		String certPath = entity.getSslCertPath();
		String keyPath = entity.getSslKeyPath();
		if (StringUtils.isBlank(certPath) || StringUtils.isBlank(keyPath)) {
			//宁可开不起来，也不要在要求加密的端口上明文提供服务
			throw new IllegalStateException("endpoint " + entity.getId()
					+ " : useSSL is on but sslCertPath/sslKeyPath are not both set (sslCertPath="
					+ certPath + ", sslKeyPath=" + keyPath + ")");
		}
		File cert = new File(certPath);
		File key = new File(keyPath);
		if (!cert.isFile()) {
			throw new IllegalStateException("endpoint " + entity.getId() + " : sslCertPath does not exist or is not a file: " + cert.getAbsolutePath());
		}
		if (!key.isFile()) {
			throw new IllegalStateException("endpoint " + entity.getId() + " : sslKeyPath does not exist or is not a file: " + key.getAbsolutePath());
		}
		try {
			String keyPassword = StringUtils.isBlank(entity.getSslKeyPassword()) ? null : entity.getSslKeyPassword();
			return SslContextBuilder.forServer(cert, key, keyPassword).build();
		} catch (Exception ex) {
			throw new IllegalStateException("create server SslContext failed for endpoint " + entity.getId()
					+ " from sslCertPath=" + cert.getAbsolutePath() + ", sslKeyPath=" + key.getAbsolutePath(), ex);
		}
	}
	protected abstract void doinitPipeLine(ChannelPipeline pipeline) ;
	
	@Override
	protected void doBindHandler(ChannelPipeline pipe, EndpointEntity entity) {

	}

	@Override
	protected AbstractSessionStateManager createSessionManager(EndpointEntity entity, ConcurrentMap storeMap, boolean preSend) {
		// TODO Auto-generated method stub
		return null;
	}

}
