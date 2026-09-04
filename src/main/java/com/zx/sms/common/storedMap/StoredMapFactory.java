package com.zx.sms.common.storedMap;

import java.util.concurrent.ConcurrentMap;

/**
 * 会话层用来存放"已发送但还没收到响应"的消息的 Map 的工厂。
 * <p>
 * 这是持久化的扩展点：默认实现 {@link InMemoryStoredMapFactory} 只在单个 JVM 生命周期内有效，
 * 想要跨进程重启的持久化，实现本接口并通过 {@link StoredMapFactoryHolder} 注册即可，
 * 不需要改动本项目的代码。
 */
public interface StoredMapFactory<K,T extends VersionObject> {
	
	/**
	 * @param storedpath
	 * 数据文件保存的路径
	 * @param name 
	 * Map的名字
	 */
	ConcurrentMap<K,T> buildMap(String storedpath,String name);
	
	/**
	 * 释放 (storedpath,name) 占用的资源（连接、文件句柄等）。只是关句柄，<b>不删除数据</b>：
	 * 之后再次 {@link #buildMap} 必须能看到之前写进去、还没被删掉的内容。
	 */
	void close(String storedpath,String name);
	
}
