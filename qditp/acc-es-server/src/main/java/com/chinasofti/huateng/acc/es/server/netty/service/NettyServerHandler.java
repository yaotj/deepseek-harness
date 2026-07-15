package com.chinasofti.huateng.acc.es.server.netty.service;

import com.chinasofti.huateng.acc.es.server.netty.model.Constant;
import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import com.chinasofti.huateng.acc.es.server.netty.model.Messagehead;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@ChannelHandler.Sharable
public class NettyServerHandler  extends ChannelInboundHandlerAdapter {


    private final BusinessHandler businessHandler;


    /**
     * 当前channel从远端读取到数据
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MessageBean messageBean= (MessageBean) msg;
        log.info("收到消息{}", messageBean.toString());
        Messagehead result= null;//设置应答消息
        String txnType = messageBean.getTxnType();
        switch (txnType) {
            case Constant.DataPackage.TXN_TYPE_7000:
                result=businessHandler.deviceSignIn(messageBean);
                break;
            case Constant.DataPackage.TXN_TYPE_7002:
                result = businessHandler.taskApply(messageBean);
                break;
            case Constant.DataPackage.TXN_TYPE_7003:
                result = businessHandler.deviceSignOut(messageBean);
                break;
            case Constant.DataPackage.TXN_TYPE_7004:
                //设备工作任务报告
                result = businessHandler.taskStatReport(messageBean);
                break;
            case Constant.DataPackage.TXN_TYPE_7005:
                result = businessHandler.deviceStat(messageBean);
                break;
            default:
                log.error("交易类型错误");
                break;
        }
        log.info("发送消息应答,消息为：{}",result.toString());
        ctx.writeAndFlush(result);
    }

    /**
     * 当前channel不活跃的时候，也就是当前channel到了它生命周期末
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        super.channelInactive(ctx);
    }
    /**
     * 异常发生的事件
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("服务异常，关闭连接",cause);
        log.error("出现异常的位置",cause);
        ctx.close();

        //super.exceptionCaught(ctx, cause);
     }


}
