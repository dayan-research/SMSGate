package com.zx.sms.codec.smgp.msg;

import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.Test;

import com.zx.sms.codec.smgp.util.SMGPMsgIdUtil;

/**
 * 状态报告解析失败时，不应该把一个只填了一半的 SMGPReportData 挂到消息上。
 */
public class TestSMGPReportData {

	// id:.. sub:001 dlvrd:001 submit_date:1807052207 done_date:1807052207 stat:DELIVRD err:000 txt:..
	private static final String REPORT_HEX = "69643a6495c36ac4ea6b00000b7375623a30303120646c7672643a303031207375626d69745f646174653a3138303730353232303720646f6e655f646174653a3138303730353232303720737461743a44454c49565244206572723a303030207478743ad6d0b9fa306138616535";

	private static byte[] truncatedReport() throws Exception {
		byte[] full = Hex.decodeHex(REPORT_HEX.toCharArray());
		byte[] truncated = new byte[30];
		System.arraycopy(full, 0, truncated, 0, truncated.length);
		return truncated;
	}

	@Test
	public void fromBytesReturnsFalseOnTruncatedReceipt() throws Exception {
		Assert.assertTrue(new SMGPReportData().fromBytes(Hex.decodeHex(REPORT_HEX.toCharArray())));
		Assert.assertFalse(new SMGPReportData().fromBytes(truncatedReport()));
	}

	@Test
	public void deliverKeepsRawBytesAndNoReportWhenReceiptIsBroken() throws Exception {
		byte[] truncated = truncatedReport();
		SMGPDeliverMessage msg = new SMGPDeliverMessage();
		msg.setBody(buildDeliverBody(truncated), 0x30);

		Assert.assertTrue(msg.isReport());
		Assert.assertNull(msg.getReport());
		Assert.assertArrayEquals(truncated, msg.getBMsgContent());
	}

	@Test
	public void deliverKeepsReportWhenReceiptIsValid() throws Exception {
		byte[] report = Hex.decodeHex(REPORT_HEX.toCharArray());
		SMGPDeliverMessage msg = new SMGPDeliverMessage();
		msg.setBody(buildDeliverBody(report), 0x30);

		Assert.assertTrue(msg.isReport());
		Assert.assertNotNull(msg.getReport());
		//解析成功时原始字节不再单独保留，和历史行为一致
		Assert.assertNull(msg.getBMsgContent());
	}

	/**
	 * SMGP Deliver 的 body: msgId(10) isReport(1) msgFmt(1) recvTime(14) srcTermId(21)
	 * destTermId(21) msgLength(1) msgContent(msgLength) reserve(8)
	 */
	private static byte[] buildDeliverBody(byte[] content) throws Exception {
		byte[] body = new byte[10 + 1 + 1 + 14 + 21 + 21 + 1 + content.length + 8];
		int offset = 0;
		System.arraycopy(SMGPMsgIdUtil.msgId2Bytes(new MsgId()), 0, body, offset, 10);
		offset += 10;
		body[offset++] = 1; // isReport
		body[offset++] = 0x0f; // msgFmt
		System.arraycopy("20180705220700".getBytes("US-ASCII"), 0, body, offset, 14);
		offset += 14;
		System.arraycopy("13800138000".getBytes("US-ASCII"), 0, body, offset, 11);
		offset += 21;
		System.arraycopy("10086".getBytes("US-ASCII"), 0, body, offset, 5);
		offset += 21;
		body[offset++] = (byte) content.length;
		System.arraycopy(content, 0, body, offset, content.length);
		return body;
	}
}
