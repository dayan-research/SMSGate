package com.zx.sms.common;

import java.io.Serializable;
import java.util.concurrent.ConcurrentMap;

import org.junit.Assert;
import org.junit.Test;

import com.zx.sms.common.storedMap.InMemoryStoredMapFactory;
import com.zx.sms.common.storedMap.StoredMapFactoryHolder;
import com.zx.sms.common.storedMap.VersionObject;

public class TestInMemoryStoredMap {

	@Test
	public void testSameInstanceUntilClosed() {
		ConcurrentMap<Serializable, VersionObject> map = InMemoryStoredMapFactory.INS.buildMap("testA", "testQ");
		map.put("121", new VersionObject<String>("1"));

		Assert.assertSame(map, InMemoryStoredMapFactory.INS.buildMap("testA", "testQ"));
		Assert.assertNotSame(map, InMemoryStoredMapFactory.INS.buildMap("testA", "otherQ"));
		Assert.assertNotSame(map, InMemoryStoredMapFactory.INS.buildMap("testB", "testQ"));

		InMemoryStoredMapFactory.INS.close("testA", "otherQ");
		InMemoryStoredMapFactory.INS.close("testB", "testQ");
		InMemoryStoredMapFactory.INS.close("testA", "testQ");
	}

	/**
	 * close 只释放句柄，不删除数据 —— 与被替换掉的 BDB 实现语义一致，
	 * 否则端点关闭再打开时未收到响应的消息就重发不出去了。
	 */
	@Test
	public void testCloseKeepsTheData() {
		ConcurrentMap<Serializable, VersionObject> map = InMemoryStoredMapFactory.INS.buildMap("testClose", "testQ");
		map.put("121", new VersionObject<String>("1"));
		Assert.assertEquals("1", map.get("121").getObj());

		InMemoryStoredMapFactory.INS.close("testClose", "testQ");
		// closing twice is harmless
		InMemoryStoredMapFactory.INS.close("testClose", "testQ");

		ConcurrentMap<Serializable, VersionObject> reopened = InMemoryStoredMapFactory.INS.buildMap("testClose", "testQ");
		Assert.assertEquals("1", reopened.get("121").getObj());

		reopened.put("1212", new VersionObject<String>("2"));
		Assert.assertEquals("2", reopened.get("1212").getObj());
		Assert.assertNull(reopened.remove("nothere"));
		Assert.assertEquals("2", reopened.remove("1212").getObj());
		Assert.assertEquals("1", reopened.remove("121").getObj());
		Assert.assertTrue(reopened.isEmpty());
	}

	@Test
	public void testDefaultResolvesToInMemory() {
		Assert.assertSame(InMemoryStoredMapFactory.INS, StoredMapFactoryHolder.get());
	}
}
