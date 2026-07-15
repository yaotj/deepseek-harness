package com.chinasofti.huateng.alipay.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.account.entity.AlipayCardPool;
import com.chinasofti.huateng.alipay.account.entity.AlipayRegLog;
import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayCardPoolMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayRegLogMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.alipay.account.service.AlipayAccountService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AlipayAccountServiceImpl implements AlipayAccountService {
    private static final Logger log = LoggerFactory.getLogger(AlipayAccountServiceImpl.class);

    @Value("${alipay.card-pool.debug-manual-allocate:true}")
    private boolean debugManualAllocate;

    @Value("${alipay.card-pool.debug-manual-card-id:9900000000000001}")
    private String debugManualCardId;

    @Value("${alipay.card-pool.threshold:10}")
    private Integer threshold;

    @Value("${alipay.card-pool.batch-size:20}")
    private Integer batchSize;

    @Autowired
    private AlipayUserInfoMapper alipayUserInfoMapper;

    @Autowired
    private AlipayRegLogMapper alipayRegLogMapper;

    @Autowired
    private AlipayCardPoolMapper alipayCardPoolMapper;

    @Autowired
    private TicketClient ticketClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request) {
        AlipayTripRequestApplicationRespDTO response = new AlipayTripRequestApplicationRespDTO();
        try {
            log.info("接收到支付宝出行-开卡申请报文: {}", JSON.toJSONString(request));

            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("开卡申请参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            AlipayUserInfo existed = alipayUserInfoMapper.selectByThirdUserId(thirdUserId);
            if (existed != null) {
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("用户已开户");
                response.setCardId(existed.getCardId());
                response.setCardType(existed.getCardType());
                response.setStatus(existed.getStatus());
                log.warn("用户已开户, thirdUserId={}, cardId={}, cardType={}",
                        existed.getThirdUserId(), existed.getCardId(), existed.getCardType());
                return response;
            }

            AlipayCardPool cardPool = allocateNextCard(thirdUserId);
            if (cardPool == null) {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("无可分配卡资源");
                log.warn("无可分配卡资源, thirdUserId={}", thirdUserId);
                return response;
            }

            String requestSeq = UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
            String cardId = cardPool.getCardId();

            AlipayUserInfo userInfo = buildAlipayUserInfo(request, cardId);
            alipayUserInfoMapper.insert(userInfo);

            AlipayRegLog regLog = buildAlipayRegLog(request, cardId, requestSeq);
            alipayRegLogMapper.insert(regLog);

            RegisterRideStatusReqDTO registerReq = new RegisterRideStatusReqDTO();
            registerReq.setThirdUserId(thirdUserId);
            registerReq.setCardId(cardId);
            registerReq.setCardType(request.getCardType());
            RegisterRideStatusRespDTO registerResp = ticketClient.registerRideStatus(registerReq);
            if (registerResp == null || !FepAppErrorCodeEnum.SUCCESS.getCode().equals(registerResp.getRetCode())) {
                throw new RuntimeException("调用 ticket-server 注册乘车状态失败, thirdUserId=" + thirdUserId + ", cardId=" + cardId + ", resp=" + registerResp);
            }

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            response.setCardId(cardId);
            response.setCardType(request.getCardType());
            response.setStatus("ACTIVE");
            log.info("开卡申请成功, thirdUserId={}, cardId={}, cardType={}", thirdUserId, cardId, request.getCardType());
            return response;
        } catch (Exception e) {
            log.error("开卡申请异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("系统内部错误");
            return response;
        }
    }

    @Override
    public AlipayUserInfoDTO selectByThirdUserId(String thirdUserId) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty()) {
            return null;
        }
        AlipayUserInfo userInfo = alipayUserInfoMapper.selectByThirdUserId(thirdUserId.trim());
        if (userInfo == null) {
            return null;
        }
        AlipayUserInfoDTO dto = new AlipayUserInfoDTO();
        dto.setThirdUserId(userInfo.getThirdUserId());
        dto.setCardId(userInfo.getCardId());
        dto.setCardType(userInfo.getCardType());
        dto.setThirdPayId(userInfo.getThirdPayId());
        dto.setReqContractNo(userInfo.getReqContractNo());
        dto.setChannel(userInfo.getChannel());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty()) {
            log.warn("更新用户支付通道失败, thirdUserId 为空");
            return false;
        }
        try {
            AlipayUserInfo userInfo = new AlipayUserInfo();
            userInfo.setThirdUserId(thirdUserId);
            userInfo.setThirdPayId(thirdPayId);
            userInfo.setReqContractNo(reqContractNo);
            int updated = alipayUserInfoMapper.updatePaymentChannel(userInfo);
            if (updated > 0) {
                log.info("更新用户支付通道成功, thirdUserId={}, thirdPayId={}, reqContractNo={}",
                        thirdUserId, thirdPayId, reqContractNo);
                return true;
            }
            log.warn("更新用户支付通道失败，用户不存在, thirdUserId={}", thirdUserId);
            return false;
        } catch (Exception e) {
            log.error("更新用户支付通道异常, thirdUserId={}", thirdUserId, e);
            return false;
        }
    }

    @Override
    public void monitorCardPool() {
        long unusedCount = alipayCardPoolMapper.countUnusedCards();
        log.info("卡号池监控, 当前未使用卡号数量={}", unusedCount);
        if (unusedCount <= threshold) {
            requestLogicalCardNo();
        }
    }

    @Override
    public void requestLogicalCardNo() {
        int requestCount = batchSize;
        log.info("准备申请逻辑卡号, count={}", requestCount);

        List<AlipayCardPool> ticketNoList = new ArrayList<>(requestCount);
        for (int i = 0; i < requestCount; i++) {
            AlipayCardPool ticketNo = new AlipayCardPool();
            ticketNo.setCardId(buildCardId());
            ticketNo.setInsertTms(LocalDateTime.now());
            ticketNoList.add(ticketNo);
        }
        if (!ticketNoList.isEmpty()) {
            alipayCardPoolMapper.batchInsert(ticketNoList);
            log.info("批量生成卡号成功, count={}", ticketNoList.size());
        }
    }

    private AlipayCardPool allocateNextCard(String thirdUserId) {
        if (debugManualAllocate && debugManualCardId != null && !debugManualCardId.isEmpty()) {
            AlipayCardPool cardPool = new AlipayCardPool();
            cardPool.setCardId(debugManualCardId);
            cardPool.setStatus("ASSIGNED");
            cardPool.setThirdUserId(thirdUserId);
            return cardPool;
        }

        monitorCardPool();

        for (int i = 0; i < 3; i++) {
            AlipayCardPool nextCard = alipayCardPoolMapper.selectUnusedCard();
            if (nextCard == null) {
                requestLogicalCardNo();
                nextCard = alipayCardPoolMapper.selectUnusedCard();
            }
            if (nextCard == null) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = alipayCardPoolMapper.updateAllocateCard(nextCard.getCardId(), thirdUserId, now);
            if (updated > 0) {
                nextCard.setThirdUserId(thirdUserId);
                nextCard.setRegTms(now);
                return nextCard;
            }
        }
        return null;
    }

    private String buildCardId() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    private String validateRequest(AlipayTripRequestApplicationReqDTO request) {
        if (request == null) {
            return "请求参数为空";
        }
        if (request.getThirdUserId() == null || request.getThirdUserId().trim().isEmpty()) {
            return "thirdUserId 不能为空";
        }
        if (request.getCardType() == null || request.getCardType().trim().isEmpty()) {
            return "cardType 不能为空";
        }
        if (request.getCardIssueCode() == null || request.getCardIssueCode().trim().isEmpty()) {
            return "cardIssueCode 不能为空";
        }
        return null;
    }

    private AlipayUserInfo buildAlipayUserInfo(AlipayTripRequestApplicationReqDTO request, String cardId) {
        AlipayUserInfo userInfo = new AlipayUserInfo();
        userInfo.setThirdUserId(request.getThirdUserId().trim());
        userInfo.setCardId(cardId);
        userInfo.setCardType(request.getCardType());
        userInfo.setCardIssueCode(request.getCardIssueCode());
        userInfo.setMsisdn(request.getMsisdn());
        userInfo.setExtend1(request.getExtend1());
        userInfo.setExtend2(request.getExtend2());
        userInfo.setChannel("ALIPAY");
        userInfo.setStatus("ACTIVE");
        userInfo.setDeleteFlag("0");
        userInfo.setVersion("1");
        return userInfo;
    }

    private AlipayRegLog buildAlipayRegLog(AlipayTripRequestApplicationReqDTO request, String cardId, String requestSeq) {
        AlipayRegLog regLog = new AlipayRegLog();
        regLog.setRequestSeq(requestSeq);
        regLog.setThirdUserId(request.getThirdUserId().trim());
        regLog.setCardId(cardId);
        regLog.setCardType(request.getCardType());
        regLog.setCardIssueCode(request.getCardIssueCode());
        regLog.setChannel("ALIPAY");
        regLog.setMsisdn(request.getMsisdn());
        regLog.setExtend1(request.getExtend1());
        regLog.setExtend2(request.getExtend2());
        regLog.setRequestBody(JSON.toJSONString(request));
        regLog.setResultCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        regLog.setResultMsg("成功");
        regLog.setVersion("1");
        return regLog;
    }
}
