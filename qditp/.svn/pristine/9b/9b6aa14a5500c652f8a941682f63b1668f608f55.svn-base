package com.chinasofti.huateng.acc.security.server.service.impl;

import com.chinasofti.huateng.acc.security.feign.domain.commonmac.SaleAndRefundParam;
import com.chinasofti.huateng.acc.security.server.config.Constant;
import com.chinasofti.huateng.acc.security.server.config.LocalCache;
import com.chinasofti.huateng.acc.security.server.param.SaleAndRefundBean;
import com.chinasofti.huateng.acc.security.server.service.CommonSaleAndRefundService;
import com.chinasofti.huateng.acc.security.server.socket.ClientSendMsg;
import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.common.util.ByteConvertUtil;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author houkepan
 * @date 2020/5/6 11:56
 */
@Service
public class CommonSaleAndRefundServiceImpl implements CommonSaleAndRefundService {
    private static final Logger log = LoggerFactory.getLogger(CommonSaleAndRefundServiceImpl.class);

    @Autowired
    private ClientSendMsg clientSendMsg;

    @Override
    public ResultVO<String> getSaleAndRefundKey(SaleAndRefundParam param) throws InterruptedException {
//        命令类型：B0
//        命令：91
//        用户保留字：0000000000000000
//        分散次数：02
//        分散数据：4500000000000000 + 卡号
//        数据长度：0008
//        数据：8字节

        SaleAndRefundBean bean = new SaleAndRefundBean();
        bean.setOrder(new byte[]{(byte) 0x91});

        String userReation = Constant.ORDER_TYPE.BYTE10 + LocalCache.getSequence();
        bean.setUserRetain(TransformUtils.HexStringToByteArr(userReation));

        // MAC1 分散秘钥00B0
        byte[] bytes1 = {0x00, (byte) 0xB0};
        bean.setIndex(bytes1);
        // 分散数据 卡号
        byte[] disperseData = new byte[16];
        System.arraycopy(TransformUtils.HexStringToByteArr("4500FF0000000000"), 0, disperseData, 0, 8);
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getCardNo()), 0, disperseData, 8, 8);
        bean.setDisperseData(disperseData);

        // mac数据拼接
        byte[] macData = new byte[8];
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getRandomNum()), 0, macData, 0, 8);

        // mac数据
        bean.setBytes(macData);

        byte[] bytes = ByteConvertUtil.byteMergerAll(
                bean.getOrderType(),
                bean.getOrder(),
                bean.getUserRetain(),
                bean.getIndex(),
                bean.getDisperseNum(),
                bean.getDisperseData(),
                bean.getBytesLength(),
                bean.getBytes());

        Channel channel = clientSendMsg.sendMsg(bytes, param.getCardNo(), userReation);

        return getSaleAndRefundKeyResultVO(userReation, param.getCardNo(), channel);
    }

    private ResultVO<String> getSaleAndRefundKeyResultVO(String userReation, String cardNo, Channel channel) throws InterruptedException {
        long start = System.currentTimeMillis();
        long end;

        String resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
        while (resultData == null) {
            end = System.currentTimeMillis();
            resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
            if ((int) (end - start) >= Constant.OverTime.TIME) {
                log.error("加密机调用超时，卡号{}用户保留域{}", cardNo, userReation.toUpperCase());
                return ResultMapper.error("加密机调用超时");
            } else {
                Thread.sleep(Constant.OverTime.POLL_TIME);
            }
        }

        ResultVO<String> resultVO = new ResultVO();

        if ("41".equals(resultData.substring(0, 2))) {
            String key = resultData.substring(resultData.length() - 16);
            resultVO.setData(key);
        } else {
            resultVO.setData(null);
        }
        return resultVO;
    }

}
