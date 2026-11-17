package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.mapper.UserPayChannelMapper;
import com.chinasofti.huateng.account.service.AccountArchiveService;
import com.chinasofti.huateng.account.service.AccountCancelService;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class AccountCancelServiceImpl implements AccountCancelService {
    private static final Logger log = LoggerFactory.getLogger(AccountCancelServiceImpl.class);


    private static final int OPER_TYPE_USER_CANCEL = 2;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserItpRegLogMapper userItpRegLogMapper;


    private final UserPayChannelMapper userPayChannelMapper;


    private final AccountArchiveService accountArchiveService;


    private final GateTxnPayClient gateTxnPayClient;


    private final TransactionTemplate transactionTemplate;


    @Value("${app.user-cancel.check-unsettled:true}")
    private boolean checkUnsettledBeforeCancel;


    public AccountCancelServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper,
                                    UserItpRegLogMapper userItpRegLogMapper,
                                    UserPayChannelMapper userPayChannelMapper,
                                    AccountArchiveService accountArchiveService,
                                    GateTxnPayClient gateTxnPayClient,
                                    TransactionTemplate transactionTemplate) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userItpRegLogMapper = userItpRegLogMapper;
        this.userPayChannelMapper = userPayChannelMapper;
        this.accountArchiveService = accountArchiveService;
        this.gateTxnPayClient = gateTxnPayClient;
        this.transactionTemplate = transactionTemplate;
    }


    @Override
    public UserCancelResult userCancel(UserCancelReqDTO request) {
        UserCancelResult response = new UserCancelResult();
        String rawThirdUserId = request == null ? null : request.getThirdUserId();
        String thirdUserId = StringUtils.hasText(rawThirdUserId) ? rawThirdUserId.trim() : null;
        if (thirdUserId == null) {
            response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("thirdUserId不能为空");
            log.warn("IF8A-42销户参数校验失败, request={}", JSON.toJSONString(request));
            return response;
        }
        log.info("开始处理IF8A-42用户销户, thirdUserId={}", thirdUserId);

        List<UserItpRegInfo> activeCards = userItpRegInfoMapper.selectActiveListByThirdUserId(thirdUserId);
        if (activeCards == null || activeCards.isEmpty()) {
            accountArchiveService.tryArchiveAfterCancel(thirdUserId);
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-42销户无有效开户记录, 按已注销幂等返回成功, thirdUserId={}", thirdUserId);
            return response;
        }

        if (checkUnsettledBeforeCancel) {
            String rejectCode = checkUnsettledOrder(thirdUserId);
            if (rejectCode != null) {
                response.setRetCode(rejectCode);
                response.setRetMsg(rejectCode.equals(AccountErrorCodeEnum.UNSETTLED_ORDER_EXISTS.getCode())
                        ? AccountErrorCodeEnum.UNSETTLED_ORDER_EXISTS.getMsg()
                        : AccountErrorCodeEnum.ACC_INFO_QUERY_FAIL.getMsg());
                return response;
            }
        } else {
            log.warn("IF8A-42销户已关闭未结清订单校验(app.user-cancel.check-unsettled=false), thirdUserId={}", thirdUserId);
        }

        String channelCheckRejectCode = checkPayChannelCleared(thirdUserId);
        if (channelCheckRejectCode != null) {
            response.setRetCode(channelCheckRejectCode);
            response.setRetMsg(AccountErrorCodeEnum.PAY_CHANNEL_REMAINING.getMsg());
            return response;
        }

        try {
            Integer canceled = transactionTemplate.execute(status -> doCancelUserCards(thirdUserId, activeCards));
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-42销户成功, thirdUserId={}, 本次注销票卡数={}, 支付通道已清空",
                    thirdUserId, canceled);
            accountArchiveService.tryArchiveAfterCancel(thirdUserId);
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-42用户销户异常, thirdUserId={}", thirdUserId, e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }


    private String checkUnsettledOrder(String thirdUserId) {
        RequestUserAccInfoResult accInfo;
        try {
            RequestUserAccInfoReqDTO accInfoReq = new RequestUserAccInfoReqDTO();
            accInfoReq.setThirdUserId(thirdUserId);
            accInfo = gateTxnPayClient.requestUserAccInfo(accInfoReq);
        } catch (Exception e) {
            log.error("IF8A-42销户前查询账务信息异常, 拒绝销户, thirdUserId={}", thirdUserId, e);
            return AccountErrorCodeEnum.ACC_INFO_QUERY_FAIL.getCode();
        }
        if (accInfo == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(accInfo.getRetCode())) {
            log.warn("IF8A-42销户前查询账务信息未成功, 拒绝销户, thirdUserId={}, result={}",
                    thirdUserId, JSON.toJSONString(accInfo));
            return AccountErrorCodeEnum.ACC_INFO_QUERY_FAIL.getCode();
        }
        if (accInfo.getUnpaidCount() > 0 || accInfo.getFailureCount() > 0) {
            log.warn("IF8A-42销户被拒绝, 存在未结清订单, thirdUserId={}, unpaidCount={}, failureCount={}",
                    thirdUserId, accInfo.getUnpaidCount(), accInfo.getFailureCount());
            return AccountErrorCodeEnum.UNSETTLED_ORDER_EXISTS.getCode();
        }
        log.info("IF8A-42销户前账务校验通过, thirdUserId={}", thirdUserId);
        return null;
    }


    private String checkPayChannelCleared(String thirdUserId) {
        int remainingChannels = userPayChannelMapper.countByThirdUserId(thirdUserId);
        if (remainingChannels > 0) {
            log.warn("IF8A-42销户被拒绝, 存在未解绑支付渠道, thirdUserId={}, remainingChannels={}",
                    thirdUserId, remainingChannels);
            return AccountErrorCodeEnum.PAY_CHANNEL_REMAINING.getCode();
        }
        log.info("IF8A-42销户前支付通道校验通过, thirdUserId={}", thirdUserId);
        return null;
    }


    private int doCancelUserCards(String thirdUserId, List<UserItpRegInfo> activeCards) {
        LocalDateTime now = LocalDateTime.now();
        int canceled = userItpRegInfoMapper.updateCancelByThirdUserId(thirdUserId, now);
        if (canceled == 0) {
            throw new IllegalStateException("销户注销影响0行（并发销户？）, thirdUserId=" + thirdUserId);
        }
        for (UserItpRegInfo card : activeCards) {
            UserItpRegLog regLog = new UserItpRegLog();
            regLog.setCardId(card.getCardId());
            regLog.setCardType(card.getCardType());
            regLog.setThirdUserId(thirdUserId);
            regLog.setMsisdn(card.getMsisdn());
            regLog.setOperDateTime(now);
            regLog.setOperType(OPER_TYPE_USER_CANCEL);
            userItpRegLogMapper.insert(regLog);
        }
        return canceled;
    }
}
