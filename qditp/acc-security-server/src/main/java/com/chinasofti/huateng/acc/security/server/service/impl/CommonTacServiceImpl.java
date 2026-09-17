package com.chinasofti.huateng.acc.security.server.service.impl;

import com.chinasofti.huateng.acc.security.feign.domain.commontac.CpuTacParam;
import com.chinasofti.huateng.acc.security.feign.domain.commontac.SingleTicketTacParam;
import com.chinasofti.huateng.acc.security.server.config.Constant;
import com.chinasofti.huateng.acc.security.server.config.LocalCache;
import com.chinasofti.huateng.acc.security.server.service.CommonTacService;
import com.chinasofti.huateng.acc.security.server.param.TacBean;
import com.chinasofti.huateng.acc.security.server.socket.ClientSendMsg;
import com.chinasofti.huateng.acc.security.server.util.NumberUtil;
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
public class CommonTacServiceImpl implements CommonTacService {
    private static final Logger log = LoggerFactory.getLogger(CommonTacServiceImpl.class);

    @Autowired
    ClientSendMsg clientSendMsg;

    @Override
    public ResultVO<Boolean> ulTacVerify(SingleTicketTacParam param) throws InterruptedException {
        TacBean tacBean = new TacBean();
        // ul卡 分散秘钥00C3
        byte[] bytes1 = {0x00, (byte) 0xC3};
        tacBean.setIndex(bytes1);
        tacBean.setTac(TransformUtils.HexStringToByteArr(param.getTac()));
        tacBean.setDisperseNum(new byte[]{(byte) 01});
        // ul 分散数据 = 逻辑卡号
        tacBean.setDisperseData(TransformUtils.HexStringToByteArr(param.getCardNo()));
        byte[] bytes = TransformUtils.HexStringToByteArr(param.getCommonTacStr());
        tacBean.setBytesLength(NumberUtil.unsignedShortToByte2(bytes.length));
        tacBean.setBytes(TransformUtils.HexStringToByteArr(param.getCommonTacStr()));
        String userReation = Constant.ORDER_TYPE.BYTE0 + LocalCache.getSequence();
        tacBean.setUserRetain(TransformUtils.HexStringToByteArr(userReation));

        byte[] sendData = ByteConvertUtil.byteMergerAll(
                tacBean.getOrderType(),
                tacBean.getOrder(),
                tacBean.getUserRetain(),
                tacBean.getTacType(),
                tacBean.getIndex(),
                tacBean.getDisperseNum(),
                tacBean.getDisperseData(),
                tacBean.getInitial(),
                tacBean.getTac(),
                tacBean.getBytesLength(),
                tacBean.getBytes()
        );

        Channel channel = clientSendMsg.sendMsg(sendData, param.getCardNo(), userReation);
        return getBooleanResultVO(userReation, param.getCardNo(), channel);

    }

    private ResultVO<Boolean> getBooleanResultVO(String userReation, String cardNo, Channel channel) throws InterruptedException {
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

        ResultVO<Boolean> resultVO = new ResultVO();

        if ("41".equals(resultData.substring(0, 2))) {
            resultVO.setData(true);
        } else {
            resultVO.setData(false);
        }
        return resultVO;
    }

    @Override
    public ResultVO<Boolean> cpuTacVerify(CpuTacParam param) throws InterruptedException {
        TacBean tacBean = new TacBean();
        // ul卡 分散秘钥00C1
        byte[] bytes1 = {0x00, (byte) 0xC1};
        tacBean.setIndex(bytes1);
        tacBean.setTac(TransformUtils.HexStringToByteArr(param.getTac()));
        tacBean.setDisperseNum(new byte[]{(byte) 02});
        // 分散数据= 0532FF0000000000 + 逻辑卡号   青岛 0532
        tacBean.setDisperseData(TransformUtils.HexStringToByteArr("0532FF0000000000" + param.getCardNo()));
        byte[] bytes = TransformUtils.HexStringToByteArr(param.getCommonTacStr());
        tacBean.setBytesLength(NumberUtil.unsignedShortToByte2(bytes.length));
        tacBean.setBytes(TransformUtils.HexStringToByteArr(param.getCommonTacStr()));
        String userRetain = Constant.ORDER_TYPE.BYTE0 + LocalCache.getSequence();
        tacBean.setUserRetain(TransformUtils.HexStringToByteArr(userRetain));

        byte[] sendData = ByteConvertUtil.byteMergerAll(
                tacBean.getOrderType(),
                tacBean.getOrder(),
                tacBean.getUserRetain(),
                tacBean.getTacType(),
                tacBean.getIndex(),
                tacBean.getDisperseNum(),
                tacBean.getDisperseData(),
                tacBean.getInitial(),
                tacBean.getTac(),
                tacBean.getBytesLength(),
                tacBean.getBytes()
        );

        Channel channel = clientSendMsg.sendMsg(sendData, param.getCardNo(), userRetain);
        return getBooleanResultVO(userRetain, param.getCardNo(), channel);
    }

}
