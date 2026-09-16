package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.utils.SignChannelUtils;
import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import com.chinasofti.huateng.ticket.mapper.StationInfoMapper;
import com.chinasofti.huateng.ticket.service.AppNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

import static com.chinasofti.huateng.model.enums.CardTypeCodeEnum.isHceCard;

/**
 * IF1A-01 闸机检票处理。
 *
 * <p>负责处理闸机传来的检票通知，包括：
 * <ul>
 *   <li>参数校验</li>
 *   <li>交易明细持久化（QRCodeTxnDetail）</li>
 *   <li>票卡状态更新（QRCodeStatus）</li>
 *   <li>HCE 卡数据回写</li>
 *   <li>行业数据推送</li>
 * </ul>
 */
@Component
public class GateTicketHandler {

    private static final Logger log = LoggerFactory.getLogger(GateTicketHandler.class);
    private static final String RET_SUCCESS = "0000";
    private static final String RET_INVALID_PARAM = "8001";

    @Value("${ticket.default-code-status:01}")
    private String defaultCodeStatus;

    @Autowired
    private QRCodeStatusMapper qrCodeStatusMapper;

    @Autowired
    private QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    @Autowired
    private StationInfoMapper stationInfoMapper;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AppNotifyService appNotifyService;

    @Autowired
    private DailyTicketClient dailyTicketClient;

    @Autowired
    private TransMerchantResolver merchantResolver;

    /**
     * 处理闸机检票通知。
     *
     * @param request  闸机检票通知请求
     * @param cardId   卡ID
     * @param response 响应对象
     */
    public void handleNotifyVerifyResult(NotifyVerifyResultReqDTO request, String cardId, NotifyVerifyResultRespDTO response) {
        // 入口日志前置，确保异常场景下请求参数不丢失
        log.info("IF1A-01 ticket-server 收到闸机检票通知, 请求参数={}", request);

        // 1. 查询当前票卡状态
        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(cardId);
        log.info("IF1A-01 当前票卡状态, 来自数据库={}", currentStatus);
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return;
        }

        handleGateTransaction(request, currentStatus, response);
    }

    /**
     * 处理检票流程：状态更新 → HCE回写 → 行业数据推送。
     * 入口日志已在 handleNotifyVerifyResult 中打印，此处仅记录关键中间状态。
     */
    private void handleGateTransaction(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus, NotifyVerifyResultRespDTO response) {
        // 0. 日票进站校验：SIGN_CHANNEL_CODE ∈ {12,13,14,15} 时拦截进站
        String resolvedCardType = CardTypeMapping.toIssueCardType(request.getSignChannelCode());
        if (CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            DailyTicketBaseResult entryCheck = dailyTicketClient.entryCheck(request.getCardId());
            if (!"0000".equals(entryCheck.getRetCode())) {
                log.warn("IF1A-01 日票进站校验拒绝, cardId={}, signChannelCode={}, retMsg={}",
                        request.getCardId(), request.getSignChannelCode(), entryCheck.getRetMsg());
                response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
                response.setRetMsg(entryCheck.getRetMsg());
                return;
            }
            log.info("IF1A-01 日票进站校验通过, cardId={}, signChannelCode={}",
                    request.getCardId(), request.getSignChannelCode());
        }

        // 2. 查询真实卡类型（日票/员工票需特殊处理）
        applyActualCardType(request);

        // 3. 填充上次交易字段（无值时用当前状态兜底）
        request.setLastTicketStatus(currentStatus.getCodeStatus());
        request.setLastHandleStationCode(currentStatus.getLastTxnStation());
        if (!StringUtils.hasText(request.getLastHandleDateTime())) {
            request.setLastHandleDateTime(currentStatus.getLastTxnTime());
        }

        // 4. 构建并插入交易明细（幂等：重复上送仅记录日志，不中断流程）
        QRCodeTxnDetail detail = buildTxnDetail(request, response);
        log.info("IF1A-01 写入交易明细, detail = {}", detail);
        boolean isDuplicate = false;
        try {
            qrCodeTxnDetailMapper.insert(detail);
        } catch (DuplicateKeyException e) {
            log.info("IF1A-01 闸机交易明细重复上送, cardId={}, trxType={}, handleDateTime={}, ticketTransSeq={}, deviceId={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(),
                    request.getTicketTransSeq(), request.getDeviceId());
            isDuplicate = true;
        }

        // 5. 更新票卡状态（重复上送也执行，upsert 幂等）
        QRCodeStatus nextStatus = buildNextStatus(request, currentStatus);
        log.info("IF1A-01 更新票卡状态明细, detail = {}", nextStatus);
        int updateCount = qrCodeStatusMapper.upsert(nextStatus);
        log.info("IF1A-01 更新票卡状态完成, cardId={}, isDuplicate={}, nextStatusCode={}, updateCount={}",
                request.getCardId(), isDuplicate, nextStatus.getCodeStatus(), updateCount);

        // 5.1 日票出站处理：出站时扣减计次票次数、标记已使用（仅出站 trxType 触发）
        if ("02".equals(request.getTrxType()) && CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            DailyTicketBaseResult markResult = dailyTicketClient.markUsed(request.getCardId(), null);
            if (!"0000".equals(markResult.getRetCode())) {
                log.warn("IF1A-01 日票出站标记失败, cardId={}, retMsg={}", request.getCardId(), markResult.getRetMsg());
            } else {
                log.info("IF1A-01 日票出站处理完成, cardId={}", request.getCardId());
            }
        }

        //todo ：给支付宝出行推送的时候，补进站类型按照正常56推，补出站区分一下，用57推

        // 5. HCE 卡仅回写；非 HCE 卡推进行业数据；支付宝渠道额外推送行程数据
        String cardType = request.getCardType();
        if (isHceCard(cardType)) {
            log.info("IF1A-01 HCE卡数据回写, cardId={}, cardType={}", request.getCardId(), cardType);
            updateHceDataFromGateTransaction(request);
        } else {
            // 推动行业数据
            appNotifyService.notifyVerifyResult(request, nextStatus);

            if (IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode())
                    && !CardTypeMapping.isAiShanDong(request.getCardType())) {
                // 支付宝行业数据推送
                appNotifyService.pushAlipayTripData(request, nextStatus, detail);
            }
        }

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setTicketStatus(nextStatus.getCodeStatus());
        response.setOrderExpType(resolveOrderExpType(request.getTrxType()));
        response.setOfflineFlag(resolveOfflineFlag(request.getSignChannelCode()));
        response.setCompanionFlag(request.getCompanionFlag());
        response.setPayChannelCode(request.getPaymentVendor());
        // 日票额外字段透传（signChannelCode∈{12,13,14,15}时填充）
        if (CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            resolveDailyTicketFields(request, response);
        }
        // 商户号分账字段透传
        resolveMerchantParties(request, response);
        log.info("IF1A-01 返回 ticketResponse 字段, cardId={}, ticketStatus={}, orderExpType={}, offlineFlag={}, companionFlag={}, ticketCode={}, countingTimes={}, countingFlag={}, attributableParty={}, receivingParty={}, payChannelCode={}, signChannelCode={}",
                request.getCardId(), response.getTicketStatus(), response.getOrderExpType(),
                response.getOfflineFlag(), response.getCompanionFlag(), response.getTicketCode(),
                response.getCountingTimes(), response.getCountingFlag(),
                response.getAttributableParty(), response.getReceivingParty(),
                response.getPayChannelCode(), request.getSignChannelCode());
    }

    /**
     * 查询日票信息并写入 response（ticketCode/countingTimes/countingFlag）。
     */
    private void resolveDailyTicketFields(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        try {
            QueryDailyTicketInfoReqDTO queryReq = new QueryDailyTicketInfoReqDTO();
            queryReq.setCardId(request.getCardId());
            QueryDailyTicketInfoResult info = dailyTicketClient.queryDailyTicketInfo(queryReq);
            if (info != null && "0000".equals(info.getRetCode())) {
                response.setTicketCode(info.getTicketCode());
                response.setCountingTimes(info.getActualTimes());
                // countingFlag: 计次票(0448)=2，计时票(0445-0447)=1
                response.setCountingFlag(
                        "0448".equals(CardTypeMapping.toIssueCardType(request.getSignChannelCode())) ? "2" : "1");
                log.info("IF1A-01 日票信息解析完成, cardId={}, ticketCode={}, countingTimes={}, countingFlag={}",
                        request.getCardId(), info.getTicketCode(), info.getActualTimes(), response.getCountingFlag());
            } else {
                log.warn("IF1A-01 查询日票信息失败, cardId={}, retCode={}", request.getCardId(),
                        info != null ? info.getRetCode() : "null");
            }
        } catch (Exception e) {
            log.error("IF1A-01 查询日票信息异常, cardId={}", request.getCardId(), e);
        }
    }

    /**
     * 商户号分账解析：
     * - 正常进出站：以 handleDateTime 的日期判断归属方/收款方
     * - 单边或补站（orderExpType=5 或 trxType 为异常类型）：若乘车日期早于变更日，强制使用城交商户
     */
    private void resolveMerchantParties(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        try {
            // 乘车日期：取 handleDateTime 前8位（yyyyMMdd）
            String txnDate = StringUtils.hasText(request.getHandleDateTime())
                    ? request.getHandleDateTime().substring(0, 8) : null;
            boolean isSingleSideOrSupplement = !"0".equals(response.getOrderExpType())
                    || TrxTypeCodeEnum.ABNORMAL.getCode().equals(request.getTrxType())
                    || TrxTypeCodeEnum.ENTRY_FAIL.getCode().equals(request.getTrxType());
            if (isSingleSideOrSupplement && StringUtils.hasText(txnDate)
                    && !merchantResolver.shouldUseOldMerchant(txnDate)) {
                // 单边/补站且乘车日期在变更日期后，回退到城交商户
                response.setAttributableParty(merchantResolver.getOldAttributableParty());
                response.setReceivingParty(merchantResolver.getOldReceivingParty());
                log.info("IF1A-01 商户号解析-单边补站回退, cardId={}, txnDate={}, attributableParty={}, receivingParty={}",
                        request.getCardId(), txnDate, response.getAttributableParty(), response.getReceivingParty());
            } else {
                if (!merchantResolver.shouldUseOldMerchant(txnDate)) {
                    response.setAttributableParty(merchantResolver.getNewAttributableParty());
                    response.setReceivingParty(merchantResolver.getNewReceivingParty());
                } else {
                    response.setAttributableParty(merchantResolver.getOldAttributableParty());
                    response.setReceivingParty(merchantResolver.getOldReceivingParty());
                }
                log.info("IF1A-01 商户号解析完成, cardId={}, txnDate={}, orderExpType={}, isSingleSide={}, attributableParty={}, receivingParty={}",
                        request.getCardId(), txnDate, response.getOrderExpType(), isSingleSideOrSupplement,
                        response.getAttributableParty(), response.getReceivingParty());
            }
        } catch (Exception e) {
            log.error("IF1A-01 商户号解析异常, cardId={}", request.getCardId(), e);
        }
    }

    /**
     * 构建交易明细。
     */
    public QRCodeTxnDetail buildTxnDetail(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        QRCodeTxnDetail detail = new QRCodeTxnDetail();
        detail.setDeviceId(request.getDeviceId());
        detail.setItpUserId(request.getItpUserId());
        detail.setTrxType(request.getTrxType());
        detail.setIssueChannelCode(request.getIssueChannelCode());
        detail.setSignChannelCode(request.getSignChannelCode());
        detail.setCardId(request.getCardId());
        detail.setCardType(request.getCardType());
        detail.setHandleDateTime(request.getHandleDateTime());
        detail.setTxnDate(request.getHandleDateTime().substring(0, 8));
        detail.setHandleStationCode(request.getHandleStationCode());
        detail.setTrxAmount(parseAmount(request.getTrxAmount()));
        detail.setOvertimeAmount(parseAmount(request.getOvertimeAmount()));
        detail.setLastTicketStatus(request.getLastTicketStatus());
        detail.setHandleResultCode(request.getHandleResultCode());
        detail.setLastHandleStationCode(request.getLastHandleStationCode());
        detail.setLastHandleDateTime(request.getLastHandleDateTime());
        detail.setTicketTransSeq(request.getTicketTransSeq());
        detail.setReserve1(request.getReserve1());
        detail.setReserve2(request.getReserve2());
        detail.setCreateTime(LocalDateTime.now());
        return detail;
    }

    /**
     * 构建下一票卡状态。
     *
     * <p>状态推进规则：</p>
     * <ul>
     *   <li>useCount = current + 1（首次为 1）</li>
     *   <li>txnSeq = current + 1（首次为 1）</li>
     *   <li>进站交易(01)：更新 gateInTime/gateInStation</li>
     *   <li>出站交易(02/03)：保留 gateInTime/gateInStation，记录 trxAmount</li>
     * </ul>
     */
    public QRCodeStatus buildNextStatus(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus) {
        QRCodeStatus nextStatus = new QRCodeStatus();
        nextStatus.setCardId(request.getCardId());
        nextStatus.setUseCount(currentStatus.getUseCount() == null ? 1 : currentStatus.getUseCount() + 1);
        nextStatus.setChannel(defaultString(request.getIssueChannelCode(), currentStatus.getChannel()));
        nextStatus.setCodeStatus(resolveCodeStatus(request.getTrxType(), request.getExcessFareType(), request.getAdviceOpt()));
        nextStatus.setLastTxnTime(request.getHandleDateTime());
        nextStatus.setLastTxnStation(request.getHandleStationCode());
        nextStatus.setTxnSeq(incrementTxnSeq(currentStatus.getTxnSeq()));
        nextStatus.setCreateTime(currentStatus.getCreateTime());
        nextStatus.setUpdateTime(LocalDateTime.now());
        nextStatus.setGateStatus(request.getTrxType());

        // 进站交易：更新进站时间和站点
        if (TrxTypeCodeEnum.isEntryTxn(request.getTrxType())) {
            nextStatus.setGateInTime(request.getHandleDateTime());
            nextStatus.setGateInStation(request.getHandleStationCode());
        } else {
            nextStatus.setGateInTime(currentStatus.getGateInTime());
            nextStatus.setGateInStation(currentStatus.getGateInStation());
        }

        // 出站时记录实际交易金额
        if (!TrxTypeCodeEnum.isEntryTxn(request.getTrxType())) {
            nextStatus.setTrxAmount(parseAmount(request.getTrxAmount()));
        } else {
            nextStatus.setTrxAmount(currentStatus.getTrxAmount());
        }

        log.info("IF1A-01 构建下一状态, cardId={}, trxType={}, currentStatus={}, nextStatus={}",
                request.getCardId(), request.getTrxType(), currentStatus, nextStatus);
        return nextStatus;
    }

    /**
     * 根据交易类型解析下一状态码。
     *
     * <p>状态机映射规则：</p>
     * <ul>
     *   <li>adviceOpt=018 → 补进站 (04)</li>
     *   <li>adviceOpt=005 → 20分钟内免费更新 (08)</li>
     *   <li>adviceOpt=006 → 20分钟内付费更新 (09)</li>
     *   <li>trxType=01 → 进站 (04)</li>
     *   <li>trxType=02 → 正常出站 (05)</li>
     *   <li>trxType=03 → 超时出站 (06)</li>
     *   <li>trxType=04 → 进站失败 (FF)</li>
     *   <li>trxType=99 → 异常 (FF)</li>
     *   <li>默认 → defaultCodeStatus (01)</li>
     * </ul>
     */
    public String resolveCodeStatus(String trxType, String excessFareType, String adviceOpt) {
        log.info("IF1A-01 解析状态码, trxType={}, excessFareType={}, adviceOpt={}",
                trxType, excessFareType, adviceOpt);
        
        String code = resolveAdviceOptCodeStatus(adviceOpt);
        if (code != null) {
            return code;
        }
        
        code = resolveTrxTypeCodeStatus(trxType);
        if (code != null) {
            return code;
        }
        
        String fallback = QRCodeStatusEnum.fromCode(defaultCodeStatus).getCode();
        log.warn("IF1A-01 状态码解析未匹配任何规则, trxType={}, 使用默认值={}", trxType, fallback);
        return fallback;
    }

    private String resolveAdviceOptCodeStatus(String adviceOpt) {
        if (!StringUtils.hasText(adviceOpt)) {
            return null;
        }
        if ("018".equals(adviceOpt)) {
            String code = QRCodeStatusEnum.ENTRY.getCode();
            log.info("IF1A-01 状态码解析结果={}(补进站), adviceOpt=018", code);
            return code;
        }
        if ("005".equals(adviceOpt)) {
            String code = QRCodeStatusEnum.UPDATE_FREE.getCode();
            log.info("IF1A-01 状态码解析结果={}(20分钟内免费更新), adviceOpt=005", code);
            return code;
        }
        if ("006".equals(adviceOpt)) {
            String code = QRCodeStatusEnum.UPDATE_PAY.getCode();
            log.info("IF1A-01 状态码解析结果={}(20分钟内付费更新), adviceOpt=006", code);
            return code;
        }
        return null;
    }

    private String resolveTrxTypeCodeStatus(String trxType) {
        if (TrxTypeCodeEnum.isEntryTxn(trxType)) {
            String code = QRCodeStatusEnum.ENTRY.getCode();
            log.info("IF1A-01 状态码解析结果={}(进站), trxType=01", code);
            return code;
        }
        if (TrxTypeCodeEnum.EXIT.getCode().equals(trxType)) {
            String code = QRCodeStatusEnum.EXIT.getCode();
            log.info("IF1A-01 状态码解析结果={}(正常出站), trxType=02", code);
            return code;
        }
        if (TrxTypeCodeEnum.EXIT_OVERTIME.getCode().equals(trxType)) {
            String code = QRCodeStatusEnum.EXIT_OVERTIME.getCode();
            log.info("IF1A-01 状态码解析结果={}(超时出站), trxType=03", code);
            return code;
        }
        if (TrxTypeCodeEnum.ENTRY_FAIL.getCode().equals(trxType)) {
            String code = QRCodeStatusEnum.ENTRY_FAIL.getCode();
            log.info("IF1A-01 状态码解析结果={}(进站失败), trxType=04", code);
            return code;
        }
        if (TrxTypeCodeEnum.ABNORMAL.getCode().equals(trxType)) {
            String code = QRCodeStatusEnum.ABNORMAL.getCode();
            log.info("IF1A-01 状态码解析结果={}(异常), trxType=99", code);
            return code;
        }
        return null;
    }

    /**
     * HCE 卡闸机交易完成后，将 reserve1 的 64 字节卡数据回写到账户服务。
     * 回写失败不影响已完成的交易明细入库和票卡状态更新，闸机重试可再次触发回写。
     */
    public void updateHceDataFromGateTransaction(NotifyVerifyResultReqDTO request) {
        if (!isHceCard(request.getCardType()) || !StringUtils.hasText(request.getReserve1())) {
            return;
        }
        try {
            UpdateHceDataReqDTO updateRequest = new UpdateHceDataReqDTO();
            updateRequest.setCardId(request.getCardId());
            updateRequest.setHceData(request.getReserve1().trim());
            accountClient.updateHceData(updateRequest);
            log.info("IF1A-01 HCE卡数据回写成功, cardId={}", request.getCardId());
        } catch (Exception e) {
            log.error("IF1A-01 回写HCE卡数据失败, cardId={}", request.getCardId(), e);
        }
    }

    /**
     * 查询并应用真实卡类型。
     * <p>闸机可能仍上送二维码行业卡类型 0441；这里以 account-server 的开户记录为准，
     * 因此鲁通码会在交易明细入库和后续行业推送前恢复为 044A。
     * <p>员工票（0444）和日票（0445-0448）强制清零金额，确保免费乘车在交易记录和行业推送中不产生费用数据。
     * 查询失败或异常时保留闸机上送值，不影响主流程。
     */
    public void applyActualCardType(NotifyVerifyResultReqDTO request) {
        String cardId = request.getCardId();
        try {
            QueryUserInfoResult result = accountClient.queryCardTypeByCardId(cardId);
            log.info("IF1A-01 查询真实卡(用户信息)，值：{}",result);
            if (result == null || !RET_SUCCESS.equals(result.getRetCode())
                    || !StringUtils.hasText(result.getCardType())) {
                log.warn("IF1A-01 查询真实卡类型失败，保留闸机上送值");
                return;
            }

            String actualCardType = result.getCardType().trim();
            request.setCardType(actualCardType);
            // 同行票/第三方票标识：account 返回 Y/N/C，透传下游
            if (StringUtils.hasText(result.getCompanionFlag())) {
                request.setCompanionFlag(result.getCompanionFlag().trim());
            }
            // 支付渠道编码：来自 USER_ITP_REG_INFO.CHANNEL
            if (StringUtils.hasText(result.getChannel())) {
                request.setPaymentVendor(result.getChannel().trim());
            } else {
                log.warn("IF1A-01 用户信息中未找到支付渠道编码，cardId={}", cardId);
            }
            // 签约流水号：来自 USER_ITP_REG_INFO.REQ_CONTRACT_NO
            if (StringUtils.hasText(result.getReqContractNo())) {
                request.setRequestSignSeq(result.getReqContractNo().trim());
            } else {
                log.warn("IF1A-01 用户信息中未找到签约流水号，cardId={}", cardId);
            }
            if (CardTypeCodeEnum.isEmployeeCard(actualCardType) || CardTypeCodeEnum.isDailyTicket(actualCardType)) {
                request.setTrxAmount("0");
                request.setOvertimeAmount("0");
                log.info("IF1A-01 免扣费, cardId={}, cardType={}", cardId, actualCardType);
            }
        } catch (Exception e) {
            log.warn("IF1A-01 查询真实卡类型异常，保留闸机上送值, cardId={}", cardId, e);
        }
    }

    private Long parseAmount(String amount) {
        if (!StringUtils.hasText(amount)) {
            return null;
        }
        return Long.valueOf(amount);
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String incrementTxnSeq(String txnSeq) {
        if (!StringUtils.hasText(txnSeq)) {
            return "1";
        }
        try {
            return String.valueOf(Long.parseLong(txnSeq) + 1);
        } catch (NumberFormatException e) {
            return txnSeq;
        }
    }

    /**
     * 根据出站交易类型解析订单异常类型。
     * 02=正常出站→0（正常），03=超时出站→5（双段计费正常订单_行程超时）。
     */
    private String resolveOrderExpType(String trxType) {
        if (TrxTypeCodeEnum.EXIT_OVERTIME.getCode().equals(trxType)) {
            return "5";
        }
        return "0";
    }

    /**
     * 根据签约渠道代码判断离线码标识。
     * 0x17=离线码→Y，其他→null。
     */
    private String resolveOfflineFlag(String signChannelCode) {
        String resolved = SignChannelUtils.resolve(signChannelCode);
        return "17".equals(resolved) ? "Y" : null;
    }
}
