package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.entity.UserAccTicketNo;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.entity.UserPayChannel;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.mapper.UserPayChannelMapper;
import com.chinasofti.huateng.account.service.AccountApplicationService;
import com.chinasofti.huateng.account.service.CardPoolService;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AccountApplicationServiceImpl implements AccountApplicationService {
    private static final Logger log = LoggerFactory.getLogger(AccountApplicationServiceImpl.class);

    @Value("${account.card-pool.debug-manual-allocate:true}")
    private boolean debugManualAllocate;

    @Value("${account.card-pool.debug-manual-card-id:9900000000000001}")
    private String debugManualCardId;

    @Autowired
    private CardPoolService cardPoolService;

    @Autowired
    private UserItpRegInfoMapper userItpRegInfoMapper;

    @Autowired
    private UserItpRegLogMapper userItpRegLogMapper;

    @Autowired
    private UserPayChannelMapper userPayChannelMapper;

    @Autowired
    private TicketClient ticketClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestApplicationResult requestApplication(RequestApplicationReqDTO request) {
        RequestApplicationResult response = new RequestApplicationResult();
        try {
            log.info("开始处理IF8A-01请求开户, request={}", JSON.toJSONString(request));

            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-01参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            UserItpRegInfo existed = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
            if (existed != null && existed.getDelYn() != null && existed.getDelYn() == 1) {
                response.setRetCode(AccountErrorCodeEnum.ALREADY_REGISTERED.getCode());
                response.setRetMsg(AccountErrorCodeEnum.ALREADY_REGISTERED.getMsg());
                response.setCardId(existed.getCardId());
                response.setCardType(existed.getCardType());
                response.setSignType("00");
                response.setSign("");
                log.warn("IF8A-01用户已开户, thirdUserId={}, cardId={}, cardType={}",
                        existed.getThirdUserId(), existed.getCardId(), existed.getCardType());
                return response;
            }

            UserAccTicketNo ticketNo = allocateTicketNo(thirdUserId);
            if (ticketNo == null) {
                response.setRetCode(AccountErrorCodeEnum.NO_CARD_RESOURCE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_CARD_RESOURCE.getMsg());
                log.warn("IF8A-01无可分配卡资源, thirdUserId={}", thirdUserId);
                return response;
            }

            UserItpRegInfo regInfo = buildRegInfo(request, ticketNo.getCardId());
            userItpRegInfoMapper.insert(regInfo);

            UserItpRegLog regLog = buildRegLog(regInfo);
            userItpRegLogMapper.insert(regLog);

            RegisterRideStatusRespDTO ticketResponse = ticketClient.registerRideStatus(buildTicketRequest(regInfo));
            if (ticketResponse == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(ticketResponse.getRetCode())) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                response.setRetCode(AccountErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg(ticketResponse == null ? "ticket-server不可用" : ticketResponse.getRetMsg());
                log.error("IF8A-01调用ticket-server失败, thirdUserId={}, response={}", thirdUserId, JSON.toJSONString(ticketResponse));
                return response;
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setCardId(regInfo.getCardId());
            response.setCardType(regInfo.getCardType());
            response.setSignType("00");
            response.setSign("");
            log.info("IF8A-01开户成功, thirdUserId={}, cardId={}, cardType={}",
                    regInfo.getThirdUserId(), regInfo.getCardId(), regInfo.getCardType());
            return response;
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("处理IF8A-01请求开户异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(AlipayTripRequestApplicationReqDTO request) {
        AlipayTripRequestApplicationRespDTO response = new AlipayTripRequestApplicationRespDTO();
        try {
            log.info("开始处理支付宝出行-开卡申请, request={}", JSON.toJSONString(request));

            // 1. 参数校验
            String validMsg = validateAlipayTripRequestApplication(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("支付宝出行-开卡申请参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardIssueCode = request.getCardIssueCode().trim();

            // 2. 检查用户是否已在支付宝渠道开户
            UserItpRegInfo existed = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
            if (existed != null && existed.getDelYn() != null && existed.getDelYn() == 1) {
                if (cardIssueCode.equals(existed.getCardIssueCode())) {
                    response.setRetCode(AccountErrorCodeEnum.ALREADY_REGISTERED.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.ALREADY_REGISTERED.getMsg());
                    response.setCardId(existed.getCardId());
                    log.warn("支付宝出行-开卡申请用户已开户, thirdUserId={}, cardId={}, cardType={}",
                            existed.getThirdUserId(), existed.getCardId(), existed.getCardType());
                    return response;
                }
            }

            // 3. 分配卡号
            UserAccTicketNo ticketNo = allocateTicketNo(thirdUserId);
            if (ticketNo == null) {
                response.setRetCode(AccountErrorCodeEnum.NO_CARD_RESOURCE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_CARD_RESOURCE.getMsg());
                log.warn("支付宝出行-开卡申请无可分配卡资源, thirdUserId={}", thirdUserId);
                return response;
            }

            // 4. 构建注册信息
            UserItpRegInfo regInfo = buildAlipayTripRegInfo(request, ticketNo.getCardId());
            userItpRegInfoMapper.insert(regInfo);

            // 5. 构建流水
            UserItpRegLog regLog = buildAlipayTripRegLog(regInfo);
            userItpRegLogMapper.insert(regLog);

            // 6. 调用 ticket-server 注册乘车状态
            RegisterRideStatusRespDTO ticketResponse = ticketClient.registerRideStatus(buildAlipayTicketRequest(regInfo));
            if (ticketResponse == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(ticketResponse.getRetCode())) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                response.setRetCode(AccountErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg(ticketResponse == null ? "ticket-server不可用" : ticketResponse.getRetMsg());
                log.error("支付宝出行-开卡申请调用ticket-server失败, thirdUserId={}, response={}", thirdUserId, JSON.toJSONString(ticketResponse));
                return response;
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setCardId(regInfo.getCardId());
            log.info("支付宝出行-开卡申请成功, thirdUserId={}, cardId={}, cardType={}",
                    regInfo.getThirdUserId(), regInfo.getCardId(), regInfo.getCardType());
            return response;
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("处理支付宝出行-开卡申请异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
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

            UserItpRegInfo regInfo = userItpRegInfoMapper.selectActiveByThirdUserId(request.getThirdUserId().trim());
            if (regInfo == null || regInfo.getDelYn() == null || regInfo.getDelYn() != 1) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("IF8A-23未找到有效用户账户, thirdUserId={}", request.getThirdUserId());
                return response;
            }
            log.info("请求转换过后的cardType:{},已注册信息cardType:{},比对结果:{}",issueCardType,regInfo.getCardType(),issueCardType.equals(regInfo.getCardType()));
//            if (!issueCardType.equals(regInfo.getCardType())
//                    || !request.getCardId().trim().equals(regInfo.getCardId())) {
//                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
//                response.setRetMsg("cardId或cardType与开户信息不匹配");
//                log.warn("IF8A-23卡信息不匹配, request={}, regInfoCardId={}, regInfoCardType={}",
//                        JSON.toJSONString(request), regInfo.getCardId(), regInfo.getCardType());
//                return response;
//            }

            UserPayChannel existed = userPayChannelMapper.selectByThirdUserIdAndCardTypeAndChannel(
                    request.getThirdUserId().trim(),
                    issueCardType,
                    request.getChannel().trim());
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

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-23添加支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}, thirdPayId={}, reqContractNo={}",
                    record.getThirdUserId(), record.getCardId(), record.getCardType(),
                    record.getChannel(), record.getThirdPayId(), record.getReqContractNo());
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-23请求添加支付通道异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
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
            String cardType = CardTypeMapping.toIssueCardType(request.getCardType().trim());;
            String channel = request.getChannel().trim();

            UserItpRegInfo regInfo = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
            if (regInfo == null || regInfo.getDelYn() == null || regInfo.getDelYn() != 1) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("IF8A-24未找到有效用户账户, thirdUserId={}", thirdUserId);
                return response;
            }
            if (!cardType.equals(regInfo.getCardType()) || !cardId.equals(regInfo.getCardId())) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("cardId或cardType与开户信息不匹配");
                log.warn("IF8A-24卡信息不匹配, request={}, regInfoCardId={}, regInfoCardType={}",
                        JSON.toJSONString(request), regInfo.getCardId(), regInfo.getCardType());
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
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestRemovePayChannelResult requestRemovePayChannel(RequestRemovePayChannelReqDTO request) {
        request.setCardType(CardTypeMapping.toIssueCardType(request.getCardType().trim()));
        RequestRemovePayChannelResult response = new RequestRemovePayChannelResult();
        try {
            log.info("开始处理删除支付通道, request={}", JSON.toJSONString(request));
            String validMsg = validateRemovePayChannelRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardId = request.getCardId().trim();
            String cardType = request.getCardType().trim();
            String channel = request.getChannel().trim();

            UserItpRegInfo regInfo = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
            if (regInfo == null || regInfo.getDelYn() == null || regInfo.getDelYn() != 1) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("删除支付通道未找到有效用户账户, thirdUserId={}", thirdUserId);
                return response;
            }
            if (!cardType.equals(regInfo.getCardType()) || !cardId.equals(regInfo.getCardId())) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("cardId或cardType与开户信息不匹配");
                log.warn("删除支付通道卡信息不匹配, request={}, regInfoCardId={}, regInfoCardType={}",
                        JSON.toJSONString(request), regInfo.getCardId(), regInfo.getCardType());
                return response;
            }

            userPayChannelMapper.deleteByThirdUserIdAndCardTypeAndChannel(thirdUserId, cardType, channel);
            if (channel.equals(regInfo.getChannel())) {
                userItpRegInfoMapper.clearDefaultPayChannelById(regInfo.getId());
                log.info("删除支付通道时清空注册信息默认支付通道, thirdUserId={}, cardType={}, channel={}",
                        thirdUserId, cardType, channel);
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("删除支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}",
                    thirdUserId, cardId, cardType, channel);
            return response;
        } catch (Exception e) {
            log.error("处理删除支付通道异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    public QueryUserInfoResult queryUserInfo(QueryUserInfoReqDTO request) {
        String cardType =CardTypeMapping.toIssueCardType(request.getCardType().trim());
        QueryUserInfoResult response = new QueryUserInfoResult();
        try {
            log.info("开始处理查询用户信息, request={}", JSON.toJSONString(request));
            String validMsg = validateQueryUserInfoRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("查询用户信息参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            UserItpRegInfo regInfo = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
            if (regInfo == null || regInfo.getDelYn() == null || regInfo.getDelYn() != 1) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("查询用户信息未找到有效用户账户, thirdUserId={}", thirdUserId);
                return response;
            }
//            if (!request.getCardId().trim().equals(regInfo.getCardId()) || !cardType.equals(regInfo.getCardType())) {
//                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
//                response.setRetMsg("cardId或cardType与开户信息不匹配");
//                log.warn("查询用户信息卡信息不匹配, request={}, regInfoCardId={}, regInfoCardType={}",
//                        JSON.toJSONString(request), regInfo.getCardId(), regInfo.getCardType());
//                return response;
//            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setThirdUserId(regInfo.getThirdUserId());
            response.setCardId(regInfo.getCardId());
            response.setCardType(regInfo.getCardType());
            response.setChannel(regInfo.getChannel());
            response.setThirdPayId(regInfo.getThirdPayId());
            response.setReqContractNo(regInfo.getReqContractNo());
            log.info("查询用户信息成功, thirdUserId={}, cardId={}, cardType={}, channel={}, thirdPayId={}, reqContractNo={}",
                    regInfo.getThirdUserId(), regInfo.getCardId(), regInfo.getCardType(),
                    regInfo.getChannel(), regInfo.getThirdPayId(), regInfo.getReqContractNo());
            return response;
        } catch (Exception e) {
            log.error("处理查询用户信息异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    private UserAccTicketNo allocateTicketNo(String thirdUserId) {
        if (!debugManualAllocate) {
            return cardPoolService.allocateNextCard(thirdUserId);
        }
        UserAccTicketNo ticketNo = new UserAccTicketNo();
        String cardId = String.format("%014d%02d",
                System.currentTimeMillis() % 100000000000000L,
                (int) (Math.random() * 100));
        ticketNo.setCardId(cardId);
        ticketNo.setThirdUserId(thirdUserId);
        ticketNo.setInsertTms(LocalDateTime.now());
        ticketNo.setRegTms(LocalDateTime.now());
        log.info("调试模式启用，直接返回动态生成卡号, thirdUserId={}, cardId={}", thirdUserId, cardId);
        return ticketNo;
    }

    private String validateRequest(RequestApplicationReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!CardTypeMapping.isSupportedAppCardType(request.getCardType())) {
            return "cardType不支持";
        }
        if (!StringUtils.hasText(request.getMsisdn())) {
            return "msisdn不能为空";
        }
        return null;
    }

    private String validateAddPayChannelRequest(RequestAddPayChannelReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getChannel())) {
            return "channel不能为空";
        }
        return null;
    }

    private String validateSetDefaultPayChannelRequest(RequestSetDefaultPayChannelReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getChannel())) {
            return "channel不能为空";
        }
        return null;
    }

    private String validateQueryUserInfoRequest(QueryUserInfoReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }

    private String validateRemovePayChannelRequest(RequestRemovePayChannelReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getChannel())) {
            return "channel不能为空";
        }
        return null;
    }

    private UserItpRegInfo buildRegInfo(RequestApplicationReqDTO request, String cardId) {
        UserItpRegInfo regInfo = new UserItpRegInfo();
        regInfo.setCardId(cardId);
        regInfo.setCardType(CardTypeMapping.toIssueCardType(request.getCardType()));
        regInfo.setThirdUserId(request.getThirdUserId().trim());
        regInfo.setMsisdn(request.getMsisdn());
        regInfo.setRegTms(LocalDateTime.now());
        regInfo.setDelYn(1);
        regInfo.setUserName(request.getUserName());
        regInfo.setUserId(request.getUserId());
        regInfo.setCardIssueCode(request.getCardIssueCode());
        regInfo.setThirdPayId(request.getThirdPayId());
        regInfo.setChannel(request.getChannel());
        regInfo.setReqContractNo(request.getReqContractNo());
        return regInfo;
    }

    private UserItpRegLog buildRegLog(UserItpRegInfo regInfo) {
        UserItpRegLog regLog = new UserItpRegLog();
        regLog.setCardId(regInfo.getCardId());
        regLog.setCardType(regInfo.getCardType());
        regLog.setThirdUserId(regInfo.getThirdUserId());
        regLog.setMsisdn(regInfo.getMsisdn());
        regLog.setOperDateTime(LocalDateTime.now());
        regLog.setOperType(0);
        return regLog;
    }

    private RegisterRideStatusReqDTO buildTicketRequest(UserItpRegInfo regInfo) {
        RegisterRideStatusReqDTO request = new RegisterRideStatusReqDTO();
        request.setThirdUserId(regInfo.getThirdUserId());
        request.setCardId(regInfo.getCardId());
        request.setCardType(regInfo.getCardType());
        request.setMsisdn(regInfo.getMsisdn());
        request.setCardIssueCode(regInfo.getCardIssueCode());
        request.setChannel(regInfo.getChannel());
        request.setThirdPayId(regInfo.getThirdPayId());
        return request;
    }

    private UserPayChannel buildUserPayChannel(RequestAddPayChannelReqDTO request) {
        UserPayChannel record = new UserPayChannel();
        record.setThirdUserId(request.getThirdUserId().trim());
        record.setCardId(request.getCardId().trim());
        record.setCardType(request.getCardType().trim());
        record.setChannel(request.getChannel().trim());
        record.setThirdPayId(request.getThirdPayId());
        record.setReqContractNo(request.getReqContractNo());
        record.setStatus("ACTIVE");
        record.setCreateTms(LocalDateTime.now());
        record.setUpdateTms(LocalDateTime.now());
        return record;
    }

    private String validateAlipayTripRequestApplication(AlipayTripRequestApplicationReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!CardTypeMapping.isSupportedAppCardType(request.getCardType())) {
            return "cardType不支持";
        }
        if (!StringUtils.hasText(request.getMsisdn())) {
            return "msisdn不能为空";
        }
        if (!StringUtils.hasText(request.getCardIssueCode())) {
            return "cardIssueCode不能为空";
        }
        return null;
    }

    private UserItpRegInfo buildAlipayTripRegInfo(AlipayTripRequestApplicationReqDTO request, String cardId) {
        UserItpRegInfo regInfo = new UserItpRegInfo();
        regInfo.setCardId(cardId);
        regInfo.setCardType(CardTypeMapping.toIssueCardType(request.getCardType()));
        regInfo.setThirdUserId(request.getThirdUserId().trim());
        regInfo.setMsisdn(request.getMsisdn());
        regInfo.setRegTms(LocalDateTime.now());
        regInfo.setDelYn(1);
        regInfo.setCardIssueCode(request.getCardIssueCode().trim());
        // 开户阶段未提供，留空，后续签约时更新
        regInfo.setThirdPayId(null);
        regInfo.setChannel(null);
        regInfo.setReqContractNo(null);
        regInfo.setUserName(null);
        regInfo.setUserId(null);
        return regInfo;
    }

    private UserItpRegLog buildAlipayTripRegLog(UserItpRegInfo regInfo) {
        UserItpRegLog regLog = new UserItpRegLog();
        regLog.setCardId(regInfo.getCardId());
        regLog.setCardType(regInfo.getCardType());
        regLog.setThirdUserId(regInfo.getThirdUserId());
        regLog.setMsisdn(regInfo.getMsisdn());
        regLog.setOperDateTime(LocalDateTime.now());
        regLog.setOperType(0);
        return regLog;
    }

    private RegisterRideStatusReqDTO buildAlipayTicketRequest(UserItpRegInfo regInfo) {
        RegisterRideStatusReqDTO request = new RegisterRideStatusReqDTO();
        request.setThirdUserId(regInfo.getThirdUserId());
        request.setCardId(regInfo.getCardId());
        request.setCardType(regInfo.getCardType());
        request.setMsisdn(regInfo.getMsisdn());
        request.setCardIssueCode(regInfo.getCardIssueCode());
        request.setChannel(regInfo.getChannel());
        request.setThirdPayId(regInfo.getThirdPayId());
        return request;
    }
}
