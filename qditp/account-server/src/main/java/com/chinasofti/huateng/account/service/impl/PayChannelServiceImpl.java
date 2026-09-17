package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.domain.ChannelBindingRule;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.entity.UserPayChannel;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.mapper.UserPayChannelMapper;
import com.chinasofti.huateng.account.service.AccountArchiveService;
import com.chinasofti.huateng.account.service.PayChannelService;

/**
 * 支付通道的增删改查实现，见 {@link PayChannelService}。
 */
@Service
public class PayChannelServiceImpl implements PayChannelService {
    private static final Logger log = LoggerFactory.getLogger(PayChannelServiceImpl.class);

    /**
     * {@code USER_ITP_REG_LOG.OPER_TYPE} 的解约取值。
     */
    private static final int OPER_TYPE_REMOVE_PAY_CHANNEL = 1;

    /**
     * IF8A-77 反查签约信息取 PAY_ACCOUNT_ID 用；本类唯一的跨服务调用。
     */
    private final PaySignClient paySignClient;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserItpRegLogMapper userItpRegLogMapper;

    private final UserPayChannelMapper userPayChannelMapper;

    /**
     * 销户归档的协作者。
     */
    private final AccountArchiveService accountArchiveService;

    /**
     * 构造器注入（ADR-D37）。
     */
    public PayChannelServiceImpl(PaySignClient paySignClient,
                                 UserItpRegInfoMapper userItpRegInfoMapper,
                                 UserItpRegLogMapper userItpRegLogMapper,
                                 UserPayChannelMapper userPayChannelMapper,
                                 AccountArchiveService accountArchiveService) {
        this.paySignClient = paySignClient;
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userItpRegLogMapper = userItpRegLogMapper;
        this.userPayChannelMapper = userPayChannelMapper;
        this.accountArchiveService = accountArchiveService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestAddPayChannelResult requestAddPayChannel(RequestAddPayChannelReqDTO request) {
        RequestAddPayChannelResult response = new RequestAddPayChannelResult();
        try {
            log.info("开始处理IF8A-23请求添加支付通道, request={}", JSON.toJSONString(request));
            String validMsg = validateAddPayChannelRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-23参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String issueCardType = CardTypeMapping.toIssueCardType(request.getCardType().trim());
            String thirdUserId = request.getThirdUserId().trim();
            String channel = request.getChannel().trim();

            UserItpRegInfo regInfo;
            if (ChannelBindingRule.isWallet(channel)) {
                regInfo = userItpRegInfoMapper.selectActiveByThirdUserIdAndCardIdAndCardType(
                        thirdUserId, request.getCardId().trim(), issueCardType);
                if (regInfo == null || !regInfo.isActive()) {
                    response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                    response.setRetMsg("钱包 cardId 或 cardType 与开户信息不匹配");
                    log.warn("IF8A-23钱包卡信息不匹配, request={}, issueCardType={}",
                            JSON.toJSONString(request), issueCardType);
                    return response;
                }
            } else {
                regInfo = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
                if (regInfo == null || !regInfo.isActive()) {
                    response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                    log.warn("IF8A-23未找到有效用户账户, thirdUserId={}", request.getThirdUserId());
                    return response;
                }
                log.info("请求转换过后的cardType:{},已注册信息cardType:{},比对结果:{}", issueCardType,
                        regInfo.getCardType(), issueCardType.equals(regInfo.getCardType()));
            }

            UserPayChannel existed = userPayChannelMapper.selectByThirdUserIdAndCardTypeAndChannel(
                    thirdUserId,
                    issueCardType,
                    channel);
            if (existed != null) {
                response.setRetCode(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getMsg());
                log.warn("IF8A-23支付通道已存在, thirdUserId={}, cardType={}, channel={}",
                        request.getThirdUserId(), issueCardType, request.getChannel());
                return response;
            }

            UserPayChannel record = buildUserPayChannel(request);
            record.setCardType(issueCardType);
            userPayChannelMapper.insert(record);

            if (ChannelBindingRule.isWallet(channel)) {
                regInfo.setThirdPayId(record.getThirdPayId());
                regInfo.setChannel(channel);
                regInfo.setReqContractNo(record.getReqContractNo());
                if (userItpRegInfoMapper.updateDefaultPayChannelById(regInfo) <= 0) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
                    response.setRetMsg("钱包默认支付通道设置失败");
                    return response;
                }
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-23添加支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}, thirdPayId={}, reqContractNo={}",
                    record.getThirdUserId(), record.getCardId(), record.getCardType(),
                    record.getChannel(), record.getThirdPayId(), record.getReqContractNo());
            scheduleWalletContractSignup(record);
            scheduleBackfillPayAccountId(record.getReqContractNo());
            return response;
        } catch (Exception e) {
            if (isDuplicateKeyViolation(e)) {
                response.setRetCode(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getMsg());
                log.warn("IF8A-23支付通道唯一约束冲突, request={}", JSON.toJSONString(request), e);
                return response;
            }
            log.error("处理IF8A-23请求添加支付通道异常, request={}", JSON.toJSONString(request), e);
            markRollbackOnly();
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     */
    private boolean isDuplicateKeyViolation(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(RequestSetDefaultPayChannelReqDTO request) {
        RequestSetDefaultPayChannelResult response = new RequestSetDefaultPayChannelResult();
        try {
            log.info("开始处理IF8A-24请求设置默认支付通道, request={}", JSON.toJSONString(request));
            String validMsg = validateSetDefaultPayChannelRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-24参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardId = request.getCardId().trim();
            String cardType = CardTypeMapping.toIssueCardType(request.getCardType().trim());
            String channel = request.getChannel().trim();

            UserItpRegInfo regInfo = userItpRegInfoMapper
                    .selectActiveByThirdUserIdAndCardIdAndCardType(thirdUserId, cardId, cardType);
            if (regInfo == null || !regInfo.isActive()) {
                if (userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId) == null) {
                    response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                    log.warn("IF8A-24未找到有效用户账户, thirdUserId={}", thirdUserId);
                } else {
                    response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                    response.setRetMsg("cardId或cardType与开户信息不匹配");
                    log.warn("IF8A-24卡信息不匹配, request={}, cardId={}, cardType={}",
                            JSON.toJSONString(request), cardId, cardType);
                }
                return response;
            }

            UserPayChannel payChannel = userPayChannelMapper.selectByThirdUserIdAndCardTypeAndChannel(thirdUserId, cardType, channel);
            if (payChannel == null) {
                response.setRetCode(AccountErrorCodeEnum.PAY_CHANNEL_NOT_FOUND.getCode());
                response.setRetMsg(AccountErrorCodeEnum.PAY_CHANNEL_NOT_FOUND.getMsg());
                log.warn("IF8A-24支付通道不存在, thirdUserId={}, cardType={}, channel={}", thirdUserId, cardType, channel);
                return response;
            }

            regInfo.setThirdPayId(payChannel.getThirdPayId());
            regInfo.setChannel(payChannel.getChannel());
            regInfo.setReqContractNo(payChannel.getReqContractNo());
            int updated = userItpRegInfoMapper.updateDefaultPayChannelById(regInfo);
            if (updated <= 0) {
                response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
                response.setRetMsg(AccountErrorCodeEnum.FAIL.getMsg());
                log.warn("IF8A-24更新默认支付通道失败, thirdUserId={}, regInfoId={}", thirdUserId, regInfo.getId());
                return response;
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-24设置默认支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}, thirdPayId={}, reqContractNo={}",
                    thirdUserId, cardId, cardType, payChannel.getChannel(), payChannel.getThirdPayId(), payChannel.getReqContractNo());
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-24请求设置默认支付通道异常, request={}", JSON.toJSONString(request), e);
            markRollbackOnly();
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * IF8A-77。
     */
    @Override
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(RequestUpdateChannelDefaultContractReqDTO request) {
        RequestUpdateChannelDefaultContractResult response = new RequestUpdateChannelDefaultContractResult();
        try {
            log.info("开始处理IF8A-77更换第三方渠道码默认支付方式, request={}", JSON.toJSONString(request));
            
            String validMsg = validateUpdateChannelDefaultContractRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-77参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String channel = request.getChannel().trim();
            String cardIssueCode = request.getCardIssueCode().trim();
            String regSignSeq = request.getRegSignSeq().trim();

            UserItpRegInfo regInfo = userItpRegInfoMapper.selectByThirdUserIdAndCardIssueCodeAndCompanionFlag(
                    thirdUserId, cardIssueCode, "C");
            if (regInfo == null || !regInfo.isActive()) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("IF8A-77未找到有效第三方渠道用户, thirdUserId={}, cardIssueCode={}", thirdUserId, cardIssueCode);
                return response;
            }

            PaySignInfoDTO signInfo = paySignClient.querySignInfoBySeq(regSignSeq);
            if (signInfo == null || !StringUtils.hasText(signInfo.getPayAccountId())) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_SIGN_DATA.getCode());
                response.setRetMsg("签约信息不存在或PAY_ACCOUNT_ID为空");
                log.warn("IF8A-77签约信息不存在, regSignSeq={}", regSignSeq);
                return response;
            }

            regInfo.setThirdPayId(signInfo.getPayAccountId());
            regInfo.setChannel(channel);
            regInfo.setReqContractNo(regSignSeq);
            int updated = userItpRegInfoMapper.updateChannelDefaultContractById(regInfo);
            if (updated <= 0) {
                response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
                response.setRetMsg(AccountErrorCodeEnum.FAIL.getMsg());
                log.warn("IF8A-77更新用户默认支付方式失败, thirdUserId={}, regInfoId={}", thirdUserId, regInfo.getId());
                return response;
            }

            syncPayAccountIdToChannelQuietly(regSignSeq, signInfo.getPayAccountId());

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-77更换第三方渠道码默认支付方式成功, thirdUserId={}, channel={}, cardIssueCode={}, regSignSeq={}, payAccountId={}",
                    thirdUserId, channel, cardIssueCode, regSignSeq, signInfo.getPayAccountId());
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-77更换第三方渠道码默认支付方式异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * IF8A-75 删除支付通道。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestRemovePayChannelResult requestRemovePayChannel(RequestRemovePayChannelReqDTO request) {
        return doRemovePayChannel(request);
    }

    /**
     * 钱包协议 requestAgreeRelease：解绑语义与 IF8A-75 删除支付通道完全一致，共用同一段实现。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestRemovePayChannelResult requestAgreeRelease(RequestRemovePayChannelReqDTO request) {
        return doRemovePayChannel(request);
    }

    /**
     * 删除支付通道的唯一实现（ADR-D39 由 {@code requestRemovePayChannel} 原地抽出，逻辑一字未改）。
     */
    private RequestRemovePayChannelResult doRemovePayChannel(RequestRemovePayChannelReqDTO request) {
        RequestRemovePayChannelResult response = new RequestRemovePayChannelResult();
        try {
            log.info("开始处理删除支付通道, request={}", JSON.toJSONString(request));
            String validMsg = validateRemovePayChannelRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("删除支付通道参数校验失败, msg={}, request={}", validMsg, JSON.toJSONString(request));
                return response;
            }
            request.setCardType(CardTypeMapping.toIssueCardType(request.getCardType().trim()));

            String thirdUserId = request.getThirdUserId().trim();
            String cardId = request.getCardId().trim();
            String cardType = request.getCardType().trim();
            String channel = request.getChannel().trim();

            UserItpRegInfo regInfo = userItpRegInfoMapper
                    .selectActiveByThirdUserIdAndCardIdAndCardType(thirdUserId, cardId, cardType);
            if (regInfo == null || !regInfo.isActive()) {
                UserItpRegInfo canceledRegInfo = userItpRegInfoMapper
                        .selectAnyByThirdUserIdAndCardIdAndCardType(thirdUserId, cardId, cardType);
                if (canceledRegInfo == null) {
                    response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                    log.warn("删除支付通道未找到该卡的开户记录, thirdUserId={}, cardId={}, cardType={}",
                            thirdUserId, cardId, cardType);
                    return response;
                }
                regInfo = canceledRegInfo;
                log.info("删除支付通道命中已注销的开户记录，按销户后清理处理, thirdUserId={}, cardId={}, cardType={}, delYn={}",
                        thirdUserId, cardId, cardType, regInfo.getDelYn());
            }

            int removed = userPayChannelMapper.deleteByThirdUserIdAndCardTypeAndChannel(thirdUserId, cardType, channel);
            boolean channelLeftUncleaned = false;
            if (removed == 0) {
                int remaining = userPayChannelMapper.countByThirdUserId(thirdUserId);
                if (remaining > 0) {
                    log.error("删除支付通道影响0行但该用户仍有{}条通道行，判定为键不匹配（疑似 CARD_TYPE 口径混用），"
                                    + "本地将残留通道且无补偿路径, thirdUserId={}, cardId={}, cardType={}, channel={}",
                            remaining, thirdUserId, cardId, cardType, channel);
                    channelLeftUncleaned = true;
                } else {
                    log.warn("删除支付通道影响0行且该用户已无通道行，按幂等成功返回, "
                                    + "thirdUserId={}, cardId={}, cardType={}, channel={}",
                            thirdUserId, cardId, cardType, channel);
                }
            }
            if (channel.equals(regInfo.getChannel())) {
                if (userItpRegInfoMapper.clearDefaultPayChannelById(regInfo.getId()) == 0) {
                    log.warn("清空注册信息默认支付通道影响0行（并发归档？）, thirdUserId={}, regInfoId={}",
                            thirdUserId, regInfo.getId());
                }
                log.info("删除支付通道时清空注册信息默认支付通道, thirdUserId={}, cardType={}, channel={}",
                        thirdUserId, cardType, channel);

                UserItpRegLog regLog = new UserItpRegLog();
                regLog.setCardId(cardId);
                regLog.setCardType(cardType);
                regLog.setThirdUserId(thirdUserId);
                regLog.setMsisdn(regInfo.getMsisdn());
                regLog.setOperDateTime(LocalDateTime.now());
                regLog.setOperType(OPER_TYPE_REMOVE_PAY_CHANNEL);
                userItpRegLogMapper.insert(regLog);
            }

            accountArchiveService.archiveIfLastChannelRemoved(thirdUserId);

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            if (channelLeftUncleaned) {
                log.warn("删除支付通道按幂等返回0000，但本地通道未清理（见上条 ERROR）, "
                                + "thirdUserId={}, cardId={}, cardType={}, channel={}",
                        thirdUserId, cardId, cardType, channel);
            } else {
                log.info("删除支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}",
                        thirdUserId, cardId, cardType, channel);
            }
            return response;
        } catch (Exception e) {
            log.error("处理删除支付通道异常, request={}", JSON.toJSONString(request), e);
            markRollbackOnly();
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * catch 内显式标记事务回滚；不在事务上下文里时静默跳过。
     */
    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
        }
    }

    /**
     * 把支付域返回的 {@code PAY_ACCOUNT_ID} 回写到 {@code APP_USER_PAY_CHANNEL}（ADR-D30）。
     */
    private void syncPayAccountIdToChannelQuietly(String reqContractNo, String payAccountId) {
        try {
            int updated = userPayChannelMapper.updatePayAccountIdByReqContractNo(
                    reqContractNo, payAccountId, LocalDateTime.now());
            if (updated <= 0) {
                log.info("IF8A-77按签约流水未命中支付通道行，PAY_ACCOUNT_ID未回写, regSignSeq={}", reqContractNo);
            } else if (updated > 1) {
                log.warn("IF8A-77按签约流水回写PAY_ACCOUNT_ID命中多行, regSignSeq={}, updated={}", reqContractNo, updated);
            }
        } catch (Exception e) {
            log.warn("IF8A-77回写支付通道PAY_ACCOUNT_ID失败，不影响本次变更结果, regSignSeq={}", reqContractNo, e);
        }
    }

    /**
     * IF8A-23 建出通道行后，安排一次「向支付域反查 {@code PAY_ACCOUNT_ID} 并回写」。
     */
    private void scheduleBackfillPayAccountId(String reqContractNo) {
        if (!StringUtils.hasText(reqContractNo)) {
            return;
        }
        String seq = reqContractNo.trim();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            backfillPayAccountIdFromPayDomain(seq);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                backfillPayAccountIdFromPayDomain(seq);
            }
        });
    }

    /**
     * IF8A-23 钱包渠道建出通道行后，安排一次「向支付中心发起代扣签约」。
     */
    private void scheduleWalletContractSignup(UserPayChannel record) {
        if (record == null || !ChannelBindingRule.isWallet(record.getChannel())) {
            return;
        }
        UserPayChannel snapshot = record;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            signWalletContractAtPayCenter(snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                signWalletContractAtPayCenter(snapshot);
            }
        });
    }

    /**
     * 向支付域发起钱包代扣签约（IF8A-16 → 支付中心 §2.2 contract）。
     */
    private void signWalletContractAtPayCenter(UserPayChannel record) {
        try {
            RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
            request.setThirdUserId(record.getThirdUserId());
            request.setPayChannelCode(record.getChannel());
            request.setRequestSignSeq(record.getReqContractNo());
            request.setPayUserId(record.getThirdPayId());
            request.setDisplayAccount(maskPayId(record.getThirdPayId()));
            RequestSignInfoResult result = paySignClient.requestSignInfo(request);
            if (result == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(result.getRetCode())) {
                log.warn("IF8A-23钱包代扣签约未成功，通道已建但扣款尚不可用, thirdUserId={}, reqContractNo={}, result={}",
                        record.getThirdUserId(), record.getReqContractNo(),
                        result == null ? "null" : result.getRetCode() + "/" + result.getRetMsg());
                return;
            }
            log.info("IF8A-23钱包代扣签约已发起, thirdUserId={}, reqContractNo={}",
                    record.getThirdUserId(), record.getReqContractNo());
        } catch (Throwable e) {
            log.warn("IF8A-23钱包代扣签约异常，不影响已提交的加通道结果, thirdUserId={}, reqContractNo={}",
                    record.getThirdUserId(), record.getReqContractNo(), e);
        }
    }

    /**
     * 钱包支付账户标识脱敏：保留前 4 后 4，中间固定 4 个星号；长度不足 8 位时整串打星。
     */
    private String maskPayId(String payId) {
        if (!StringUtils.hasText(payId)) {
            return null;
        }
        String trimmed = payId.trim();
        if (trimmed.length() < 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 向支付域按签约流水反查 {@code PAY_ACCOUNT_ID} 并回写本地通道行。
     */
    private void backfillPayAccountIdFromPayDomain(String reqContractNo) {
        try {
            PaySignInfoDTO signInfo = paySignClient.querySignInfoBySeq(reqContractNo);
            if (signInfo == null || !StringUtils.hasText(signInfo.getPayAccountId())) {
                log.info("IF8A-23反查支付域未取到PAY_ACCOUNT_ID，通道行该列留空, reqContractNo={}", reqContractNo);
                return;
            }
            syncPayAccountIdToChannelQuietly(reqContractNo, signInfo.getPayAccountId());
        } catch (Throwable e) {
            log.warn("IF8A-23反查支付域回写PAY_ACCOUNT_ID异常，不影响已提交的加通道结果, reqContractNo={}",
                    reqContractNo, e);
        }
    }

    /**
     * IF8A-23 添加通道的入参校验。
     */
    private String validateAddPayChannelRequest(RequestAddPayChannelReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        return ChannelBindingRule.validateAddBinding(request.getThirdUserId(), request.getCardId(),
                request.getCardType(), request.getChannel(), request.getThirdPayId());
    }

    private String validateSetDefaultPayChannelRequest(RequestSetDefaultPayChannelReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        return ChannelBindingRule.validateBindingFields(request.getThirdUserId(), request.getCardId(),
                request.getCardType(), request.getChannel());
    }

    /**
     * IF8A-77 的字段集合是 {@code cardIssueCode} / {@code regSignSeq}，与四要素不是同一组，
     * <b>刻意不走 {@link ChannelBindingRule}</b>——硬凑会得到一个带开关的四不像。
     */
    private String validateUpdateChannelDefaultContractRequest(RequestUpdateChannelDefaultContractReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getChannel())) {
            return "channel不能为空";
        }
        if (!StringUtils.hasText(request.getCardIssueCode())) {
            return "cardIssueCode不能为空";
        }
        if (!StringUtils.hasText(request.getRegSignSeq())) {
            return "regSignSeq不能为空";
        }
        return null;
    }

    private String validateRemovePayChannelRequest(RequestRemovePayChannelReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        return ChannelBindingRule.validateBindingFields(request.getThirdUserId(), request.getCardId(),
                request.getCardType(), request.getChannel());
    }

    /**
     * 组装 {@code APP_USER_PAY_CHANNEL} 行。
     */
    private UserPayChannel buildUserPayChannel(RequestAddPayChannelReqDTO request) {
        UserPayChannel record = new UserPayChannel();
        record.setThirdUserId(request.getThirdUserId().trim());
        record.setCardId(request.getCardId().trim());
        record.setCardType(CardTypeMapping.toIssueCardType(request.getCardType().trim()));
        record.setChannel(request.getChannel().trim());
        record.setThirdPayId(request.getThirdPayId());
        record.setReqContractNo(request.getReqContractNo());
        record.setStatus("ACTIVE");
        record.setCreateTms(LocalDateTime.now());
        record.setUpdateTms(LocalDateTime.now());
        return record;
    }
}
