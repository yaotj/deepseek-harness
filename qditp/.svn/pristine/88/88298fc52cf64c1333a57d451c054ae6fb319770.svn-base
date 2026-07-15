package com.chinasofti.huateng.online.service.impl;

import com.chinasofti.huateng.online.constant.OnlineErrorCodeEnum;
import com.chinasofti.huateng.online.entity.OnlineDeviceHeartbeat;
import com.chinasofti.huateng.online.entity.OnlineOrder;
import com.chinasofti.huateng.online.entity.OnlineOrderTicket;
import com.chinasofti.huateng.online.entity.QRCodeStatus;
import com.chinasofti.huateng.online.mapper.OnlineDeviceHeartbeatMapper;
import com.chinasofti.huateng.online.mapper.OnlineOrderMapper;
import com.chinasofti.huateng.online.mapper.OnlineOrderTicketMapper;
import com.chinasofti.huateng.online.model.BaseRespDTO;
import com.chinasofti.huateng.online.model.agm.AgmDtos;
import com.chinasofti.huateng.online.model.bom.BomDtos;
import com.chinasofti.huateng.online.model.tvm.TvmDtos;
import com.chinasofti.huateng.online.service.DeductionService;
import com.chinasofti.huateng.online.service.OnlineService;
import com.chinasofti.huateng.online.service.QRCodeStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OnlineServiceImpl implements OnlineService {
    private static final Logger log = LoggerFactory.getLogger(OnlineServiceImpl.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String STATUS_ORDERED = "ORDERED";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String STATUS_REFUND = "REFUND_PENDING";
    private static final String STATUS_CANCELED = "CANCELED";

    @Value("${online.pay.base-url}")
    private String payBaseUrl;

    @Value("${online.default-provider-id:01}")
    private String defaultProviderId;

    @Value("${online.default-issue-channel-code:01}")
    private String defaultIssueChannelCode;

    @Value("${online.max-topup-amount:1000}")
    private Integer maxTopupAmount;

    @Autowired
    private OnlineOrderMapper onlineOrderMapper;
    @Autowired
    private OnlineOrderTicketMapper onlineOrderTicketMapper;
    @Autowired
    private OnlineDeviceHeartbeatMapper onlineDeviceHeartbeatMapper;
    @Autowired
    private QRCodeStatusService qrCodeStatusService;
    @Autowired
    private DeductionService deductionService;

    @Override
    public BomDtos.RequestCardDataAnalyseRespDTO requestCardDataAnalyse(BomDtos.RequestCardDataAnalyseReqDTO request) {
        BomDtos.RequestCardDataAnalyseRespDTO response = new BomDtos.RequestCardDataAnalyseRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId()) || !StringUtils.hasText(request.getUpdateType())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "cardId/updateType不能为空");
            return response;
        }

        /*
         * 规范中 IF5A-01 的核心目的是让 BOM 在处理前先拿到平台侧票卡最新状态，
         * 然后给出“本次建议操作类型”。
         * 这里实现上使用 QR_CODE_STATUS 作为平台最新票卡状态快照：
         * 1. 如果票卡不存在则初始化一张默认二维码票；
         * 2. 根据当前状态 + 更新区域(updateType)计算 adviceOpt；
         * 3. 返回 BOM 需要展示和后续更新时要使用的基础字段。
         */
        QRCodeStatus cardState = qrCodeStatusService.getOrInitByCardId(request.getCardId());
        response.setProviderId(defaultProviderId);
        response.setCardIssueDate(formatDateTime(cardState.getCreateTms()));
        response.setMsisdn(defaultString(cardState.getMsisdn(), request.getMsisdn()));
        response.setCardId(cardState.getCardId());
        response.setCardStatus(defaultString(cardState.getCardStatus(), "03"));
        response.setLastLineCode(defaultString(cardState.getLastLineCode(), "01"));
        response.setLastStationCode(defaultString(cardState.getLastStationCode(), "FFFF"));
        response.setLastUpdateDate(formatDateTime(cardState.getLastHandleDateTime()));
        response.setLastTransAmout(String.valueOf(defaultInteger(cardState.getLastTransAmount(), 0)));
        response.setLastTikcetTransSeq(defaultString(cardState.getLastTicketTransSeq(), "0"));
        response.setAdviceOpt(Collections.singletonList(resolveBomAdvice(cardState.getCardStatus(), request.getUpdateType())));
        response.setManagerCode("");
        response.setTransAmount(String.valueOf(defaultInteger(cardState.getLastTransAmount(), 0)));
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BomDtos.RequestUpdateCardDataRespDTO requestUpdateCardData(BomDtos.RequestUpdateCardDataReqDTO request) {
        BomDtos.RequestUpdateCardDataRespDTO response = new BomDtos.RequestUpdateCardDataRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId()) || !StringUtils.hasText(request.getAdviceOpt())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "cardId/adviceOpt不能为空");
            return response;
        }

        /*
         * IF5A-03 的职责是把 BOM 选定的补站/更新动作真正落到平台侧。
         * 这里按 adviceOpt 映射出新的 ticket status，并同时更新：
         * 1. 最后处理站点与时间；
         * 2. 最近一次处理金额；
         * 3. 返回给 BOM 的最新行业数据。
         */
        QRCodeStatus cardState = qrCodeStatusService.getOrInitByCardId(request.getCardId());
        cardState.setCardStatus(mapBomAdviceToStatus(request.getAdviceOpt()));
        cardState.setLastStationCode(defaultString(request.getUpdateStationCode(), cardState.getLastStationCode()));
        cardState.setLastHandleDateTime(parseDateTime(request.getOptDate()));
        cardState.setLastTransAmount(parseInteger(request.getTransAmount()));
        cardState.setIndustryData(qrCodeStatusService.buildIndustryData(cardState));
        cardState.setUpdateTms(LocalDateTime.now());
        qrCodeStatusService.save(cardState);
        response.setCardData(cardState.getIndustryData());
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BomDtos.RequestGenNoCashOrderRespDTO requestGenNoCashOrder(BomDtos.RequestGenNoCashOrderReqDTO request, String deviceId) {
        BomDtos.RequestGenNoCashOrderRespDTO response = new BomDtos.RequestGenNoCashOrderRespDTO();
        if (request == null || !StringUtils.hasText(request.getTransType()) || !StringUtils.hasText(request.getCardId())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "transType/cardId不能为空");
            return response;
        }

        /*
         * BOM 非现金业务的第一步只是“建单”，并不立即支付。
         * 因此这里先将 BOM 业务场景、卡号、操作员、班次、终端流水等上下文全部落到订单表，
         * 让后续扫码支付、支付结果查询和业务结果通知都围绕同一订单号闭环。
         */
        OnlineOrder order = buildBaseOrder(generateOrderNo("BOM"), "BOM_NOCASH", deviceId);
        order.setTransType(request.getTransType());
        order.setAdminTransType(request.getAdminTransType());
        order.setOperatorId(request.getOperaterId());
        order.setShiftId(request.getShiftId());
        order.setCardId(request.getCardId());
        order.setTransAmount(parseInteger(request.getTransAount()));
        order.setBomOptSeq(request.getBomOptSeq());
        onlineOrderMapper.insert(order);
        response.setOrderNo(order.getOrderNo());
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BomDtos.RequestPaymentRespDTO requestBomPayment(BomDtos.RequestPaymentReqDTO request) {
        BomDtos.RequestPaymentRespDTO response = new BomDtos.RequestPaymentRespDTO();
        if (request == null || !StringUtils.hasText(request.getOrderNo()) || !StringUtils.hasText(request.getPaymentCode())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "orderNo/paymentCode不能为空");
            return response;
        }
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * 规范里 IF8A-05 是同步返回支付结果的接口。
         * 当前阶段先实现为平台内的统一支付模拟：
         * - 默认按成功处理；
         * - paymentVendor 中如果显式带 FAIL，则按失败处理，方便联调。
         */
        applyPayment(order, request.getPaymentCode(), request.getPaymentVendor());
        onlineOrderMapper.updateByOrderNo(order);
        response.setPaymentResult(order.getPayResult());
        response.setPaymentResultDesc(order.getPayResultDesc());
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO notiBomTopupResult(BomDtos.BomTopupResultReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "orderNo不能为空");
            return response;
        }
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * 这里承接文档里“TVM 充值存疑后，乘客到 BOM 处理”的场景。
         * BOM 给出的结果被视为人工确认结果，因此直接更新订单最终状态。
         */
        order.setTopupStatus(request.getTopupStatus());
        order.setOrderStatus("00".equals(request.getTopupStatus()) ? STATUS_FINISHED : STATUS_FAILED);
        order.setOrderStatusDesc("00".equals(request.getTopupStatus()) ? "充值成功" : "充值失败");
        order.setUpdateTms(LocalDateTime.now());
        onlineOrderMapper.updateByOrderNo(order);
        fillSuccess(response);
        return response;
    }

    @Override
    public BomDtos.RequestGetPayResultRespDTO requestBomPayResult(BomDtos.RequestGetPayResultReqDTO request) {
        BomDtos.RequestGetPayResultRespDTO response = new BomDtos.RequestGetPayResultRespDTO();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "orderNo不能为空");
            return response;
        }
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * 规范里 BOM 超时后会反复查支付结果。
         * 本实现中内部订单初始支付状态为 ORDERED，对外查询结果转成 PROCESSING，
         * 保持设备侧“处理中”的语义更贴近文档描述。
         */
        response.setPaymentResult(STATUS_ORDERED.equals(order.getPayResult()) ? STATUS_PROCESSING : order.getPayResult());
        response.setPaymentResultDesc(defaultString(order.getPayResultDesc(), "处理中"));
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO notiBomBusinessResult(BomDtos.BomBusinessResultReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "orderNo不能为空");
            return response;
        }
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * BOM 最终业务结果回告是订单闭环的最后一步。
         * 无论前面是否支付成功，这里都记录终端实际执行业务结果，
         * 方便后续对账、审计和异常追踪。
         */
        order.setBusinessResult(request.getOptResult());
        order.setBusinessResultDesc(request.getOptResultDesc());
        order.setOrderStatus(STATUS_SUCCESS.equals(request.getOptResult()) ? STATUS_FINISHED : STATUS_FAILED);
        order.setUpdateTms(LocalDateTime.now());
        onlineOrderMapper.updateByOrderNo(order);
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO notiUpdateHceData(BomDtos.HceUpdateResultReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "cardId不能为空");
            return response;
        }
        /*
         * HCE 更新结果与二维码更新本质一致，都是平台侧票卡状态变更。
         * 区别在于这里额外保存 HCE 数据块和交易计数器，后续如果需要和 ACC/HCE 业务串联，
         * 这一张状态表就可以继续承接。
         */
        QRCodeStatus cardState = qrCodeStatusService.getOrInitByCardId(request.getCardId());
        cardState.setCardStatus(mapBomAdviceToStatus(request.getAdviceOpt()));
        cardState.setLastStationCode(request.getUpdateStationCode());
        cardState.setLastHandleDateTime(parseDateTime(request.getOptDate()));
        cardState.setLastTransAmount(parseInteger(request.getTransAmount()));
        cardState.setLastTicketTransSeq(request.getTikcetTransSeq());
        cardState.setHceData(request.getHceData());
        cardState.setIndustryData(qrCodeStatusService.buildIndustryData(cardState));
        cardState.setUpdateTms(LocalDateTime.now());
        qrCodeStatusService.save(cardState);
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO notiVerifyResult(AgmDtos.NotiVerifyResultReqDTO request, String deviceId) {
        BaseRespDTO response = new BaseRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "cardId不能为空");
            return response;
        }
        /*
         * AGM ????????????????????
         * 1. ?? QRCodeStatusService ???????????
         * 2. ??????????????????
         * 3. ?????????? DeductionService?
         *
         * TODO:
         * - ???? QRCodeStatusService ??????????????
         * - ???? DeductionService ???????/?????
         */
        QRCodeStatus cardState = qrCodeStatusService.handleAgmVerifyResult(request, deviceId);
        if (deductionService.shouldDeduct(request, cardState)) {
            deductionService.deduct(request, cardState);
        }
        fillSuccess(response);
        return response;
    }

    @Override
    public AgmDtos.RequestSynKeyListRespDTO requestSynKeyList(AgmDtos.RequestSynKeyListReqDTO request) {
        AgmDtos.RequestSynKeyListRespDTO response = new AgmDtos.RequestSynKeyListRespDTO();
        List<AgmDtos.KeyCurVerRespDTO> result = new ArrayList<>();
        List<AgmDtos.KeyCurVerReqDTO> requestList = request == null || request.getKeyCurVerList() == null
                ? Collections.emptyList() : request.getKeyCurVerList();
        if (requestList.isEmpty()) {
            AgmDtos.KeyCurVerReqDTO req = new AgmDtos.KeyCurVerReqDTO();
            req.setIssueChannelCode(defaultIssueChannelCode);
            req.setKeyId("01");
            req.setKeyBathNumber("0");
            requestList = Collections.singletonList(req);
        }
        /*
         * 当前阶段这里做的是“可联调密钥同步模拟”：
         * - 请求里带当前版本号；
         * - 平台固定返回版本 2；
         * - 如果终端已是版本 2，则返回无需更新；
         * - 否则返回一组模拟公钥数据。
         * 后续若接入真实密钥中心，可在这里替换为正式的密钥装载逻辑。
         */
        for (AgmDtos.KeyCurVerReqDTO item : requestList) {
            AgmDtos.KeyCurVerRespDTO respItem = new AgmDtos.KeyCurVerRespDTO();
            respItem.setIssueChannelCode(defaultString(item.getIssueChannelCode(), defaultIssueChannelCode));
            respItem.setKeyId(defaultString(item.getKeyId(), "01"));
            respItem.setKeyBathNumber("2");
            respItem.setNeedUpdateYN("2".equals(item.getKeyBathNumber()) ? "N" : "Y");
            respItem.setKeyList(buildKeyList());
            result.add(respItem);
        }
        response.setKeyCurVerList(result);
        fillSuccess(response);
        return response;
    }

    @Override
    public AgmDtos.RequestQrCodeStatusRespDTO requestQrCodeStatus(AgmDtos.RequestQrCodeStatusReqDTO request) {
        AgmDtos.RequestQrCodeStatusRespDTO response = new AgmDtos.RequestQrCodeStatusRespDTO();
        if (request == null || (!StringUtils.hasText(request.getCardId()) && !StringUtils.hasText(request.getItpUserId()))) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "itpUserId/cardId不能为空");
            return response;
        }
        /*
         * IF1A-04 是 AGM 在检票前的可选预校验接口。
         * 这里优先按 cardId 查，其次按 itpUserId 查。
         * 如果票卡被平台锁定，则按规范返回 50；否则返回平台保存的最后票卡状态。
         */
        QRCodeStatus cardState = StringUtils.hasText(request.getCardId())
                ? qrCodeStatusService.findByCardId(request.getCardId())
                : qrCodeStatusService.findByItpUserId(request.getItpUserId());
        if (cardState == null) {
            fillError(response, OnlineErrorCodeEnum.CARD_NOT_FOUND, OnlineErrorCodeEnum.CARD_NOT_FOUND.getMsg());
            return response;
        }
        response.setItpUserId(cardState.getItpUserId());
        response.setCardId(cardState.getCardId());
        response.setLastTicketStatus("Y".equalsIgnoreCase(cardState.getQueryLockedYn()) ? "50" : defaultString(cardState.getCardStatus(), "03"));
        response.setLastHandleDateTime(formatDateTime(cardState.getLastHandleDateTime()));
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TvmDtos.RequestGenSjtOrderRespDTO requestGenSjtOrder(TvmDtos.RequestGenSjtOrderReqDTO request, String deviceId) {
        TvmDtos.RequestGenSjtOrderRespDTO response = new TvmDtos.RequestGenSjtOrderRespDTO();
        if (request == null || !StringUtils.hasText(request.getEntryStationCode())
                || !StringUtils.hasText(request.getTicketPrice()) || !StringUtils.hasText(request.getSingelTicketNum())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "购票参数不完整");
            return response;
        }
        /*
         * TVM 单程票建单后，需要立即返回 orderNo 与 payUrl 给终端生成聚合二维码。
         * 因此这里除了落单外，还构造一个带签名占位的支付 URL，
         * 让 TVM 侧联调时可以先把二维码流程跑通。
         */
        OnlineOrder order = buildBaseOrder(generateOrderNo("SJT"), "TVM_SJT", deviceId);
        order.setEntryStationCode(request.getEntryStationCode());
        order.setExitStationCode(request.getExitStationCode());
        order.setTicketPrice(parseInteger(request.getTicketPrice()));
        order.setTicketNum(parseInteger(request.getSingelTicketNum()));
        order.setSingleTicketType(request.getSingleTicketType());
        onlineOrderMapper.insert(order);
        response.setOrderNo(order.getOrderNo());
        response.setPayUrl(buildPayUrl(order.getOrderNo()));
        fillSuccess(response);
        return response;
    }

    @Override
    public TvmDtos.RequestPayResultRespDTO requestTvmPayResult(TvmDtos.RequestPayResultReqDTO request) {
        TvmDtos.RequestPayResultRespDTO response = new TvmDtos.RequestPayResultRespDTO();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "orderNo不能为空");
            return response;
        }
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * TVM 会在二维码展示期间高频轮询这个接口。
         * 这里返回的是订单当前最新支付状态，不做额外状态换算，便于设备直接判断：
         * ORDERED / SUCCESS / FAILED。
         */
        response.setPaymentChannelCode(order.getPayChannelCode());
        response.setPaymentResult(defaultString(order.getPayResult(), STATUS_ORDERED));
        response.setPaymentResultDesc(defaultString(order.getPayResultDesc(), "已下单"));
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO notiTakeTicketResult(TvmDtos.NotiTakeTicketResultReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request == null ? null : request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * TVM 出票成功后，平台不仅要更新主订单状态，
         * 还要把每一张已写卡单程票的逻辑号、交易时间、交易金额单独落明细，
         * 这样后续对账和故障追溯才有依据。
         */
        order.setActualTakeTicketNum(parseInteger(request.getActualTakeTicketNum()));
        order.setTakeTicketDate(parseDateTime(request.getTakeTickeDate()));
        order.setOrderStatus(STATUS_FINISHED);
        order.setOrderStatusDesc("出票成功");
        order.setUpdateTms(LocalDateTime.now());
        onlineOrderMapper.updateByOrderNo(order);
        saveTicketItems(order.getOrderNo(), request.getTicketList());
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO notiTakeTicketFailResult(TvmDtos.NotiTakeTicketFailResultReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request == null ? null : request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * 出票故障场景要保留更多现场信息：
         * - 实际出票张数
         * - 故障时间
         * - 故障凭条号
         * - 错误码与错误说明
         * 文档中特别提到 2101 是二维码超时解锁订单，因此这里单独转成取消状态。
         * 其它故障则先进入待退款状态。
         */
        order.setActualTakeTicketNum(parseInteger(request.getActualTakeTicketNum()));
        order.setFaultOccurDate(parseDateTime(request.getFaultOccurDate()));
        order.setFaultSlipSeq(request.getFaultSlipSeq());
        order.setErrorCode(request.getErrorCode());
        order.setErrorMessage(request.getErrorMessage());
        order.setOrderStatus("2101".equals(request.getErrorCode()) ? STATUS_CANCELED : STATUS_REFUND);
        order.setOrderStatusDesc("2101".equals(request.getErrorCode()) ? "二维码超时" : "出票故障待退款");
        order.setUpdateTms(LocalDateTime.now());
        onlineOrderMapper.updateByOrderNo(order);
        saveTicketItems(order.getOrderNo(), request.getTicketList());
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO topupCardResultNoti(TvmDtos.TopupCardResultNotiReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request == null ? null : request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * 充值成功时，平台保存票卡逻辑号、物理号、充值金额、充值后余额与交易时间，
         * 这些字段后续既可用于对账，也能支撑 BOM 对 TVM 存疑充值的人工复核。
         */
        order.setTicketLogicNum(request.getTicketLogicNum());
        order.setTicketPhysicsNum(request.getTicketPhysicsNum());
        order.setTransAmount(parseInteger(request.getTransAmount()));
        order.setAfterAmount(parseInteger(request.getAfterAmount()));
        order.setTakeTicketDate(parseDateTime(request.getTransDate()));
        order.setTopupStatus("00");
        order.setOrderStatus(STATUS_FINISHED);
        order.setOrderStatusDesc("充值成功");
        order.setUpdateTms(LocalDateTime.now());
        onlineOrderMapper.updateByOrderNo(order);
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO topupCardFailNoti(TvmDtos.TopupCardFailNotiReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request == null ? null : request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * 充值失败通知覆盖三类状态：
         * 01 失败
         * 02 存疑
         * 03 取消
         * 当前实现将“存疑”保留在处理中，其余记为失败，便于后续接入退款或客服处理流程。
         */
        order.setTicketLogicNum(request.getTicketLogicNum());
        order.setTicketPhysicsNum(request.getTicketPhysicsNum());
        order.setTopupStatus(request.getTopupStatus());
        order.setFaultOccurDate(parseDateTime(request.getFaultOccurDate()));
        order.setFaultSlipSeq(request.getFaultSlipSeq());
        order.setErrorCode(request.getErrorCode());
        order.setErrorMessage(request.getErrorMessage());
        order.setOrderStatus("02".equals(request.getTopupStatus()) ? STATUS_PROCESSING : STATUS_FAILED);
        order.setOrderStatusDesc("02".equals(request.getTopupStatus()) ? "充值存疑" : "充值失败");
        order.setUpdateTms(LocalDateTime.now());
        onlineOrderMapper.updateByOrderNo(order);
        fillSuccess(response);
        return response;
    }

    @Override
    public TvmDtos.RequestTakeTicketAuthRespDTO requestTakeTicketAuth(TvmDtos.RequestTakeTicketAuthReqDTO request) {
        TvmDtos.RequestTakeTicketAuthRespDTO response = new TvmDtos.RequestTakeTicketAuthRespDTO();
        if (request == null || !StringUtils.hasText(request.getDeviceId())
                || !StringUtils.hasText(request.getQrcodeGenDate()) || !StringUtils.hasText(request.getRandomFact())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "deviceId/qrcodeGenDate/randomFact不能为空");
            return response;
        }
        /*
         * 扫码取票订单查询的关键是把 TVM 本地二维码中的 deviceId/qrcodeGenDate/randomFact
         * 和平台订单做绑定查询。
         * 这里先按三元组反查订单，后续如果补上 APP “激活取票订单”接口，
         * 只需要把激活时产生的绑定关系也落到主订单表即可继续复用本查询逻辑。
         */
        OnlineOrder order = onlineOrderMapper.selectByTakeTicketCode(
                request.getDeviceId(), parseDateTime(request.getQrcodeGenDate()), request.getRandomFact());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.NO_ACTIVE_ORDER, OnlineErrorCodeEnum.NO_ACTIVE_ORDER.getMsg());
            return response;
        }
        response.setOrderNo(order.getOrderNo());
        response.setDeviceId(order.getDeviceId());
        response.setEntryStationCode(order.getEntryStationCode());
        response.setExitStationCode(order.getExitStationCode());
        response.setTicketPrice(toString(order.getTicketPrice()));
        response.setSingelTicketNum(toString(order.getTicketNum()));
        response.setSingleTicketType(order.getSingleTicketType());
        response.setPaymentChannelCode(order.getPayChannelCode());
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TvmDtos.RequestTopupRespDTO requestTopup(TvmDtos.RequestTopupReqDTO request, String deviceId) {
        TvmDtos.RequestTopupRespDTO response = new TvmDtos.RequestTopupRespDTO();
        if (request == null || !StringUtils.hasText(request.getTicketLogicNum()) || !StringUtils.hasText(request.getTransAmount())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "ticketLogicNum/transAmount不能为空");
            return response;
        }
        /*
         * 文档中对 TVM 充值有限额要求。
         * 这里同时校验：
         * 1. 单次充值金额是否合法；
         * 2. 充值金额本身是否超限；
         * 3. 充值后余额是否超限。
         * 校验通过后生成充值订单并返回支付二维码地址。
         */
        Integer beforeAmount = parseInteger(request.getBeforeAmount());
        Integer transAmount = parseInteger(request.getTransAmount());
        if (transAmount == null || transAmount <= 0) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "充值金额非法");
            return response;
        }
        if ((beforeAmount != null && beforeAmount + transAmount > maxTopupAmount * 100)
                || transAmount > maxTopupAmount * 100) {
            fillError(response, OnlineErrorCodeEnum.TOPUP_AMOUNT_EXCEED, OnlineErrorCodeEnum.TOPUP_AMOUNT_EXCEED.getMsg());
            return response;
        }
        OnlineOrder order = buildBaseOrder(generateOrderNo("TOP"), "TVM_TOPUP", deviceId);
        order.setTicketLogicNum(request.getTicketLogicNum());
        order.setTicketPhysicsNum(request.getTicketPhysicsNum());
        order.setBeforeAmount(beforeAmount);
        order.setTransAmount(transAmount);
        onlineOrderMapper.insert(order);
        response.setOrderNo(order.getOrderNo());
        response.setPayUrl(buildPayUrl(order.getOrderNo()));
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TvmDtos.RequestPaymentRespDTO requestTvmPayment(TvmDtos.RequestPaymentReqDTO request) {
        TvmDtos.RequestPaymentRespDTO response = new TvmDtos.RequestPaymentRespDTO();
        if (request == null || !StringUtils.hasText(request.getOrderNo()) || !StringUtils.hasText(request.getPaymentCode())) {
            fillError(response, OnlineErrorCodeEnum.INVALID_PARAM, "orderNo/paymentCode不能为空");
            return response;
        }
        OnlineOrder order = onlineOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            fillError(response, OnlineErrorCodeEnum.ORDER_NOT_FOUND, OnlineErrorCodeEnum.ORDER_NOT_FOUND.getMsg());
            return response;
        }
        /*
         * TVM 扫码支付与 BOM 扫码支付共享同一套统一支付模拟逻辑，
         * 保证两个终端侧接口在状态语义和落库字段上保持一致。
         */
        applyPayment(order, request.getPaymentCode(), request.getPaymentVendor());
        onlineOrderMapper.updateByOrderNo(order);
        response.setPaymentResult(order.getPayResult());
        response.setPaymentResultDesc(order.getPayResultDesc());
        fillSuccess(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO deviceHeartbeat(String providerId, String deviceId) {
        BaseRespDTO response = new BaseRespDTO();
        /*
         * 设备心跳统一收敛到一张表，按 providerId + deviceId 做 upsert。
         * 后续如果要做设备在线监控、超时告警或终端健康统计，可以直接复用这张表。
         */
        OnlineDeviceHeartbeat heartbeat = new OnlineDeviceHeartbeat();
        heartbeat.setProviderId(providerId);
        heartbeat.setDeviceId(defaultString(deviceId, "UNKNOWN"));
        heartbeat.setLastHeartbeatTms(LocalDateTime.now());
        heartbeat.setStatus("NORMAL");
        heartbeat.setUpdateTms(LocalDateTime.now());
        onlineDeviceHeartbeatMapper.upsert(heartbeat);
        fillSuccess(response);
        return response;
    }

    private OnlineOrder buildBaseOrder(String orderNo, String orderType, String deviceId) {
        /*
         * 所有终端订单都统一走这里初始化，保证公共字段含义一致：
         * - ORDERED：支付未完成
         * - CREATED：订单刚生成
         * - deviceId：记录发起设备
         * 这样 BOM/TVM 后续复用相同的支付与闭环逻辑时，不会出现状态定义漂移。
         */
        OnlineOrder order = new OnlineOrder();
        order.setOrderNo(orderNo);
        order.setOrderType(orderType);
        order.setDeviceId(defaultString(deviceId, "UNKNOWN"));
        order.setPayResult(STATUS_ORDERED);
        order.setPayResultDesc("已下单");
        order.setOrderStatus(STATUS_CREATED);
        order.setOrderStatusDesc("订单已创建");
        order.setCreateTms(LocalDateTime.now());
        order.setUpdateTms(LocalDateTime.now());
        return order;
    }

    private void applyPayment(OnlineOrder order, String paymentCode, String paymentVendor) {
        /*
         * 统一支付状态推进器。
         * 当前作为模拟实现，约定：
         * - 默认支付成功
         * - paymentVendor 中带 FAIL 时返回失败
         * 这样测试时无需接第三方通道，也能覆盖终端成功/失败两条主分支。
         */
        order.setPayChannelCode(paymentCode);
        order.setPaymentVendor(paymentVendor);
        if (StringUtils.hasText(paymentVendor) && paymentVendor.toUpperCase().contains("FAIL")) {
            order.setPayResult(STATUS_FAILED);
            order.setPayResultDesc("支付失败");
            order.setOrderStatus(STATUS_FAILED);
            order.setOrderStatusDesc("支付失败");
        } else {
            order.setPayResult(STATUS_SUCCESS);
            order.setPayResultDesc("支付成功");
            order.setOrderStatus("PAID");
            order.setOrderStatusDesc("支付成功");
        }
        order.setUpdateTms(LocalDateTime.now());
    }

    private void saveTicketItems(String orderNo, List<TvmDtos.TicketItemDTO> items) {
        /*
         * 出票结果和故障结果都会带 ticketList。
         * 这里先删后插，保证同一订单重复补传通知时，平台保存的始终是最后一版终端明细。
         */
        onlineOrderTicketMapper.deleteByOrderNo(orderNo);
        if (items == null) {
            return;
        }
        for (TvmDtos.TicketItemDTO item : items) {
            OnlineOrderTicket record = new OnlineOrderTicket();
            record.setOrderNo(orderNo);
            record.setTicketLogicNum(item.getTicketLogicNum());
            record.setTicketPhysicsNum(item.getTicketPhysicsNum());
            record.setTransDate(parseDateTime(item.getTransDate()));
            record.setTransAmount(parseInteger(item.getTransAmount()));
            record.setCreateTms(LocalDateTime.now());
            onlineOrderTicketMapper.insert(record);
        }
    }

    private List<AgmDtos.KeyItemDTO> buildKeyList() {
        /*
         * AGM 密钥同步暂时返回固定结构的模拟密钥。
         * 这里保留“批次号 + keyIdx + keyValue + 生效日期”的正式字段形态，
         * 这样后续切换真实密钥来源时，设备侧协议无需再改。
         */
        List<AgmDtos.KeyItemDTO> keyList = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            AgmDtos.KeyItemDTO key = new AgmDtos.KeyItemDTO();
            key.setKeyBathNumber("2");
            key.setKeyIdx(String.format("%02X", i));
            key.setKeyValue(repeatHex(String.format("%02X", i), 128));
            key.setKeyEffectiveDate("20271101");
            key.setKvc("");
            key.setReserve("");
            keyList.add(key);
        }
        return keyList;
    }

    private String resolveBomAdvice(String cardStatus, String updateType) {
        /*
         * 根据规范里的典型场景做最小可用判断：
         * - 已进站且在非付费区更新，建议补出站 006
         * - 已出站/超时/已发售等状态在付费区更新，建议补进站 018
         * - 其它情况默认无需更新 000
         * 后续如果你们要完全贴合线路细则，可以继续在这里细化规则矩阵。
         */
        if ("04".equals(cardStatus) && "00".equals(updateType)) {
            return "006";
        }
        if (Arrays.asList("02", "05", "06", "80", "81", "03").contains(defaultString(cardStatus, "03"))
                && "01".equals(updateType)) {
            return "018";
        }
        return "000";
    }

    private String mapBomAdviceToStatus(String adviceOpt) {
        /*
         * BOM 建议动作与二维码票卡状态码之间的映射关系。
         * 这里直接沿用文档状态含义，方便 AGM/APP/BOM 三侧统一理解状态推进。
         */
        if ("018".equals(adviceOpt)) {
            return "10";
        }
        if ("006".equals(adviceOpt)) {
            return "02";
        }
        if ("005".equals(adviceOpt)) {
            return "08";
        }
        return "03";
    }

    private String buildPayUrl(String orderNo) {
        /*
         * TVM 文档要求建单后返回一个可展示为二维码的支付 URL。
         * 当前用本地配置的 base-url + orderNo + md5(orderNo) 作为联调占位实现。
         */
        return payBaseUrl + "?orderNo=" + orderNo + "&sign=" + md5(orderNo);
    }

    private String generateOrderNo(String prefix) {
        /*
         * 订单号采用“业务前缀 + 时间戳 + 随机串”生成，
         * 便于从日志中快速区分 BOM 非现金、TVM 售票、TVM 充值等业务来源。
         */
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("MD5签名失败", e);
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private String repeatHex(String seed, int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(seed);
        }
        return sb.substring(0, length);
    }

    private Integer parseInteger(String value) {
        /*
         * 规范里的数值字段大多以 string 传输，这里做统一转换。
         * 解析失败时返回 null，由上层按具体业务决定是否继续或报参数错误。
         */
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String toString(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime parseDateTime(String value) {
        /*
         * 统一解析规范里的 YYYYMMDDHHMMSS 时间格式。
         * 解析失败只打日志不抛异常，避免终端补传历史脏数据时导致整个流程中断。
         */
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATETIME_FORMATTER);
        } catch (Exception e) {
            log.warn("时间解析失败, value={}", value);
            return null;
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATETIME_FORMATTER);
    }

    private int defaultInteger(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private void fillSuccess(BaseRespDTO response) {
        /*
         * 所有接口统一返回 0000/成功，保持与规范对外报文一致。
         */
        response.setRetCode(OnlineErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(OnlineErrorCodeEnum.SUCCESS.getMsg());
    }

    private void fillError(BaseRespDTO response, OnlineErrorCodeEnum code, String msg) {
        /*
         * 错误场景统一由枚举提供基础错误码，再允许调用方覆盖更具体的错误消息，
         * 方便后面逐步细化成正式线路口径。
         */
        response.setRetCode(code.getCode());
        response.setRetMsg(msg);
    }
}
