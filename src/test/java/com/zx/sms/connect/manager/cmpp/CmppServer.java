package com.zx.sms.connect.manager.cmpp;

import com.zx.sms.common.GlobalConstance;
import com.zx.sms.connect.manager.EndpointManager;
import com.zx.sms.handler.api.AbstractBusinessHandler;
import com.zx.sms.handler.api.BusinessHandlerInterface;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class CmppServer {
    public static void main(String[] args) throws InterruptedException {
        EndpointManager.INS.removeAll();
        int port = 17890;
        CMPPServerEndpointEntity server = new CMPPServerEndpointEntity();
        server.setId("server");
        server.setHost("0.0.0.0");
        server.setPort(port);
        server.setValid(true);
        // 使用ssl加密数据流
        server.setUseSSL(false);
        server.setWindow(81960);
        server.setOverSpeedSendCountLimit(30);

        CMPPServerChildEndpointEntity child = new CMPPServerChildEndpointEntity();
        child.setId("690365");
        child.setChartset(Charset.forName("utf-8"));
        child.setGroupName("690365");
        child.setUserName("690365");
        child.setPassword("awpq2m");

        child.setValid(true);
        child.setVersion((short) 0x20);

        child.setMaxChannels((short) 32);
        child.setRetryWaitTimeSec((short) 30);
        child.setMaxRetryCnt((short) 3);

        //不开启IP白名单
//		List<String> iplist = new ArrayList<String>();
//		iplist.add("192.168.98.48/18");
//		child.setAllowedAddr(iplist);

        child.setReSendFailMsg(true);
        // child.setWriteLimit(200);
        // child.setReadLimit(200);
        List<BusinessHandlerInterface> serverhandlers = new ArrayList<BusinessHandlerInterface>();

        CMPPMessageReceiveHandler receiver = new CMPPMessageReceiveHandler();
        serverhandlers.add(new AbstractBusinessHandler() {

            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                CMPPResponseSenderHandler handler = new CMPPResponseSenderHandler();
                handler.setEndpointEntity(getEndpointEntity());
                ctx.pipeline().addAfter(GlobalConstance.sessionStateManager, handler.name(), handler);
                ctx.pipeline().remove(this);
            }

            @Override
            public String name() {
                return "AddCMPPResponseSenderHandler";
            }

        });
        serverhandlers.add(receiver);
        child.setBusinessHandlerSet(serverhandlers);
        server.addchild(child);
        EndpointManager.INS.openEndpoint(server);

        Thread.sleep(1000000000);
        EndpointManager.INS.close(server);
    }
}
