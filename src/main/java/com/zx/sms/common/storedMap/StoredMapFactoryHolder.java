package com.zx.sms.common.storedMap;

import java.io.Serializable;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link StoredMapFactory} 的解析入口。
 * <p>
 * 第一次使用时通过 {@link ServiceLoader} 在 classpath 上找 {@code StoredMapFactory} 的实现
 * （即 {@code META-INF/services/com.zx.sms.common.storedMap.StoredMapFactory}），找不到就退回
 * {@link InMemoryStoredMapFactory}。也可以在启动时调用 {@link #set} 直接指定。
 */
public abstract class StoredMapFactoryHolder {

	private static final Logger logger = LoggerFactory.getLogger(StoredMapFactoryHolder.class);

	private static volatile StoredMapFactory<Serializable, VersionObject> INSTANCE = load();

	@SuppressWarnings("unchecked")
	private static StoredMapFactory<Serializable, VersionObject> load() {
		try {
			Iterator<StoredMapFactory> it = ServiceLoader.load(StoredMapFactory.class).iterator();
			if (it.hasNext()) {
				StoredMapFactory<Serializable, VersionObject> found = it.next();
				logger.info("use StoredMapFactory implementation : {}", found.getClass().getName());
				return found;
			}
		} catch (Throwable ex) {
			logger.warn("load StoredMapFactory failed, fall back to InMemoryStoredMapFactory", ex);
		}
		logger.info("no StoredMapFactory implementation found. use InMemoryStoredMapFactory : "
				+ "unacked messages are replayed within this JVM only, they are lost on restart.");
		return InMemoryStoredMapFactory.INS;
	}

	public static StoredMapFactory<Serializable, VersionObject> get() {
		return INSTANCE;
	}

	/**
	 * 用指定的实现替换当前实现。传 null 表示回到 {@link InMemoryStoredMapFactory}。
	 */
	public static void set(StoredMapFactory<Serializable, VersionObject> factory) {
		INSTANCE = factory == null ? InMemoryStoredMapFactory.INS : factory;
	}
}
