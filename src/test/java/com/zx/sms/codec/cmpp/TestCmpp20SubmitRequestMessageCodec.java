package com.zx.sms.codec.cmpp;

import java.util.UUID;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.Test;

import com.chinamobile.cmos.sms.SmsDcs;
import com.chinamobile.cmos.sms.SmsTextMessage;
import com.zx.sms.codec.AbstractTestMessageCodec;
import com.zx.sms.codec.cmpp.msg.CmppSubmitRequestMessage;
import com.zx.sms.common.util.MsgId;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class TestCmpp20SubmitRequestMessageCodec extends AbstractTestMessageCodec<CmppSubmitRequestMessage> {
	@Override
	protected int getVersion(){
		return 0x20;
	}

	@Test
	public void testCodecGBKLong()
	{
		CmppSubmitRequestMessage msg = new CmppSubmitRequestMessage();
		
		msg.setDestterminalId(new String[]{"13800138000","13800138001","138001380002"});
		msg.setLinkID("0000");
		String content = UUID.randomUUID().toString();
		msg.setMsgContent(content);
		msg.setMsgContent(new SmsTextMessage("你好，【温馨提示】移娃没理解您的问题2【温馨提示】移娃没理解您的问题3【温馨提示】移娃没理解您的问题4【温馨提示】移娃没理解您的问题5【",new SmsDcs((byte)15)));
		
		msg.setMsgid(new MsgId());
		msg.setServiceId("10000");
		msg.setSrcId("10000");
		
		
		ByteBuf buf = encode(msg);
		
		ByteBuf copybuf = buf.copy();
		
		int length = buf.readableBytes();
		
		Assert.assertEquals(length, buf.readInt());
		Assert.assertEquals(msg.getPacketType().getCommandId(), buf.readInt());
		Assert.assertEquals(msg.getHeader().getSequenceId(), buf.readInt());
		
	
		
		CmppSubmitRequestMessage result = decode(copybuf);
		System.out.println(result);
		Assert.assertEquals(msg.getHeader().getSequenceId(), result.getHeader().getSequenceId());
		Assert.assertArrayEquals(msg.getDestterminalId(), result.getDestterminalId());
		Assert.assertEquals(msg.getMsgContent(), result.getMsgContent());
		Assert.assertEquals(msg.getServiceId(), result.getServiceId());
	}
	

	@Test
	public void testCodec()
	{
		CmppSubmitRequestMessage msg = new CmppSubmitRequestMessage();
		
		msg.setDestterminalId(new String[]{"13800138000","13800138001","138001380002"});
		msg.setLinkID("0000");
		String content = UUID.randomUUID().toString();
		msg.setMsgContent(content);
		msg.setMsgContent(new SmsTextMessage("你好，我是闪信！",new SmsDcs((byte)15)));
		
		msg.setMsgid(new MsgId());
		msg.setServiceId("10000");
		msg.setSrcId("10000");
		
		
		ByteBuf buf = encode(msg);
		
		ByteBuf copybuf = buf.copy();
		
		int length = buf.readableBytes();
		
		Assert.assertEquals(length, buf.readInt());
		Assert.assertEquals(msg.getPacketType().getCommandId(), buf.readInt());
		Assert.assertEquals(msg.getHeader().getSequenceId(), buf.readInt());
		
	
		
		CmppSubmitRequestMessage result = decode(copybuf);
		System.out.println(result);
		Assert.assertEquals(msg.getHeader().getSequenceId(), result.getHeader().getSequenceId());
		Assert.assertArrayEquals(msg.getDestterminalId(), result.getDestterminalId());
		Assert.assertEquals(msg.getMsgContent(), result.getMsgContent());
		Assert.assertEquals(msg.getServiceId(), result.getServiceId());
	}

	@Test
	public void testchinesecode()
	{
		testlongCodec("1234567890123456789中01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" );
	}

	@Test
	public void testASCIIcode()
	{
		testlongCodec("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
	}
	
	
	public void testlongCodec(String content)
	{
		CmppSubmitRequestMessage msg = new CmppSubmitRequestMessage();
		msg.setDestterminalId(new String[]{"13800138000","13800138001","138001380002"});
		msg.setLinkID("0000");
		msg.setMsgContent(content);
		msg.setMsgid(new MsgId());
		msg.setServiceId("10000");
		msg.setSrcId("10000");
		channel().writeOutbound(msg);
		ByteBuf buf =(ByteBuf)channel().readOutbound();
		ByteBuf copybuf = Unpooled.buffer();
	    while(buf!=null){
			
			
	    	ByteBuf copy = buf.copy();
	    	copybuf.writeBytes(copy);
	    	copy.release();
			int length = buf.readableBytes();
			
			Assert.assertEquals(length, buf.readInt());
			Assert.assertEquals(msg.getPacketType().getCommandId(), buf.readInt());
			buf.release();

			buf =(ByteBuf)channel().readOutbound();
	    }
	    
		CmppSubmitRequestMessage result = decode(copybuf);
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.getUniqueLongMsgId().getId());
		System.out.println(result.getMsgContent());
		Assert.assertEquals(msg.getServiceId(), result.getServiceId());
		Assert.assertArrayEquals(msg.getDestterminalId(), result.getDestterminalId());
		Assert.assertEquals(msg.getMsgContent(), result.getMsgContent());
	}
	
	@Test
	public void testErrTpudhiMsg1() throws DecoderException {
		//测试不支持长短信的多关，	可能出现错误的长短信报文，设置了长短信标识头（tpudhi） ,但短信内容里没有udh头
			byte[] expected = Hex.decodeHex("0000012b0000000467e38b7f39c937c5db294704030100093130303030000000000002000000000000000000000000000000000000000000000108000000000000303130303030303000000000000000000000000000000000000000000000000000000000000000000000313030303000000000000000000000000000000000013133383030313338303030000000000000000000008c51d1003100320033003200334e2d0034003500360037003800390030003100410073007300420043003500360037003800390030003100320033003400350036003700380039003000310032003300340035003600370038003900300031003200330034003500360037003800390030003100320033003400350036003700380039003000310032003300340000000000000000".toCharArray()) ;;
			ByteBuf buf = Unpooled.buffer(expected.length);
			buf.writeBytes(expected);
			byte[] actuals = new byte[expected.length];
			int index = 0;
			ch.writeInbound(buf);
			CmppSubmitRequestMessage result = null;
			while (null != (result = (CmppSubmitRequestMessage) ch.readInbound())) {
				System.out.println(result);
				Assert.assertEquals("凑12323中45678901AssBC56789012345678901234567890123456789012345678901234", result.getMsgContent());
			}
	}
	
	@Test
	public void testErrTpudhiMsg2() throws DecoderException {
		//测试不支持长短信的多关，	可能出现错误的长短信报文，设置了长短信标识头（tpudhi） ,但短信内容里没有udh头
			byte[] expected = Hex.decodeHex("0000012b0000000467e38b7f39c937c5db294704030100093130303030000000000002000000000000000000000000000000000000000000000108000000000000303130303030303000000000000000000000000000000000000000000000000000000000000000000000313030303000000000000000000000000000000000013133383030313338303030000000000000000000008c91d1003100320033003200334e2d0034003500360037003800390030003100410073007300420043003500360037003800390030003100320033003400350036003700380039003000310032003300340035003600370038003900300031003200330034003500360037003800390030003100320033003400350036003700380039003000310032003300340000000000000000".toCharArray()) ;;
			ByteBuf buf = Unpooled.buffer(expected.length);
			buf.writeBytes(expected);
			byte[] actuals = new byte[expected.length];
			int index = 0;
			ch.writeInbound(buf);
			CmppSubmitRequestMessage result = null;
			while (null != (result = (CmppSubmitRequestMessage) ch.readInbound())) {
				System.out.println(result);
				Assert.assertEquals("金12323中45678901AssBC56789012345678901234567890123456789012345678901234", result.getMsgContent());
			}
	}
}
