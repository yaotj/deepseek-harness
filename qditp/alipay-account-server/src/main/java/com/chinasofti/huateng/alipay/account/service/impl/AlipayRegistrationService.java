package com.chinasofti.huateng.alipay.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.account.entity.AlipayRegLog;
import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayRegLogMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReserveResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付宝出行开卡申请（规范 3.69）—— 本模块**唯一**的开户实现（ADR-D143）。
 *
 * <p>2026-09-18 从 `AlipayAccountServiceImpl` 按聚合拆出：那个类原先 349 行、把
 * 开户 / 换号 / 只读查询三种职责混在一起，而依赖簇实际上是可分的 ——
 * `alipayRegLogMapper` / `ticketClient` / `cardPoolClient` / `transactionTemplate`
 * **只被开户用**，`alipayPhoneChangeLogMapper` 只被换号用，唯一相交的 `alipayUserInfoMapper`
 * 在两边一个写一个读。拆分判据的两条（依赖簇不相交 + 规模造成真实成本）同时成立。
 *
 * <p><b>四步顺序 NEVER 改</b>：查重 → 卡池预占 → 注册乘车状态 → 短事务落两表 → 卡池 confirm。
 * 三条口径同样 NEVER 改：
 * <ol>
 *   <li><b>本方法 NEVER 加 `@Transactional`</b> —— 链路里有三次 RPC，事务包住就是
 *       2026-08-26 那起生产事故的同型（行锁持有时长 = 对端响应时长），
 *       落库那两条 INSERT 只能收进 {@link TransactionTemplate}（同类自调用绕不过 Spring 代理）。</li>
 *   <li><b>失败 NEVER release 预占</b> —— 预占按 `businessId` 幂等、是共享资源而非本请求私有，
 *       抽回会打掉兄弟请求已发给用户的卡号；到期由 `sys_job` 107 回收。</li>
 *   <li><b>confirm 失败 NEVER 返成功</b> —— 卡号已写进 `ALIPAY_USER_INFO`，对上游必须报失败。</li>
 * </ol>
 */
@Service
public class AlipayRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(AlipayRegistrationService.class);

    /** 卡池预占的业务类型，与 ITP 侧的 `ACCOUNT_OPEN` 刻意不同名：两条链路的键空间必须分开。 */
    private static final String CARD_POOL_BUSINESS_TYPE = "ALIPAY_ACCOUNT_OPEN";

    private final AlipayUserInfoMapper alipayUserInfoMapper;
    private final AlipayRegLogMapper alipayRegLogMapper;
    private final TicketClient ticketClient;
    private final CardPoolClient cardPoolClient;
    private final TransactionTemplate transactionTemplate;

    public AlipayRegistrationService(AlipayUserInfoMapper alipayUserInfoMapper,
                                    AlipayRegLogMapper alipayRegLogMapper,
                                    TicketClient ticketClient,
                                    CardPoolClient cardPoolClient,
                                    TransactionTemplate transactionTemplate) {
        this.alipayUserInfoMapper = alipayUserInfoMapper;
        this.alipayRegLogMapper = alipayRegLogMapper;
        this.ticketClient = ticketClient;
        this.cardPoolClient = cardPoolClient;
        this.transactionTemplate = transactionTemplate;
    }

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

            CardPoolReserveResult cardPool = reserveCard(thirdUserId, request.getCardType());
            if (!cardPool.isSuccess()) {
                fillReserveFailure(response, cardPool, thirdUserId, request.getCardType());
                return response;
            }

            String requestSeq = UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
            String cardId = cardPool.getData().getCardNo();

            RegisterRideStatusReqDTO registerReq = new RegisterRideStatusReqDTO();
            registerReq.setThirdUserId(thirdUserId);
            registerReq.setCardId(cardId);
            registerReq.setCardType(request.getCardType());
            registerReq.setChannel(IssueChannelCodeEnum.ALIPAY.getCode());
            RegisterRideStatusRespDTO registerResp = ticketClient.registerRideStatus(registerReq);
            if (registerResp == null || !FepAppErrorCodeEnum.SUCCESS.getCode().equals(registerResp.getRetCode())) {
                throw new RuntimeException("调用 ticket-server 注册乘车状态失败, thirdUserId=" + thirdUserId
                        + ", cardId=" + cardId + ", resp=" + registerResp);
            }

            AlipayUserInfo userInfo = buildAlipayUserInfo(request, cardId);
            AlipayRegLog regLog = buildAlipayRegLog(request, cardId, requestSeq);
            transactionTemplate.executeWithoutResult(status -> {
                alipayUserInfoMapper.insert(userInfo);
                alipayRegLogMapper.insert(regLog);
            });

            CardPoolActionResult confirmResult = cardPoolClient.confirm(cardPool.getData().getReservationId(),
                    reservationBusinessId(thirdUserId, request.getCardType()));
            /* confirm 失败 NEVER 返成功、NEVER release 预占：卡号已写进 ALIPAY_USER_INFO；重试会命中幂等短路而不补 confirm，预占到期由 sys_job 107 回收，MUST 人工核对该卡号。 */
            if (!confirmResult.isSuccess()) {
                log.error("确认逻辑卡号预占失败，卡号已发给用户、开户数据 NEVER 回滚，对上游返失败等人工核对, thirdUserId={}, cardId={}, reservationId={}, outcome={}, msg={}",
                        thirdUserId, cardId, cardPool.getData().getReservationId(),
                        confirmResult.getOutcome(), confirmResult.getMessage());
                response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
                response.setRetMsg("卡号确认失败，请稍后重试");
                return response;
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

    /**
     * 向逻辑卡号池预占一个卡号。
     *
     * @param thirdUserId 支付宝用户标识，作为预占归属方
     * @param appCardType APP 侧票种码，内部转换为发卡票种码
     * @return 预占结果，非 SUCCESS 时 data 为空，由调用方按 outcome 分流
     */
    private CardPoolReserveResult reserveCard(String thirdUserId, String appCardType) {
        String cardType = CardTypeMapping.toIssueCardType(appCardType);
        CardPoolReservationReqDTO request = new CardPoolReservationReqDTO();
        request.setCardType(cardType);
        request.setBusinessType(CARD_POOL_BUSINESS_TYPE);
        request.setBusinessId(reservationBusinessId(thirdUserId, appCardType));
        request.setOwnerId(thirdUserId);
        return cardPoolClient.reserve(request);
    }

    /**
     * 按预占失败分类填充对外响应并落日志。
     *
     * @param response    待填充的对外响应
     * @param result      预占结果，outcome 必为非 SUCCESS
     * @param thirdUserId 支付宝用户标识，仅用于日志定位
     * @param cardType    APP 侧票种码，仅用于日志定位
     */
    private void fillReserveFailure(AlipayTripRequestApplicationRespDTO response, CardPoolReserveResult result,
                                    String thirdUserId, String cardType) {
        switch (result.getOutcome()) {
            case POOL_EMPTY -> {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("逻辑卡号池暂无可用卡号，请稍后重试");
                log.warn("逻辑卡号池已空, thirdUserId={}, cardType={}, msg={}",
                        thirdUserId, cardType, result.getMessage());
            }
            case REJECTED -> {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("票种配置错误，无法开卡");
                log.error("逻辑卡号预占被拒绝，票种不走卡池或归属冲突，重试无用, thirdUserId={}, cardType={}, msg={}",
                        thirdUserId, cardType, result.getMessage());
            }
            default -> {
                response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
                response.setRetMsg("发卡服务暂不可用，请稍后重试");
                log.error("调用逻辑卡号池预占失败, thirdUserId={}, cardType={}, outcome={}, msg={}",
                        thirdUserId, cardType, result.getOutcome(), result.getMessage());
            }
        }
    }

    /**
     * 卡池预占的业务流水号，决定发号的幂等边界（ADR-D143）。
     *
     * <p><b>长度前缀是必需的</b>：`thirdUserId` 由支付宝侧给定、可能含冒号，
     * 旧式 `前缀:用户:票种` 拼接在那种取值下会与另一组入参产出同一个键（串键 ⇒ 两个用户抢同一张卡）。
     * 格式与 ITP 侧 `AccountRegistrationServiceImpl.buildAccountOpenBusinessId`（ADR-D122）一致，
     * 但**保留 `ALIPAY_ACCOUNT_OPEN` 前缀把两条链路的键空间分开**，NEVER 与 ITP 侧合并。
     */
    private String reservationBusinessId(String thirdUserId, String appCardType) {
        String issueCardType = CardTypeMapping.toIssueCardType(appCardType);
        return CARD_POOL_BUSINESS_TYPE + ":" + thirdUserId.length() + ":" + thirdUserId + ":" + issueCardType;
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
        String issueCardType = CardTypeMapping.toIssueCardType(request.getCardType());
        if (CardTypeCodeEnum.isHceCard(issueCardType)) {
            return "HCE卡仅支持安全服务实时发卡";
        }
        if (CardTypeCodeEnum.isEmployeeCard(issueCardType)) {
            return "员工票仅支持APP静默开户";
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
        userInfo.setChannel(IssueChannelCodeEnum.ALIPAY.getCode());
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
        regLog.setChannel(IssueChannelCodeEnum.ALIPAY.getCode());
        regLog.setMsisdn(request.getMsisdn());
        regLog.setExtend1(request.getExtend1());
        regLog.setExtend2(request.getExtend2());
        regLog.setRequestBody(JSON.toJSONString(request));
        regLog.setResultCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        regLog.setResultMsg("成功");
        regLog.setVersion("1");
        regLog.setDeleteFlag("0");
        regLog.setCreateTime(LocalDateTime.now());
        regLog.setUpdateTime(LocalDateTime.now());
        return regLog;
    }
}
