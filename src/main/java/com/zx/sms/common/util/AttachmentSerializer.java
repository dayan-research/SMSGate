package com.zx.sms.common.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBufUtil;

/**
 * 用 JDK 自带的序列化机制读写 Serializable 对象。替换原来的 fst
 * ({@code FstObjectSerializeUtil})，fst 2.48 在 JDK 9+ 上依赖已被封装的 JDK 内部结构，
 * 在 JDK 17/21/25 上会抛 {@code InaccessibleObjectException}。
 * <p>
 * 反序列化时安装了一个白名单 {@link ObjectInputFilter}：只允许本项目消息对象图上会出现的包
 * （{@code com.zx.sms.**}、{@code com.chinamobile.cmos.**}、{@code io.netty.channel.**} 以及
 * {@code java.lang.*}/{@code java.util.**}/{@code java.math.*}/{@code java.time.**}/{@code java.net.*}）
 * 及它们的数组，其余一律拒绝，并限制了嵌套深度、引用数和字节数。
 */
public abstract class AttachmentSerializer {

	private static final Logger logger = LoggerFactory.getLogger(AttachmentSerializer.class);

	private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
			"maxdepth=32;maxrefs=10000;maxarray=100000;maxbytes=1048576;"
			+ "com.zx.sms.**;com.chinamobile.cmos.**;io.netty.channel.**;"
			+ "java.lang.*;java.util.**;java.math.*;java.time.**;java.net.*;!*");

	public static byte[] write(Serializable obj) throws IOException {
		ByteArrayOutputStream arroutput = new ByteArrayOutputStream();
		ObjectOutputStream objoutput = new ObjectOutputStream(arroutput);
		try {
			objoutput.writeObject(obj);
			objoutput.flush();
			return arroutput.toByteArray();
		} finally {
			objoutput.close();
		}
	}

	/**
	 * 反序列化。失败时返回 null（与历史行为一致），但会在 warn 级别打印报文十六进制，不再静默吞掉。
	 */
	public static Serializable read(byte[] bytes) {
		if (bytes == null || bytes.length == 0)
			return null;
		try {
			ObjectInputStream objinput = new ObjectInputStream(new ByteArrayInputStream(bytes));
			try {
				objinput.setObjectInputFilter(FILTER);
				Object t = objinput.readObject();
				if (t instanceof Serializable) {
					return (Serializable) t;
				} else {
					logger.warn("deserialized object is not Serializable : {}", t == null ? null : t.getClass());
					return null;
				}
			} finally {
				objinput.close();
			}
		} catch (Exception ex) {
			logger.warn("deserialize failed, bytes={}", ByteBufUtil.hexDump(bytes), ex);
			return null;
		}
	}
}
