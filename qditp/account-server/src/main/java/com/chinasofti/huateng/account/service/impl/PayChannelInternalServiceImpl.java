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
 *
 * <p>2026-09-11（ADR-D34）从 {@code PayChannelServiceImpl} 按调用方切出，<b>方法体逐行照搬、
 * 行为不变</b>：两处的 retCode 分支、日志文案、trim 时机、告警级别一并保留。
 * `PayChannelInternalContractTest` 里针对这两个入口的 11 个用例**未改一行**即在本类上通过，
 * 这就是「行为不变」的判据。</p>
 *
 * <p><b>本类只持 {@code UserPayChannelMapper} 一个协作者，且两个方法都不带 `@Transactional`</b>：
 * 一个是单条 select、一个是单条 update，单语句自身原子（同 ADR-D22 / ADR-D32 的判断）。
 * <b>NEVER 给本类加事务注解</b>，也 NEVER 在这里注入 {@code PaySignClient} —— 对内契约面
 * 反过来调支付域会立刻造出一条新的双向边。</p>
 */
@Service
public class PayChannelInternalServiceImpl implements PayChannelInternalService {
    private static final Logger log = LoggerFactory.getLogger(PayChannelInternalServiceImpl.class);

    private final UserPayChannelMapper userPayChannelMapper;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public PayChannelInternalServiceImpl(UserPayChannelMapper userPayChannelMapper) {
        this.userPayChannelMapper = userPayChannelMapper;
    }

    /*
     * 只读查询，不加 @Transactional：单条 select 不需要事务边界。
     * 供 pay-sign-server IF8A-75 在补建解约申请前反查 CARD_ID / CARD_TYPE，
     * 因为 APP_PAY_SIGN_INFO 的这两列全库为 NULL（2026-09-08 实测），
     * 而 APP_TERMINATION_REQUEST 的同名列是 NOT NULL。
     */
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
     * 接收支付域推来的支付账号并回写（ADR-D32）。见 {@link PayChannelInternalService#syncPayAccountId}。
     *
     * <p><b>不带 `@Transactional`</b>：只有一条 UPDATE，单语句自身原子，加事务无意义
     * （同 ADR-D22 对 IF8A-77 的判断）。</p>
     *
     * <p>复用 ADR-D30 已有的 {@code updatePayAccountIdByReqContractNo}，<b>不新增 mapper 语句</b>。
     * 与 {@code PayChannelServiceImpl.syncPayAccountIdToChannelQuietly} 的差别只在异常语义：
     * 那个是 IF8A-77 内的「附带回写、吞异常」，这个是**独立入口**，异常要变成 retCode 让支付域看见。
     * <b>那个私有方法留在 APP 契约面、NEVER 迁到本类</b> —— 它是 IF8A-77 的组成部分。</p>
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
                // REQ_CONTRACT_NO 无唯一索引，命中多行说明同一签约流水被复用，MUST 告警
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
