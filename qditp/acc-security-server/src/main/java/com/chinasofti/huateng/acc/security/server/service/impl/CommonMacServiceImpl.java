package com.chinasofti.huateng.acc.security.server.service.impl;

import com.chinasofti.huateng.acc.security.feign.domain.commonmac.InvestMac1Param;
import com.chinasofti.huateng.acc.security.feign.domain.commonmac.InvestMac2Param;
import com.chinasofti.huateng.acc.security.server.config.Constant;
import com.chinasofti.huateng.acc.security.server.config.LocalCache;
import com.chinasofti.huateng.acc.security.server.param.MacBean;
import com.chinasofti.huateng.acc.security.server.service.CommonMacService;
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


@Service
public class CommonMacServiceImpl implements CommonMacService {
    private static final Logger log = LoggerFactory.getLogger(CommonMacServiceImpl.class);

    @Autowired
    private ClientSendMsg clientSendMsg;

    @Override
    public ResultVO<Boolean> verifyMac1(InvestMac1Param param) throws InterruptedException {
        MacBean macBean = new MacBean();
        macBean.setOrder(new byte[]{(byte) 0x81});
        byte[] bytes1 = {0x00, (byte) 0xB6};
        macBean.setIndex(bytes1);
        byte[] disperseData = new byte[8];
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getCardNo()), 0, disperseData, 0, 8);
        macBean.setDisperseData(disperseData);
        byte[] sessionKey = new byte[8];
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getRandomNum()), 0, sessionKey, 0, 4);
        System.arraycopy(NumberUtil.unsignedShortToByte2(param.getTicketCount()), 0, sessionKey, 4, 2);
        sessionKey[6] = (byte) 0x80;
        sessionKey[7] = (byte) 0x00;
        macBean.setSessionKey(sessionKey);
        macBean.setMac(TransformUtils.HexStringToByteArr(param.getMac()));

        byte[] macData = new byte[15];
        System.arraycopy(NumberUtil.intToByte4(param.getBeforeAmt()), 0, macData, 0, 4);
        System.arraycopy(NumberUtil.intToByte4(param.getTxnAmt()), 0, macData, 4, 4);
        macData[8] = 0x02;
        System.arraycopy(TransformUtils.HexStringToByteArr("4500"), 0, macData, 9, 2);
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getDevNodeId()), 0, macData, 11, 4);

        macBean.setBytesLength(NumberUtil.unsignedShortToByte2(macData.length));
        macBean.setBytes(macData);

        String userReation = Constant.ORDER_TYPE.BYTE0 + LocalCache.getSequence();
        macBean.setUserRetain(TransformUtils.HexStringToByteArr(userReation));

        byte[] bytes = ByteConvertUtil.byteMergerAll(
                macBean.getOrderType(),
                macBean.getOrder(),
                macBean.getUserRetain(),
                macBean.getMacType(),
                macBean.getIndex(),
                macBean.getDisperseNum(),
                macBean.getDisperseData(),
                macBean.getTemporaryAlgorithm(),
                macBean.getSessionKey(),
                macBean.getInitial(),
                macBean.getMac(),
                macBean.getBytesLength(),
                macBean.getBytes());


        Channel channel = clientSendMsg.sendMsg(bytes, param.getCardNo(), userReation);

        return getMac1ResultVO(userReation, param.getCardNo(), channel);
    }

    private ResultVO<Boolean> getMac1ResultVO(String userReation, String cardNo, Channel channel) throws InterruptedException {
        long start = System.currentTimeMillis();
        long end;

        String resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
        while (resultData == null) {
            end = System.currentTimeMillis();
            resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
            if ((int) (end - start) >= Constant.OverTime.TIME) {
                log.error("加密机调用超时，卡号{}保留域长度{}",
                        maskCardNo(cardNo), userReation == null ? 0 : userReation.length());
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
    public ResultVO<String> getMac2(InvestMac2Param param) throws InterruptedException {
        MacBean macBean = new MacBean();
        macBean.setOrder(new byte[]{(byte) 0x80});
        byte[] bytes1 = {0x00, (byte) 0xB6};
        macBean.setIndex(bytes1);
        byte[] disperseData = new byte[8];
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getCardNo()), 0, disperseData, 0, 8);
        macBean.setDisperseData(disperseData);
        byte[] sessionKey = new byte[8];
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getRandomNum()), 0, sessionKey, 0, 4);
        System.arraycopy(NumberUtil.unsignedShortToByte2(param.getTicketCount()), 0, sessionKey, 4, 2);
        sessionKey[6] = (byte) 0x80;
        sessionKey[7] = (byte) 0x00;
        macBean.setSessionKey(sessionKey);

        byte[] macData = new byte[18];
        System.arraycopy(NumberUtil.intToByte4(param.getTxnAmt()), 0, macData, 0, 4);
        macData[4] = 0x02;
        System.arraycopy(TransformUtils.HexStringToByteArr("4500"), 0, macData, 5, 2);
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getDevNodeId()), 0, macData, 7, 4);
        System.arraycopy(TransformUtils.HexStringToByteArr(param.getCenterTime()), 0, macData, 11, 7);

        macBean.setBytesLength(NumberUtil.unsignedShortToByte2(macData.length));
        macBean.setBytes(macData);
        String userReation = Constant.ORDER_TYPE.BYTE8 + LocalCache.getSequence();
        macBean.setUserRetain(TransformUtils.HexStringToByteArr(userReation));

        byte[] bytes = ByteConvertUtil.byteMergerAll(
                macBean.getOrderType(),
                macBean.getOrder(),
                macBean.getUserRetain(),
                macBean.getMacType(),
                macBean.getIndex(),
                macBean.getDisperseNum(),
                macBean.getDisperseData(),
                macBean.getTemporaryAlgorithm(),
                macBean.getSessionKey(),
                macBean.getInitial(),
                macBean.getBytesLength(),
                macBean.getBytes());

        Channel channel = clientSendMsg.sendMsg(bytes, param.getCardNo(), userReation);
        return getMac2ResultVO(userReation, param.getCardNo(), channel);
    }

    private ResultVO<String> getMac2ResultVO(String userReation, String cardNo, Channel channel) throws InterruptedException {
        long start = System.currentTimeMillis();
        long end;

        String resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
        while (resultData == null) {
            end = System.currentTimeMillis();
            resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
            if ((int) (end - start) >= Constant.OverTime.TIME) {
                log.error("加密机调用超时，卡号{}保留域长度{}",
                        maskCardNo(cardNo), userReation == null ? 0 : userReation.length());
                return ResultMapper.error("加密机调用超时");
            } else {
                Thread.sleep(Constant.OverTime.POLL_TIME);
            }
        }

        log.info("结果" + resultData);
        ResultVO<String> resultVO = new ResultVO();

        if ("41".equals(resultData.substring(0, 2))) {
            String temp = resultData.substring(resultData.length() - 16, resultData.length() - 8);
            resultVO.setData(temp);
        } else {
            resultVO.setCode(ResultVO.ERROR_CODE);
            resultVO.setMsg(ResultVO.ERROR_MSG);
            resultVO.setData("");
        }
        return resultVO;
    }

    /**
     * 日志脱敏：卡号仅保留前 6 后 4 位，中间以 * 代替，禁止完整卡号落日志。
     */
    private static String maskCardNo(String cardNo) {
        if (cardNo == null || cardNo.length() <= 10) {
            return cardNo == null ? "-" : "****";
        }
        return cardNo.substring(0, 6) + "****" + cardNo.substring(cardNo.length() - 4);
    }

}
