package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.entity.UserPayChannel;
import com.chinasofti.huateng.account.mapper.UserPayChannelMapper;
import com.chinasofti.huateng.account.service.PayChannelInternalService;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付通道对内契约面的实现，见 {@link PayChannelInternalService}。
 */
@Service
public class PayChannelInternalServiceImpl implements PayChannelInternalService {
    private static final Logger log = LoggerFactory.getLogger(PayChannelInternalServiceImpl.class);

    private final UserPayChannelMapper userPayChannelMapper;

    /**
     * 构造器注入（ADR-D37）。
     */
    public PayChannelInternalServiceImpl(UserPayChannelMapper userPayChannelMapper) {
        this.userPayChannelMapper = userPayChannelMapper;
    }

    @Override
    public QueryPayChannelByContractResult queryPayChannelByContractNo(QueryPayChannelByContractReqDTO request) {
        QueryPayChannelByContractResult response = new QueryPayChannelByContractResult();
        String reqContractNo = request == null ? null : request.getReqContractNo();
        if (reqContractNo == null || reqContractNo.trim().isEmpty()) {
            response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("reqContractNo不能为空");
            return response;
        }
        try {
            UserPayChannel payChannel = userPayChannelMapper.selectByReqContractNo(reqContractNo.trim());
            if (payChannel == null) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("按签约流水号未查到支付通道, reqContractNo={}", reqContractNo);
                return response;
            }
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setThirdUserId(payChannel.getThirdUserId());
            response.setCardId(payChannel.getCardId());
            response.setCardType(payChannel.getCardType());
            response.setChannel(payChannel.getChannel());
            response.setThirdPayId(payChannel.getThirdPayId());
            response.setReqContractNo(payChannel.getReqContractNo());
            response.setStatus(payChannel.getStatus());
            return response;
        } catch (Exception e) {
            log.error("按签约流水号查询支付通道异常, reqContractNo={}", reqContractNo, e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 接收支付域推来的支付账号并回写（ADR-D32）。
     */
    @Override
    public CommonResult syncPayAccountId(SyncPayAccountIdReqDTO request) {
        CommonResult response = new CommonResult();
        String reqContractNo = request == null ? null : request.getReqContractNo();
        String payAccountId = request == null ? null : request.getPayAccountId();
        try {
            if (!StringUtils.hasText(reqContractNo) || !StringUtils.hasText(payAccountId)) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("reqContractNo与payAccountId均不能为空");
                log.warn("支付域回写PAY_ACCOUNT_ID参数校验失败, request={}", JSON.toJSONString(request));
                return response;
            }
            int updated = userPayChannelMapper.updatePayAccountIdByReqContractNo(
                    reqContractNo.trim(), payAccountId.trim(), LocalDateTime.now());
            if (updated <= 0) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg("未命中支付通道行");
                log.info("支付域回写PAY_ACCOUNT_ID未命中通道行（签约先于加通道属合法时序）, reqContractNo={}", reqContractNo);
                return response;
            }
            if (updated > 1) {
                log.warn("支付域回写PAY_ACCOUNT_ID命中多行, reqContractNo={}, updated={}", reqContractNo, updated);
            }
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            return response;
        } catch (Exception e) {
            log.error("支付域回写PAY_ACCOUNT_ID异常, reqContractNo={}", reqContractNo, e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }
}
