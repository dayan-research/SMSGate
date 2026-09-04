package com.zx.sms.common.storedMap;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link StoredMapFactory} 的默认实现，纯内存。
 * <p>
 * 同一个 (storedpath,name) 在整个 JVM 生命周期内返回同一个 {@link ConcurrentHashMap}。
 * 因此 {@code EndpointEntity.reSendFailMsg=true} 在断线重连时仍然可以把没收到响应的消息重发一次，
 * 但<b>进程重启后这些消息会丢失</b>。需要跨重启的持久化时，实现 {@link StoredMapFactory}
 * 并用 {@link StoredMapFactoryHolder} 注册。
 */
public enum InMemoryStoredMapFactory implements StoredMapFactory<Serializable, VersionObject> {
	INS;

	private final ConcurrentHashMap<String, ConcurrentMap<Serializable, VersionObject>> storedMaps = new ConcurrentHashMap<String, ConcurrentMap<Serializable, VersionObject>>();

	@Override
	public ConcurrentMap<Serializable, VersionObject> buildMap(String storedpath, String name) {
		String key = buildKey(storedpath, name);
		ConcurrentMap<Serializable, VersionObject> map = storedMaps.get(key);
		if (map == null) {
			ConcurrentMap<Serializable, VersionObject> tmpMap = new ConcurrentHashMap<Serializable, VersionObject>();
			ConcurrentMap<Serializable, VersionObject> old = storedMaps.putIfAbsent(key, tmpMap);
			return old == null ? tmpMap : old;
		}
		return map;
	}

	/**
	 * 与被替换掉的 BDB 实现语义一致：close 只是释放句柄，<b>不删除数据</b>。
	 * 内存实现没有句柄可释放，所以这里什么也不做，下次 {@link #buildMap} 仍然拿到同一个 Map ——
	 * 端点整体关闭再打开时，上次没收到响应的消息才能被重发。
	 */
	@Override
	public void close(String storedpath, String name) {
	}

	private String buildKey(String storedpath, String name) {
		return new StringBuilder().append(storedpath).append(name).toString();
	}
}
