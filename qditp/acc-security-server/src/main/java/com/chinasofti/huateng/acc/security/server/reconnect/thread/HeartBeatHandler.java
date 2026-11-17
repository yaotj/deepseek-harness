package com.chinasofti.huateng.acc.security.server.reconnect.thread;

import io.netty.channel.ChannelHandlerContext;


public interface HeartBeatHandler {


    void process(ChannelHandlerContext ctx) throws Exception;
}
