package com.chinasofti.huateng.paysign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PayCallbackLogMapper;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付签约领域的既有业务工作流。
 *
 * <p>该类保留原有业务实现和事务边界；新的领域服务按能力委派到这里，
 * 使后续可逐项迁移实现，而不会在重构期间改变对外接口或支付处理语义。</p>
 */
@Service
public class PaySignWorkflow {
    private static final Logger log = LoggerFactory.getLogger(PaySignWorkflow.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String STATUS_NOT_SIGNED = "NOT_SIGNED";
    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_UNSIGNED = "UNSIGNED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SCANNING = "SCANNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    @Autowired
    private PaySignProperties paySignProperties;
    @Autowired
    private PaySignInfoMapper paySignInfoMapper;
    @Autowired
    private PaySignRequestMapper paySignRequestMapper;
    @Autowired
    private PayTxnDetailMapper payTxnDetailMapper;
    @Autowired
    private AppTerminationRequestMapper terminationRequestMapper;
    @Autowired
    private PayRefundDetailMapper payRefundDetailMapper;
    @Autowired
    private PayCallbackLogMapper payCallbackLogMapper;
    @Autowired
    private AppNotifyService appNotifyService;
    @Autowired
    private AccountClient accountClient;
    @Autowired
    private PayGatewayClient payGatewayClient;

    @Autowired
    private BlacklistClient blacklistClient;

    /**
     * IF8A-16 请求签约信息。
     * 按支付平台 2.2 contract 接口组装签约参数并发起请求，
     * 将返回的业务数据 JSON 字符串放入 requestStartSdkInfo 中返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel) {
        RequestSignInfoResult response = new RequestSignInfoResult();
        try {
            String validMsg = validateRequestSignInfo(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                writeLog("REQUEST_SIGN_INFO", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? normalizeVendor(request.getPayChannelCode()) : null, signChannel, request, response);
                return response;
            }

            String paymentVendor = normalizeVendor(request.getPayChannelCode());

            // 1. 校验是否已签约
            PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
            if (existingSign != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
                writeLog("REQUEST_SIGN_INFO", request.getThirdUserId(), request.getRequestSignSeq(), paymentVendor, signChannel, request, response);
                return response;
            }

            // 2. 调用支付平台获取SDK参数
            String sdkInfo = buildRequestStartSdkInfo(request, paymentVendor);
            if (!StringUtils.hasText(sdkInfo)) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "请求支付签约接口失败");
                writeLog("REQUEST_SIGN_INFO", request.getThirdUserId(), request.getRequestSignSeq(), paymentVendor, signChannel, request, response);
                return response;
            }

            // 3. 不操作 APP_PAY_SIGN_INFO，只记录流水
            fillSuccess(response);
            response.setRequestStartSdkInfo(sdkInfo);
            writeLog("REQUEST_SIGN_INFO", request.getThirdUserId(), request.getRequestSignSeq(), paymentVendor, signChannel, request, response);
            return response;
        } catch (Exception e) {
            log.error("处理请求签约信息异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            writeLog("REQUEST_SIGN_INFO", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? normalizeVendor(request.getPayChannelCode()) : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * 支付宝出行-添加签约信息。
     * <p>
     * 支付宝渠道完全独立，不需要对接支付平台的签约接口。
     * 接收支付宝 DTO，直接写入 APP_PAY_SIGN_INFO 表和流水表，同步确认签约成功。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request) {
        RequestSignInfoResult response = new RequestSignInfoResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())
                    || !StringUtils.hasText(request.getChannel())
                    || !StringUtils.hasText(request.getAgreementCode())
                    || !StringUtils.hasText(request.getChannelUserAccount())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId/channel/agreementCode/channelUserAccount不能为空");
                writeLog("ALIPAY_TRIP_REQUEST_SIGN_INFO", request != null ? request.getThirdUserId() : null, request != null ? request.getAgreementCode() : null, request != null ? request.getChannel() : null, SignChannelEnum.ALIPAY.getCode(), request, response);
                return response;
            }

            String paymentVendor = normalizeVendor(request.getChannel());

            // 1. 校验是否已签约（支付宝渠道）
            PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
            if (existingSign != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
                writeLog("ALIPAY_TRIP_REQUEST_SIGN_INFO", request.getThirdUserId(), request.getAgreementCode(), paymentVendor, SignChannelEnum.ALIPAY.getCode(), request, response);
                return response;
            }

            // 2. 同步确认：直接写入签约主表，不调用支付平台
            PaySignInfo signInfo = new PaySignInfo();
            signInfo.setRequestSignSeq(request.getAgreementCode());
            signInfo.setThirdUserId(request.getThirdUserId());
            signInfo.setPaymentVendor(paymentVendor);
            signInfo.setSignChannel(SignChannelEnum.ALIPAY.getCode());
            signInfo.setDisplayAccount(request.getChannelUserAccount());
            signInfo.setContractStatus(STATUS_SIGNED);
            signInfo.setSignTime(LocalDateTime.now());
            paySignInfoMapper.insert(signInfo);

            // 3. 写入流水表
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(request.getAgreementCode());
            logRecord.setThirdUserId(request.getThirdUserId());
            logRecord.setPaymentVendor(paymentVendor);
            logRecord.setSignChannel(SignChannelEnum.ALIPAY.getCode());
            logRecord.setOperationType("ALIPAY_TRIP_REQUEST_SIGN_INFO");
            logRecord.setSignStatus(STATUS_SIGNED);
            logRecord.setCreateTms(LocalDateTime.now());
            logRecord.setNotifyStatus("PENDING");
            logRecord.setNotifyRetryCount(0);
            paySignRequestMapper.insert(logRecord);

            // 4. 返回成功响应，不返回 SDK 参数
            fillSuccess(response);
            response.setRequestStartSdkInfo(null);
            writeLog("ALIPAY_TRIP_REQUEST_SIGN_INFO", request.getThirdUserId(), request.getAgreementCode(), paymentVendor, SignChannelEnum.ALIPAY.getCode(), request, response);
            return response;
        } catch (Exception e) {
            log.error("支付宝出行-添加签约信息异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            writeLog("ALIPAY_TRIP_REQUEST_SIGN_INFO", request != null ? request.getThirdUserId() : null, request != null ? request.getAgreementCode() : null, request != null ? request.getChannel() : null, SignChannelEnum.ALIPAY.getCode(), request, response);
            return response;
        }
    }

    /**
     * IF8A-21 信用能力咨询。
     * 实际映射支付平台 creditQuery 接口，用于判断当前用户是否具备签约/代扣能力。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel) {
        RequestContractAdvisoryRespDTO response = new RequestContractAdvisoryRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                writeLog("REQUEST_CONTRACT_ADVISORY", null, null, null, signChannel, null, response);
                return response;
            }
            String validMsg = validateContractQuery(request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor());
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                writeLog("REQUEST_CONTRACT_ADVISORY", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            PaySignGatewayResponse gatewayResponse = requestGatewaySimple(paySignProperties.getContractAdvisoryPath(), buildCreditQueryBizData(request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor()));
            if (!isGatewaySuccess(gatewayResponse)) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, gatewayErrorMsg(gatewayResponse, "请求支付签约咨询接口失败"));
                writeLog("REQUEST_CONTRACT_ADVISORY", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            fillSuccess(response);
            writeLog("REQUEST_CONTRACT_ADVISORY", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
            return response;
        } catch (Exception e) {
            log.error("处理信用能力咨询异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            writeLog("REQUEST_CONTRACT_ADVISORY", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * IF8A-22 签约结果查询。
     * 以 requestSignSeq 为主键向支付平台查询最新签约状态，并回填本地签约主表。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel) {
        RequestContractResultRespDTO response = new RequestContractResultRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                writeLog("REQUEST_CONTRACT_RESULT", null, null, null, signChannel, null, response);
                return response;
            }
            String validMsg = validateContractQuery(request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor());
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                writeLog("REQUEST_CONTRACT_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), null);
            boolean existed = signInfo != null;

            // 本地已有签约记录，且状态为已签约、数据完整时，直接返回，不调用支付平台。
            boolean needCallGateway = !existed
                    || !STATUS_SIGNED.equals(signInfo.getContractStatus())
                    || !StringUtils.hasText(signInfo.getPayAccountId())
                    || !StringUtils.hasText(signInfo.getPayAgreementNo());

            if (!needCallGateway) {
                fillSuccess(response);
                response.setStatus(defaultString(signInfo.getContractStatus(), STATUS_NOT_SIGNED));
                response.setPayUserId(signInfo.getPayAccountId());
                response.setPayAccountId(signInfo.getPayAccountId());
                response.setPayAgreementNo(signInfo.getPayAgreementNo());
                writeLog("REQUEST_CONTRACT_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            PaySignGatewayResponse gatewayResponse = requestGatewaySimple(paySignProperties.getContractResultPath(), buildQueryResultBizData(request.getRequestSignSeq()));

            if (!isGatewaySuccess(gatewayResponse)) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, gatewayErrorMsg(gatewayResponse, "request pay contract result failed"));
                writeLog("REQUEST_CONTRACT_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            if (gatewayResponse.getData() != null) {
                Map<String, Object> data = gatewayResponse.getData();
                if (!existed) {
                    signInfo = new PaySignInfo();
                    signInfo.setRequestSignSeq(request.getRequestSignSeq());
                    signInfo.setThirdUserId(request.getThirdUserId());
                    signInfo.setPaymentVendor(request.getPaymentVendor());
                    signInfo.setSignChannel(signChannel);
                }
                // 以支付平台返回结果为准，刷新本地签约状态和协议号。
                signInfo.setContractStatus(stringValue(data.get("status"), signInfo.getContractStatus()));
                signInfo.setPayAccountId(stringValue(data.get("payUserId"), signInfo.getPayAccountId()));
                signInfo.setPayAgreementNo(stringValue(data.get("payAgreementNo"), signInfo.getPayAgreementNo()));
                if (existed) {
                    paySignInfoMapper.updateBySeq(signInfo);
                } else if (STATUS_SIGNED.equals(signInfo.getContractStatus())) {
                    paySignInfoMapper.insert(signInfo);
                }
            } else if (!existed) {
                signInfo = new PaySignInfo();
                signInfo.setThirdUserId(request.getThirdUserId());
                signInfo.setRequestSignSeq(request.getRequestSignSeq());
                signInfo.setPaymentVendor(request.getPaymentVendor());
                signInfo.setSignChannel(signChannel);
                signInfo.setContractStatus(STATUS_NOT_SIGNED);
            }

            fillSuccess(response);
            response.setStatus(defaultString(signInfo.getContractStatus(), STATUS_NOT_SIGNED));
            response.setPayUserId(signInfo.getPayAccountId());
            response.setPayAccountId(signInfo.getPayAccountId());
            response.setPayAgreementNo(signInfo.getPayAgreementNo());
            writeLog("REQUEST_CONTRACT_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
            return response;
        } catch (Exception e) {
            log.error("处理签约结果查询异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            writeLog("REQUEST_CONTRACT_RESULT", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * IF8A-06 请求解约。
     * 对外仍保留 ITP 侧报文风格；T+4 日前仅记录解约申请，不调用支付平台。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel) {
        RequestTerminationRespDTO response = new RequestTerminationRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                writeLog("REQUEST_TERMINATION", null, null, null, signChannel, null, response);
                return response;
            }
            String validMsg = validateRequestTermination(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                writeLog("REQUEST_TERMINATION", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 1. 查询是否已签约（只做校验，不修改）
            PaySignInfo signInfo = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
            if (signInfo == null) {
                fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, PaySignErrorCodeEnum.USER_NOT_SIGNED.getMsg());
                writeLog("REQUEST_TERMINATION", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 2. 校验是否已存在待处理解约申请
            AppTerminationRequest existRequest = terminationRequestMapper.selectPendingByUserId(request.getThirdUserId());
            if (existRequest != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_TERMINATING, "用户正在解约中");
                writeLog("REQUEST_TERMINATION", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 3. 插入解约申请记录（状态=PENDING，通知状态=PENDING）
            AppTerminationRequest terminationRequest = new AppTerminationRequest();
            terminationRequest.setRequestSignSeq(request.getRequestSignSeq());
            terminationRequest.setThirdUserId(request.getThirdUserId());
            terminationRequest.setCardId(request.getCardId());
            terminationRequest.setCardType(request.getCardType());
            terminationRequest.setPaymentVendor(request.getPaymentVendor());
            terminationRequest.setTerminationStatus(STATUS_PENDING);
            terminationRequest.setNotifyStatus(STATUS_PENDING);
            terminationRequest.setNotifyRetryCount(0);
            terminationRequest.setRequestTime(LocalDateTime.now());
            terminationRequestMapper.insert(terminationRequest);

            // T+4 日前不调用支付平台，不操作 APP_PAY_SIGN_INFO 与 USER_ITP_REG_INFO
            fillSuccess(response);
            writeLog("REQUEST_TERMINATION", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
            return response;
        } catch (Exception e) {
            log.error("处理请求解约异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            writeLog("REQUEST_TERMINATION", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * 支付 API 1.1 请求支付。
     * 内部调用方只传业务参数，支付网关公共参数和签名在 pay-sign-server 内统一完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestPayResult requestPay(RequestPayReqDTO request) {
        RequestPayResult response = new RequestPayResult();
        try {
            // 重试路径：request 中 paymentVendor/requestSignSeq 可能为 null，从 PAY_TXN_DETAIL 补充
            if (request != null && StringUtils.hasText(request.getOrderNo())) {
                PayTxnDetail txn = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
                if (txn != null) {
                    if (!StringUtils.hasText(request.getPaymentVendor())) {
                        request.setPaymentVendor(txn.getPaymentVendor());
                    }
                    if (!StringUtils.hasText(request.getRequestSignSeq())) {
                        request.setRequestSignSeq(txn.getRequestSignSeq());
                    }
                } else if (!StringUtils.hasText(request.getPaymentVendor())
                        || !StringUtils.hasText(request.getRequestSignSeq())) {
                    // PAY_TXN_DETAIL 不存在时，从 account-server 查询签约信息并创建记录
                    // 防止 retryPay 等场景因签约信息缺失导致 ensurePayTxn 无法执行
                    resolveAndCreatePayTxnFromAccount(request);
                }
            }
            // 测试阶段可强制覆盖支付金额（分），0 表示不覆盖，使用调用方传入的实际金额
            int forceAmount = paySignProperties.getTestForceAmount();
            if (forceAmount > 0) {
                request.setAmount(forceAmount);
            }
            String validMsg = validateRequestPay(request);

            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_PAY 参数校验失败, request={}, response={}", JSON.toJSONString(request), JSON.toJSONString(response));
                return response;
            }

            if (!validatePaySignInfo(request, response)) {
                log.info("REQUEST_PAY 签约信息校验失败, request={}, response={}",
                        JSON.toJSONString(request), JSON.toJSONString(response));
                return response;
            }

            ensurePayTxn(request);
            payTxnDetailMapper.markRequesting(request.getOrderNo());
            log.info("REQUEST_PAY ensurePayTxn完成, orderNo={}, paymentVendor={}, discountFee={}, discountInfo={}",
                    request.getOrderNo(), request.getPaymentVendor(), request.getDiscountFee(), request.getDiscountInfo());

            Map<String, Object> bizData = buildRequestPayBizData(request);
            log.info("REQUEST_PAY 调用支付平台, orderNo={}, thirdUserId={}, paymentVendor={}, amount={}, discountFee={}, discountInfo={}, bizData={}",
                    request.getOrderNo(), request.getThirdUserId(), request.getPaymentVendor(), request.getAmount(),
                    request.getDiscountFee(), request.getDiscountInfo(), JSON.toJSONString(bizData));
            PaySignGatewayResponse gatewayResponse = requestGatewaySimple(paySignProperties.getRequestPayPath(), bizData);
            log.info("REQUEST_PAY 支付平台返回, orderNo={}, gatewayResponse={}", request.getOrderNo(), JSON.toJSONString(gatewayResponse));

            if (!isGatewaySuccess(gatewayResponse)) {
                // 特殊业务码：订单已支付成功（幂等场景）
                if (isAlreadyPaidSuccess(gatewayResponse)) {
                    log.info("REQUEST_PAY 订单已支付成功（幂等），orderNo={}", request.getOrderNo());
                    fillSuccess(response);
                    fillGatewayFields(response, gatewayResponse);
                    updatePayRequestResult(request.getOrderNo(), "SUCCESS", response, null);
                    return response;
                }

                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, gatewayErrorMsg(gatewayResponse, "请求支付接口失败"));
                fillGatewayFields(response, gatewayResponse);
                String transIn = gatewayResponse.getData() != null ? stringValue(gatewayResponse.getData().get("transIn"), null) : null;
                updatePayRequestResult(request.getOrderNo(), "RETRY", response, transIn);

                // 支付宝渠道支付失败，添加黑名单
                String paymentVendor = request.getPaymentVendor();
                if (StringUtils.hasText(paymentVendor)
                        && ("03".equals(paymentVendor) || "05".equals(paymentVendor))) {
                    addBlacklistForPaymentFailure(request, gatewayResponse);
                }

                return response;
            }

            fillSuccess(response);
            fillGatewayFields(response, gatewayResponse);
            String transIn = gatewayResponse.getData() != null ? stringValue(gatewayResponse.getData().get("transIn"), null) : null;
            if (gatewayResponse.getData() != null) {
                response.setOrderNo(stringValue(gatewayResponse.getData().get("merchantOrderNo"), request.getOrderNo()));
                response.setMerchantOrderNo(stringValue(gatewayResponse.getData().get("orderNo"), null));
                response.setChannelOrderNo(stringValue(gatewayResponse.getData().get("channelOrderNo"), null));
                response.setPayData(stringValue(gatewayResponse.getData().get("data"), null));
            } else {
                response.setOrderNo(request.getOrderNo());
            }
            updatePayRequestResult(request.getOrderNo(), "PROCESSING", response, transIn);
            return response;
        } catch (Exception e) {
            log.error("处理请求支付异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            if (request != null && StringUtils.hasText(request.getOrderNo())) {
                try {
                    // 只有当前状态不是 PROCESSING 时才回退为 RETRY，避免覆盖正在处理中的状态
                    PayTxnDetail current = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
                    if (current == null || !"PROCESSING".equals(current.getPayStatus())) {
                        updatePayRequestResult(request.getOrderNo(), "RETRY", response, null);
                    } else {
                        log.warn("支付请求异常但订单已处于PROCESSING状态，跳过状态回退, orderNo={}", request.getOrderNo());
                    }
                } catch (Exception ex) {
                    log.error("查询或更新PAY_TXN_DETAIL异常, orderNo={}, 订单需人工补偿", request.getOrderNo(), ex);
                }
            }
            return response;
        }
    }

    /**
     * 支付 API 3.1 请求退款。
     *
     * <p>调用方只传 orderNo/refundAmount；pay-sign 根据原支付订单补齐 merchantOrderNo，
     * 生成 refundOrderNo，并负责退款明细入库和原支付订单退款汇总回写。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestRefundResult requestRefund(RequestRefundReqDTO request) {
        RequestRefundResult response = new RequestRefundResult();
        try {
            String validMsg = validateRequestRefund(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_REFUND 参数校验失败, request={}, response={}", JSON.toJSONString(request), JSON.toJSONString(response));
                return response;
            }

            PayTxnDetail payTxn = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
            validMsg = validateRefundPayTxn(payTxn, request.getRefundAmount());
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_REFUND 原支付订单校验失败, request={}, payTxn={}, response={}",
                        JSON.toJSONString(request), JSON.toJSONString(payTxn), JSON.toJSONString(response));
                return response;
            }

            PayRefundDetail refundDetail = buildPayRefundDetail(request, payTxn);
            payRefundDetailMapper.insert(refundDetail);

            Map<String, Object> bizData = buildRequestRefundBizData(refundDetail, payTxn);
            payRefundDetailMapper.markRequesting(refundDetail.getRefundOrderNo(), refundDetail.getTxnDate(), JSON.toJSONString(bizData));
            log.info("REQUEST_REFUND 调用支付平台, orderNo={}, refundOrderNo={}, bizData={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(bizData));
            PaySignGatewayResponse gatewayResponse = requestGatewaySimple(paySignProperties.getRequestRefundPath(), bizData);
            log.info("REQUEST_REFUND 支付平台返回, orderNo={}, refundOrderNo={}, gatewayResponse={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(gatewayResponse));

            response.setOrderNo(refundDetail.getOrderNo());
            response.setRefundOrderNo(refundDetail.getRefundOrderNo());
            fillGatewayFields(response, gatewayResponse);

            if (!isGatewaySuccess(gatewayResponse)) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, gatewayErrorMsg(gatewayResponse, "请求退款接口失败"));
                fillGatewayFields(response, gatewayResponse);
                updateRefundRequestResult(refundDetail, "RETRY", response, gatewayResponse);
                return response;
            }

            fillSuccess(response);
            fillRefundResponseFields(response, refundDetail, gatewayResponse);
            updateRefundRequestResult(refundDetail, "SUCCESS", response, gatewayResponse);
            payTxnDetailMapper.updateRefundSummary(payTxn.getOrderNo(), request.getRefundAmount(),
                    resolveRefundSummaryStatus(payTxn, request.getRefundAmount()));
            return response;
        } catch (Exception e) {
            log.error("处理请求退款异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * IPD02 签约结果回调。
     * 支付平台异步通知签约结果时，以回调报文为准刷新本地签约主表。
     */
    /**
     * 支付 API 5.1 支付回调。
     *
     * <p>fep-app 负责接收支付平台回调并解析 bizData，pay-sign-server 负责保存回调流水
     * 并回写 PAY_TXN_DETAIL 当前状态。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        try {
            if (request == null || !StringUtils.hasText(request.getOrderNo())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "orderNo不能为空");
                return response;
            }
            PayCallbackLog callbackLog = buildPayCallbackLog(request, rawBody);
            payCallbackLogMapper.insert(callbackLog);

            PayTxnDetail update = new PayTxnDetail();
            update.setOrderNo(request.getOrderNo());
            update.setPayStatus(convertPayStatus(request.getStatus()));
            update.setMerchantOrderNo(request.getMerchantOrderNo());
            update.setChannelOrderNo(request.getChannelOrderNo());
            update.setTotalAmount(request.getTotalAmount());
            update.setCashAmount(request.getCashAmount());
            update.setCouponAmount(request.getCouponAmount());
            update.setPayUserId(request.getPayUserId());
            update.setPaymentVendor(request.getPaymentVendor());
            update.setPayTime(request.getPayTime());
            // 优惠字段、金额字段在回调中通常为空，SQL 使用 NVL 保留入库时的原始值
            // PAY_STATUS 由 SQL 状态机保护：SUCCESS 状态的订单不允许被回退为非 SUCCESS 状态
            payTxnDetailMapper.updatePayCallback(update);

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("处理支付结果回调异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                writeLog("RECEIVE_SIGN_RESULT", null, null, null, null, null, response);
                return response;
            }
            String validMsg = validateReceiveSignResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                writeLog("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 支付平台回调可能不带 thirdUserId，尝试从流水表补充
            String thirdUserId = resolveThirdUserId(request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                writeLog("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            // 支付平台回调可能不带 displayAccount，尝试从流水表补充
            String displayAccount = resolveDisplayAccount(request.getRequestSignSeq(), request.getDisplayAccount());
            request.setDisplayAccount(displayAccount);

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            log.info("签约成功，准备通知app--- {} ., displayAccount: {}",request,displayAccount);
            if (isSuccess) {
                // 1. INSERT 签约成功记录
                PaySignInfo signInfo = new PaySignInfo();
                signInfo.setRequestSignSeq(request.getRequestSignSeq());
                signInfo.setThirdUserId(request.getThirdUserId());
                signInfo.setPaymentVendor(request.getPaymentVendor());
                signInfo.setSignChannel(signChannel);
                signInfo.setDisplayAccount(request.getDisplayAccount());
                signInfo.setPayAccountId(request.getPayUserId());
                signInfo.setPayAgreementNo(request.getPayAgreementNo());
                signInfo.setContractStatus(STATUS_SIGNED);
                signInfo.setSignTime(parseDateTime(request.getSignTime(), null));
                paySignInfoMapper.insert(signInfo);

                // 2. 写入流水表
                PaySignRequest logRecord = new PaySignRequest();
                logRecord.setRequestSignSeq(request.getRequestSignSeq());
                logRecord.setThirdUserId(request.getThirdUserId());
                logRecord.setPaymentVendor(request.getPaymentVendor());
                logRecord.setSignChannel(signChannel);
                logRecord.setOperationType("RECEIVE_SIGN_RESULT");
                logRecord.setSignStatus(STATUS_SIGNED);
                logRecord.setPayAccountId(request.getPayUserId());
                logRecord.setPayAgreementNo(request.getPayAgreementNo());
                logRecord.setCreateTms(LocalDateTime.now());
                logRecord.setNotifyStatus("PENDING");
                logRecord.setNotifyRetryCount(0);
                paySignRequestMapper.insert(logRecord);

                // 回填自增主键，避免后续更新通知状态时 ID 为 null
                logRecord = paySignRequestMapper.selectByRequestSignSeq(logRecord.getRequestSignSeq());

                // 3. 异步通知App（不阻塞返回）
                appNotifyService.asyncNotifySignResult(logRecord, signInfo, request);
            } else {
                // 签约失败，记录流水但不通知
                writeLog("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(),
                         request.getPaymentVendor(), signChannel, request, response, STATUS_FAILED);
            }

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("处理签约结果通知异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            writeLog("RECEIVE_SIGN_RESULT", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * IPD03 解约结果回调。
     * 支付平台异步通知解约结果时，补齐解约时间和最终状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                writeLog("RECEIVE_TERMINATION_RESULT", null, null, null, null, null, response);
                return response;
            }
            String validMsg = validateReceiveTerminationResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                writeLog("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 支付平台回调可能不带 thirdUserId，尝试从流水表补充
            String thirdUserId = resolveThirdUserId(request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                writeLog("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            // 支付平台回调可能不带 cardId/cardType，尝试从签约主表补充
            resolveCardInfoFromSignInfo(request);

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            if (isSuccess) {
                // 查询签约记录用于通知（删除前先查询）
                PaySignInfo signInfo = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());

                // 1. DELETE 签约记录
                paySignInfoMapper.deleteByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());

                // 2. 清理账户支付通道
                removeAccountPayChannel(request.getThirdUserId(), request.getPaymentVendor(), request.getCardId(), request.getCardType());

                // 3. 写入流水表
                PaySignRequest logRecord = new PaySignRequest();
                logRecord.setRequestSignSeq(request.getRequestSignSeq());
                logRecord.setThirdUserId(request.getThirdUserId());
                logRecord.setPaymentVendor(request.getPaymentVendor());
                logRecord.setSignChannel(signChannel);
                logRecord.setOperationType("RECEIVE_TERMINATION_RESULT");
                logRecord.setSignStatus(STATUS_UNSIGNED);
                logRecord.setCardId(request.getCardId());
                logRecord.setCardType(request.getCardType());
                logRecord.setTerminationTime(request.getDismissalTime());
                logRecord.setCreateTms(LocalDateTime.now());
                logRecord.setNotifyStatus("PENDING");
                logRecord.setNotifyRetryCount(0);
                paySignRequestMapper.insert(logRecord);

                // 回填自增主键，避免后续更新通知状态时 ID 为 null
                logRecord = paySignRequestMapper.selectByRequestSignSeq(logRecord.getRequestSignSeq());

                // 4. 异步通知App（不阻塞返回）
                appNotifyService.asyncNotifyTerminationResult(logRecord, signInfo, request);
            } else {
                // 解约失败，记录流水但不通知
                writeLog("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(),
                         request.getPaymentVendor(), signChannel, request, response, STATUS_FAILED);
            }

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("处理解约结果通知异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            writeLog("RECEIVE_TERMINATION_RESULT", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * 校验签约请求必要字段。
     */
    private String validateRequestSignInfo(RequestSignInfoReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getDisplayAccount())) {
            return "displayAccount不能为空";
        }
        if (!StringUtils.hasText(request.getPayChannelCode())) {
            return "payChannelCode不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        return null;
    }

    /**
     * 校验签约查询类接口的公共参数。
     */
    private String validateContractQuery(String thirdUserId, String requestSignSeq, String paymentVendor) {
        if (!StringUtils.hasText(thirdUserId)) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(paymentVendor)) {
            return "paymentVendor不能为空";
        }
        return null;
    }

    /**
     * 校验解约请求必要字段。
     */
    private String validateRequestTermination(RequestTerminationReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        return null;
    }

    /**
     * 校验请求支付必要字段。
     */
    private String validateRequestPay(RequestPayReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (!StringUtils.hasText(request.getScene())) {
            return "scene不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (request.getAmount() == null) {
            return "amount不能为空";
        }
        if (request.getAmount() < 0) {
            return "amount不能小于0";
        }
        if (!StringUtils.hasText(request.getIndustryType())) {
            return "industryType不能为空";
        }
        if (!StringUtils.hasText(request.getSubject())) {
            return "subject不能为空";
        }
        if (!StringUtils.hasText(request.getBody())) {
            return "body不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }

    /**
     * 校验请求退款必要字段。
     */
    private String validateRequestRefund(RequestRefundReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (request.getRefundAmount() == null) {
            return "refundAmount不能为空";
        }
        if (request.getRefundAmount() <= 0) {
            return "refundAmount必须大于0";
        }
        return null;
    }

    /**
     * 校验原支付订单是否允许退款。
     */
    private String validateRefundPayTxn(PayTxnDetail payTxn, Integer refundAmount) {
        if (payTxn == null) {
            return "原支付订单不存在";
        }
        if (!"SUCCESS".equals(payTxn.getPayStatus())) {
            return "原支付订单未支付成功";
        }
        if (!StringUtils.hasText(payTxn.getMerchantOrderNo())) {
            return "原支付订单缺少merchantOrderNo";
        }
        int paidAmount = resolvePaidAmount(payTxn);
        int refundedAmount = payTxn.getRefundAmount() == null ? 0 : payTxn.getRefundAmount();
        if (refundAmount > paidAmount - refundedAmount) {
            return "退款金额超出可退金额";
        }
        return null;
    }

    /**
     * 校验签约结果回调必要字段。
     */
    private String validateReceiveSignResult(ReceiveSignResultReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return "paymentVendor不能为空";
        }
        if (!StringUtils.hasText(request.getStatus())) {
            return "status不能为空";
        }
        return null;
    }

    /**
     * 校验解约结果回调必要字段。
     */
    private String validateReceiveTerminationResult(ReceiveTerminationResultReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return "paymentVendor不能为空";
        }
        if (!StringUtils.hasText(request.getStatus())) {
            return "status不能为空";
        }
        return null;
    }

    /**
     * 统一封装失败应答。
     * 兼容支付平台要求的 code/msg/success，同时保留 ITP 侧沿用的 retCode/retMsg。
     */
    private void fillError(BaseRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    /**
     * requestSignInfo 接口返回模型单独适配。
     */
    private void fillError(RequestSignInfoResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    private void fillError(PaySignCallbackResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    private void fillError(RequestPayResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    private void fillError(RequestRefundResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    /**
     * 统一封装成功应答。
     */
    private void fillSuccess(BaseRespDTO response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    /**
     * requestSignInfo 接口返回模型单独适配。
     */
    private void fillSuccess(RequestSignInfoResult response) {
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    private void fillSuccess(PaySignCallbackResult response) {
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    private void fillSuccess(RequestPayResult response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    private void fillSuccess(RequestRefundResult response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    /**
     * 支付平台回调可能不带 thirdUserId，尝试从流水表补充。
     */
    private String resolveThirdUserId(String requestSignSeq, String thirdUserId) {
        if (StringUtils.hasText(thirdUserId)) {
            return thirdUserId;
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return null;
        }
        try {
            PaySignRequest record = paySignRequestMapper.selectByRequestSignSeq(requestSignSeq);
            if (record != null && StringUtils.hasText(record.getThirdUserId())) {
                log.info("从流水表补充 thirdUserId, requestSignSeq={}, thirdUserId={}", requestSignSeq, record.getThirdUserId());
                return record.getThirdUserId();
            }
        } catch (Exception e) {
            log.warn("查询流水表补充 thirdUserId 异常, requestSignSeq={}", requestSignSeq, e);
        }
        return null;
    }

    /**
     * 支付平台回调可能不带 displayAccount，尝试从流水表补充。
     */
    private String resolveDisplayAccount(String requestSignSeq, String displayAccount) {
        if (StringUtils.hasText(displayAccount)) {
            return displayAccount;
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return null;
        }
        try {
            PaySignRequest record = paySignRequestMapper.selectByRequestSignSeq(requestSignSeq);
            if (record != null && StringUtils.hasText(record.getDisplayAccount())) {
                log.info("从流水表补充 displayAccount, requestSignSeq={}, displayAccount={}", requestSignSeq, record.getDisplayAccount());
                return record.getDisplayAccount();
            }
        } catch (Exception e) {
            log.warn("查询流水表补充 displayAccount 异常, requestSignSeq={}", requestSignSeq, e);
        }
        return null;
    }

    /**
     * 支付平台回调可能不带 cardId/cardType，尝试从签约主表补充。
     * 解约回调时，签约记录还存在（尚未 DELETE），可反查补齐。
     */
    private void resolveCardInfoFromSignInfo(ReceiveTerminationResultReqDTO request) {
        if (StringUtils.hasText(request.getCardId()) && StringUtils.hasText(request.getCardType())) {
            return;
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return;
        }
        try {
            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
            if (signInfo != null) {
                if (!StringUtils.hasText(request.getCardId()) && StringUtils.hasText(signInfo.getCardId())) {
                    request.setCardId(signInfo.getCardId());
                    log.info("从签约主表补充 cardId, requestSignSeq={}, cardId={}", request.getRequestSignSeq(), signInfo.getCardId());
                }
                if (!StringUtils.hasText(request.getCardType()) && StringUtils.hasText(signInfo.getCardType())) {
                    request.setCardType(signInfo.getCardType());
                    log.info("从签约主表补充 cardType, requestSignSeq={}, cardType={}", request.getRequestSignSeq(), signInfo.getCardType());
                }
            }
        } catch (Exception e) {
            log.warn("查询签约主表补充 cardId/cardType 异常, requestSignSeq={}", request.getRequestSignSeq(), e);
        }
    }

    /**
     * 解约成功后清理 account-server 侧支付通道信息。
     *
     * <p>account-server 会先删除支付渠道表记录；如果该通道正好是注册信息里的默认支付通道，
     * 会同步清空注册信息里的 thirdPayId/channel/reqContractNo。</p>
     */
    private boolean removeAccountPayChannel(String thirdUserId, String paymentVendor, String cardId, String cardType) {
        try {
            RequestRemovePayChannelReqDTO request = new RequestRemovePayChannelReqDTO();
            request.setThirdUserId(thirdUserId);
            request.setCardId(cardId);
            request.setCardType(cardType);
            request.setChannel(paymentVendor);
            log.info("call account removePayChannel request={}", JSON.toJSONString(request));
            RequestRemovePayChannelResult response = accountClient.requestRemovePayChannel(request);
            log.info("call account removePayChannel response={}", JSON.toJSONString(response));
            boolean success = response != null && PaySignErrorCodeEnum.SUCCESS.getCode().equals(response.getRetCode());
            if (!success) {
                log.error("清理账户支付通道失败, request={}, response={}", JSON.toJSONString(request), JSON.toJSONString(response));
            }
            return success;
        } catch (Exception e) {
            log.error("清理账户支付通道异常", e);
            return false;
        }
    }

    /**
     * 将 ITP 侧的 paymentVendor 归一化。
     */
    private String normalizeVendor(String paymentVendor) {
        if (!StringUtils.hasText(paymentVendor)) {
            return null;
        }
        String code = paymentVendor.trim();
        if (!PaymentVendorEnum.isValid(code)) {
            log.warn("未知的支付渠道编码: {}", code);
        }
        return code;
    }

    /**
     * 解析签约回调地址。
     * 优先使用请求中的 notifyUrl，其次使用配置默认值，最后退化为 returnUrl。
     */
    private String resolveNotifyUrl(RequestSignInfoReqDTO request) {
        if (StringUtils.hasText(request.getNotifyUrl())) {
            return request.getNotifyUrl();
        }
        if (StringUtils.hasText(paySignProperties.getDefaultNotifyUrl())) {
            return paySignProperties.getDefaultNotifyUrl();
        }
        return request.getReturnUrl();
    }

    /**
     * 将内部细分操作归并成新日志表设计中的 SIGN/UNSIGN。
     */
    private String convertOperationType(String operationType) {
        if (!StringUtils.hasText(operationType)) {
            return "SIGN";
        }
        String upper = operationType.toUpperCase();
        if (upper.contains("TERMINATION") || upper.contains("UNSIGN")) {
            return "UNSIGN";
        }
        return "SIGN";
    }

    /**
     * URL 编码工具方法。
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 解析支付平台返回的 yyyyMMddHHmmss 时间格式。
     */
    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATETIME_FORMATTER);
        } catch (Exception e) {
            log.warn("解析时间失败, value={}", value);
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime defaultValue) {
        LocalDateTime parsed = parseDateTime(value);
        return parsed == null ? defaultValue : parsed;
    }

    /**
     * 从对象中安全提取字符串值。
     */
    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    /**
     * 字符串为空时返回默认值。
     */
    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 组装 creditQuery 请求参数。
     */
    private Map<String, Object> buildCreditQueryBizData(String thirdUserId, String requestSignSeq, String paymentVendor) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("thirdUserId", thirdUserId);
        bizData.put("requestSignSeq", requestSignSeq);
        bizData.put("paymentVendor", paymentVendor);
        return bizData;
    }

    /**
     * 组装 queryResult 请求参数。
     */
    private Map<String, Object> buildQueryResultBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    /**
     * 组装 dismissal 解约请求参数。
     */
    private Map<String, Object> buildDismissalBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    /**
     * 供内部解约流程调用，向支付平台发起解约请求。
     *
     * @param requestSignSeq 签约流水号
     * @return 支付平台网关响应
     */
    public PaySignGatewayResponse requestPayPlatformTermination(String requestSignSeq) {
        return requestGatewaySimple(paySignProperties.getTerminationPath(), buildDismissalBizData(requestSignSeq));
    }

    /**
     * 组装 requestPay 请求业务参数。
     */
    private Map<String, Object> buildRequestPayBizData(RequestPayReqDTO request) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", request.getOrderNo());
        bizData.put("scene", request.getScene());
        bizData.put("paymentVendor", request.getPaymentVendor());
        bizData.put("amount", request.getAmount());
        bizData.put("industryType", request.getIndustryType());
        bizData.put("subject", request.getSubject());
        bizData.put("body", request.getBody());
        putIfHasText(bizData, "requestSignSeq", request.getRequestSignSeq());
        putIfHasText(bizData, "thirdUserId", request.getThirdUserId());
        putIfHasText(bizData, "industryDetail", request.getIndustryDetail());
        if (request.getOrderTimeOut() != null) {
            bizData.put("orderTimeOut", request.getOrderTimeOut());
        }
        putIfHasText(bizData, "authCode", request.getAuthCode());
        putIfHasText(bizData, "notifyUrl", resolvePayNotifyUrl(request));
        putIfHasText(bizData, "returnUrl", request.getReturnUrl());
        putIfHasText(bizData, "ipAddress", request.getIpAddress());
        putIfHasText(bizData, "remark", request.getRemark());
        return bizData;
    }

    /**
     * 创建支付订单当前态记录。
     *
     * <p>同一个 orderNo 重复请求时视为幂等，不重复插入，只继续走请求支付和次数累加。</p>
     */
    private void ensurePayTxn(RequestPayReqDTO request) {
        PayTxnDetail record = new PayTxnDetail();
        record.setOrderNo(request.getOrderNo());
        record.setPayType("PAY");
        record.setPayStatus("INIT");
        record.setThirdUserId(request.getThirdUserId());
        record.setCardId(request.getCardId());
        record.setCardType(request.getCardType());
        record.setPaymentVendor(request.getPaymentVendor());
        record.setRequestSignSeq(request.getRequestSignSeq());
        record.setAmount(request.getAmount());
        record.setRefundStatus("NONE");
        record.setRefundAmount(0);
        record.setRequestCount(0);
        record.setTxnDate(resolveTxnDate());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDiscountInfo(request.getDiscountInfo());
        record.setDiscountFee(request.getDiscountFee());
        try {
            payTxnDetailMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("ensurePayTxn 并发插入重复，orderNo={}", request.getOrderNo(), e);
        }
    }

    /**
     * 校验过闸扣费发起支付所需的签约信息是否已透传。
     *
     * <p>paymentVendor / requestSignSeq 已由 ticket-server 从 account-server 查询后透传，
     * pay-sign-server 不再重复查询，缺失即视为用户未签约。</p>
     */
    private boolean validatePaySignInfo(RequestPayReqDTO request, RequestPayResult response) {
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "paymentVendor不能为空");
            return false;
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "requestSignSeq不能为空");
            return false;
        }
        return true;
    }

    /**
     * 兜底逻辑：当 PAY_TXN_DETAIL 不存在且 request 中签约信息缺失时，
     * 从 account-server 查询 USER_ITP_REG_INFO 获取 paymentVendor/requestSignSeq
     * 并创建 PAY_TXN_DETAIL 记录，避免 retryPay 等场景形成死锁。
     */
    private void resolveAndCreatePayTxnFromAccount(RequestPayReqDTO request) {
        if (!StringUtils.hasText(request.getCardId()) || !StringUtils.hasText(request.getThirdUserId())) {
            log.warn("resolveAndCreatePayTxnFromAccount: cardId或thirdUserId为空，跳过, orderNo={}", request.getOrderNo());
            return;
        }
        try {
            QueryUserInfoReqDTO queryReq = new QueryUserInfoReqDTO();
            queryReq.setCardId(request.getCardId());
            queryReq.setThirdUserId(request.getThirdUserId());
            queryReq.setCardType(request.getCardType());
            QueryUserInfoResult userInfo = accountClient.queryUserInfo(queryReq);
            if (userInfo != null && StringUtils.hasText(userInfo.getChannel())) {
                request.setPaymentVendor(userInfo.getChannel().trim());
            } else {
                log.warn("resolveAndCreatePayTxnFromAccount: account-server未返回channel, cardId={}", request.getCardId());
            }
            if (userInfo != null && StringUtils.hasText(userInfo.getReqContractNo())) {
                request.setRequestSignSeq(userInfo.getReqContractNo().trim());
            } else {
                log.warn("resolveAndCreatePayTxnFromAccount: account-server未返回reqContractNo, cardId={}", request.getCardId());
            }
            if (StringUtils.hasText(request.getPaymentVendor()) && StringUtils.hasText(request.getRequestSignSeq())) {
                ensurePayTxn(request);
                log.info("resolveAndCreatePayTxnFromAccount: 从account-server补充签约信息并创建PAY_TXN_DETAIL, orderNo={}, paymentVendor={}, requestSignSeq={}",
                        request.getOrderNo(), request.getPaymentVendor(), request.getRequestSignSeq());
            }
        } catch (Exception e) {
            log.error("resolveAndCreatePayTxnFromAccount: 查询account-server异常, orderNo={}", request.getOrderNo(), e);
        }
    }

    private void updatePayRequestResult(String orderNo, String payStatus, RequestPayResult response, String transIn) {
        if (!StringUtils.hasText(orderNo)) {
            log.error("updatePayRequestResult: orderNo 为空，跳过更新, payStatus={}", payStatus);
            return;
        }
        PayTxnDetail record = new PayTxnDetail();
        record.setOrderNo(orderNo);
        record.setPayStatus(payStatus);
        record.setMerchantOrderNo(response.getOrderNo());
        record.setChannelOrderNo(response.getChannelOrderNo());
        record.setDebitRequestResult(resolveDebitRequestResult(payStatus));
        record.setResponseTime(LocalDateTime.now());
        record.setTransIn(transIn);
        payTxnDetailMapper.updateRequestResult(record);
    }

    private String resolveDebitRequestResult(String payStatus) {
        if ("PROCESSING".equals(payStatus)) return "PROCESSING";
        if ("SUCCESS".equals(payStatus)) return "SUCCESS";
        return "FAIL";
    }

    /**
     * 创建本地退款明细。
     */
    private PayRefundDetail buildPayRefundDetail(RequestRefundReqDTO request, PayTxnDetail payTxn) {
        PayRefundDetail record = new PayRefundDetail();
        record.setRefundOrderNo(buildRefundOrderNo(request.getOrderNo()));
        record.setOrderNo(payTxn.getMerchantOrderNo());
        record.setRefundStatus("INIT");
        record.setRefundAmount(request.getRefundAmount());
        record.setRefundReason(request.getRefundReason());
        record.setRequestCount(0);
        record.setTxnDate(resolveTxnDate());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    /**
     * 组装支付平台请求退款 bizData。
     */
    private Map<String, Object> buildRequestRefundBizData(PayRefundDetail refundDetail, PayTxnDetail payTxn) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", refundDetail.getRefundOrderNo());
        bizData.put("merchantOrderNo", payTxn.getOrderNo());
        bizData.put("orderNo", refundDetail.getMerchantRefundNo());
        bizData.put("refundAmount", refundDetail.getRefundAmount());
        bizData.put("refundReason", refundDetail.getRefundReason());
        return bizData;
    }

    private void updateRefundRequestResult(PayRefundDetail refundDetail, String refundStatus,
                                           RequestRefundResult response, PaySignGatewayResponse gatewayResponse) {
        PayRefundDetail update = new PayRefundDetail();
        update.setRefundOrderNo(refundDetail.getRefundOrderNo());
        update.setTxnDate(refundDetail.getTxnDate());
        update.setRefundStatus(refundStatus);
        update.setMerchantRefundNo(response.getMerchantRefundNo());
        update.setRefundNo(response.getRefundNo());
        update.setChannelRefundNo(response.getChannelRefundNo());
        update.setRefundTime(response.getRefundTime());
        update.setRetCode(response.getRetCode());
        update.setRetMsg(response.getRetMsg());
        update.setPayCenterCode(gatewayResponse == null ? null : String.valueOf(gatewayResponse.getCode()));
        update.setPayCenterMsg(gatewayResponse == null ? null : gatewayResponse.getMsg());
        update.setResponseBody(JSON.toJSONString(gatewayResponse));
        payRefundDetailMapper.updateRequestResult(update);
    }

    private void fillRefundResponseFields(RequestRefundResult response, PayRefundDetail refundDetail,
                                          PaySignGatewayResponse gatewayResponse) {
        response.setOrderNo(refundDetail.getOrderNo());
        response.setRefundOrderNo(refundDetail.getRefundOrderNo());
        if (gatewayResponse == null || gatewayResponse.getData() == null) {
            return;
        }
        response.setMerchantRefundNo(stringValue(gatewayResponse.getData().get("merchantRefundNo"), null));
        response.setRefundNo(stringValue(gatewayResponse.getData().get("refundNo"), null));
        response.setChannelRefundNo(stringValue(gatewayResponse.getData().get("channelRefundNo"), null));
        response.setRefundTime(stringValue(gatewayResponse.getData().get("refundTime"), null));
    }

    private String resolveRefundSummaryStatus(PayTxnDetail payTxn, Integer currentRefundAmount) {
        int paidAmount = resolvePaidAmount(payTxn);
        int refundedAmount = payTxn.getRefundAmount() == null ? 0 : payTxn.getRefundAmount();
        int totalRefunded = refundedAmount + (currentRefundAmount == null ? 0 : currentRefundAmount);
        return totalRefunded >= paidAmount ? "SUCCESS" : "PARTIAL";
    }

    private int resolvePaidAmount(PayTxnDetail payTxn) {
        if (payTxn.getTotalAmount() != null && payTxn.getTotalAmount() > 0) {
            return payTxn.getTotalAmount();
        }
        return payTxn.getAmount() == null ? 0 : payTxn.getAmount();
    }

    private String buildRefundOrderNo(String orderNo) {
        String suffix = orderNo;
        if (suffix != null && suffix.length() > 8) {
            suffix = suffix.substring(suffix.length() - 8);
        }
        return "RF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + defaultString(suffix, "");
    }

    /**
     * 支付回调地址优先使用本次请求透传值，其次使用支付专用配置。
     */
    private String resolvePayNotifyUrl(RequestPayReqDTO request) {
        if (StringUtils.hasText(request.getNotifyUrl())) {
            return request.getNotifyUrl();
        }
        return paySignProperties.getRequestPayNotifyUrl();
    }

    private PayCallbackLog buildPayCallbackLog(ReceivePayResultReqDTO request, String rawBody) {
        PayCallbackLog logRecord = new PayCallbackLog();
        logRecord.setOrderNo(request.getOrderNo());
        logRecord.setCallbackType("PAY");
        logRecord.setCallbackStatus(request.getStatus());
        logRecord.setMerchantOrderNo(request.getMerchantOrderNo());
        logRecord.setChannelOrderNo(request.getChannelOrderNo());
        logRecord.setPayTime(request.getPayTime());
        logRecord.setTotalAmount(request.getTotalAmount());
        logRecord.setCashAmount(request.getCashAmount());
        logRecord.setCouponAmount(request.getCouponAmount());
        logRecord.setPayUserId(request.getPayUserId());
        logRecord.setPaymentVendor(request.getPaymentVendor());
        logRecord.setTxnDate(resolveTxnDate());
        logRecord.setRawBody(rawBody);
        logRecord.setHandleStatus("SUCCESS");
        logRecord.setCreateTime(LocalDateTime.now());
        return logRecord;
    }

    private String convertPayStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "PROCESSING";
        }
        String normalized = status.trim().toUpperCase();
        if ("SUCCESS".equals(normalized) || "PAID".equals(normalized)) {
            return "SUCCESS";
        }
        if ("FAIL".equals(normalized) || "FAILED".equals(normalized)) {
            return "FAIL";
        }
        return normalized;
    }

    private String resolveTxnDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private void putIfHasText(Map<String, Object> params, String key, String value) {
        if (StringUtils.hasText(value)) {
            params.put(key, value);
        }
    }

    private void fillGatewayFields(RequestPayResult response, PaySignGatewayResponse gatewayResponse) {
        if (gatewayResponse == null) {
            return;
        }
        response.setCode(gatewayResponse.getCode());
        response.setMsg(gatewayResponse.getMsg());
        response.setSuccess(isGatewaySuccess(gatewayResponse));
        response.setData(gatewayResponse.getData());
    }

    private void fillGatewayFields(RequestRefundResult response, PaySignGatewayResponse gatewayResponse) {
        if (gatewayResponse == null) {
            return;
        }
        response.setCode(gatewayResponse.getCode());
        response.setMsg(gatewayResponse.getMsg());
        response.setSuccess(isGatewaySuccess(gatewayResponse));
        response.setData(gatewayResponse.getData());
    }

    /**
     * 调用支付平台 contract 接口发起正式签约。
     * 返回值为支付平台 data 节点的 JSON 字符串。
     */
    private String requestGatewaySignInfo(RequestSignInfoReqDTO request, String paymentVendor) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", request.getRequestSignSeq());
        bizData.put("paymentVendor", paymentVendor);
        bizData.put("thirdUserId", request.getThirdUserId());
        bizData.put("displayAccount", request.getDisplayAccount());
        if (StringUtils.hasText(resolveNotifyUrl(request))) {
            bizData.put("notifyUrl", resolveNotifyUrl(request));
        }
        if (StringUtils.hasText(request.getReturnUrl())) {
            bizData.put("returnUrl", request.getReturnUrl());
        }
        if (StringUtils.hasText(request.getOptions())) {
            bizData.put("options", request.getOptions());
        }
        if (StringUtils.hasText(request.getAuthCode())) {
            bizData.put("authCode", request.getAuthCode());
        }
        // 下列字段为支付平台文档定义的通道扩展字段，按需透传。
        if (StringUtils.hasText(request.getMobilePhone())) {
            bizData.put("mobilePhone", request.getMobilePhone());
        }
        if (StringUtils.hasText(request.getCertNo())) {
            bizData.put("certNo", request.getCertNo());
        }
        if (StringUtils.hasText(request.getCustName())) {
            bizData.put("custName", request.getCustName());
        }
        if (StringUtils.hasText(request.getToken())) {
            bizData.put("token", request.getToken());
        }
        if (StringUtils.hasText(request.getPayUserId())) {
            bizData.put("payUserId", request.getPayUserId());
        }
        if (StringUtils.hasText(request.getBankCardNo())) {
            bizData.put("bankCardNo", request.getBankCardNo());
        }
        log.info("IF8A-16调用支付平台签约, requestSignSeq={}, thirdUserId={}", request.getRequestSignSeq(), request.getThirdUserId());
        PaySignGatewayResponse gatewayResponse = requestGatewaySimple(paySignProperties.getContractPath(), bizData);

        log.info("IF8A-16支付平台返回, requestSignSeq={}, gatewayResponse={}", request.getRequestSignSeq(), JSON.toJSONString(gatewayResponse));
        if (!isGatewaySuccess(gatewayResponse)) {
            return null;
        }
        if (gatewayResponse.getData() == null) {
            log.error("支付签约接口返回失败, code={}, msg={}", gatewayResponse.getCode(), gatewayResponse.getMsg());
            return null;
        }
        return stringValue(gatewayResponse.getData().get("data"), JSON.toJSONString(gatewayResponse.getData()));
    }

    private String buildRequestStartSdkInfo(RequestSignInfoReqDTO request, String paymentVendor) {
        String sdkInfo = requestGatewaySignInfo(request, paymentVendor);
//        if (StringUtils.hasText(sdkInfo)) {
        return sdkInfo;
//        }
//        return buildLocalSdkInfo(request, paymentVendor);
    }

    /**
     * 调用支付平台通用网关。
     * 按公共报文格式组装 merchantNo/apiVersion/signType/charset/bizData/sign。
     */
    private PaySignGatewayResponse requestGatewaySimple(String path, Map<String, Object> bizData) {
        return payGatewayClient.request(path, bizData);
    }

    boolean isGatewaySuccess(PaySignGatewayResponse response) {
        return payGatewayClient.isSuccess(response);
    }

    /**
     * 判断是否为"订单已支付成功"幂等场景（网关返回 code=9999 但业务上已成功）
     */
    private boolean isAlreadyPaidSuccess(PaySignGatewayResponse response) {
        if (response == null) {
            return false;
        }
        Integer code = response.getCode();
        String msg = response.getMsg();
        return Integer.valueOf(9999).equals(code)
                && (msg != null && (msg.contains("已支付成功") || msg.contains("请勿重复支付")));
    }

    private String gatewayErrorMsg(PaySignGatewayResponse response, String defaultMsg) {
        return payGatewayClient.errorMessage(response, defaultMsg);
    }

    /**
     * 支付平台未接通时的本地兜底签约参数拼装。
     * 当前仅对支付宝、微信提供简单模拟，便于前端联调。
     */
    private String buildLocalSdkInfo(RequestSignInfoReqDTO request, String paymentVendor) {
        if ("03".equals(paymentVendor)) {
            String signParams = "app_id=" + encode(paySignProperties.getAlipayMerchantAppId()) + "&third_user_id=" + encode(request.getThirdUserId()) + "&display_account=" + encode(request.getDisplayAccount()) + "&request_sign_seq=" + encode(request.getRequestSignSeq()) + "&return_url=" + encode(request.getReturnUrl());
            if (StringUtils.hasText(request.getAuthCode())) {
                signParams = signParams + "&auth_code=" + encode(request.getAuthCode());
            }
            return "alipays://platformapi/startapp?appId=" + encode(paySignProperties.getAlipayAppId()) + "&appClearTop=false&startMultApp=YES&sign_params=" + encode(signParams);
        }
        if ("04".equals(paymentVendor)) {
            String sdkInfo = paySignProperties.getWechatEntrustUrl() + "?appid=" + encode(paySignProperties.getWechatAppId()) + "&contract_code=" + encode(request.getRequestSignSeq()) + "&contract_display_account=" + encode(request.getDisplayAccount()) + "&notify_url=" + encode(request.getReturnUrl()) + "&request_serial=" + encode(request.getRequestSignSeq()) + "&third_user_id=" + encode(request.getThirdUserId());
            if (StringUtils.hasText(request.getAuthCode())) {
                sdkInfo = sdkInfo + "&auth_code=" + encode(request.getAuthCode());
            }
            return sdkInfo;
        }
        return "";
    }

    /**
     * 从请求对象中提取 displayAccount，用于记录到流水表。
     */
    private String extractDisplayAccount(Object request) {
        if (request == null) {
            return null;
        }
        if (request instanceof RequestSignInfoReqDTO dto) {
            return dto.getDisplayAccount();
        }
        if (request instanceof ReceiveSignResultReqDTO dto) {
            return dto.getDisplayAccount();
        }
        return null;
    }

    /**
     * 记录接口流水日志，保存请求/响应快照。
     * 使用 APP_PAY_SIGN_REQUEST 流水表，每次 INSERT 新记录。
     */
    void writeLog(String operationType, String thirdUserId, String requestSignSeq, String paymentVendor, String signChannel, Object request, Object response) {
        if (!StringUtils.hasText(requestSignSeq)) {
            return;
        }
        try {
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(requestSignSeq);
            logRecord.setThirdUserId(thirdUserId);
            logRecord.setPaymentVendor(paymentVendor);
            logRecord.setSignChannel(signChannel);
            logRecord.setDisplayAccount(extractDisplayAccount(request));
            logRecord.setOperationType(convertOperationType(operationType));
            logRecord.setRequestBody(request == null ? null : JSON.toJSONString(request));
            logRecord.setResponseBody(response == null ? null : JSON.toJSONString(response));
            if (response instanceof BaseRespDTO baseRespDTO) {
                logRecord.setResultCode(baseRespDTO.getRetCode());
                logRecord.setResultMsg(baseRespDTO.getRetMsg());
            }
            logRecord.setCreateTms(LocalDateTime.now());
            paySignRequestMapper.insert(logRecord);
        } catch (Exception e) {
            log.error("记录流水日志异常, requestSignSeq={}, 不影响主事务", requestSignSeq, e);
        }
    }

    /**
     * 支付失败且为支付宝渠道时，添加黑名单。
     */
    private void addBlacklistForPaymentFailure(RequestPayReqDTO request, PaySignGatewayResponse gatewayResponse) {
        try {
            AddBlackListReqDTO blacklistRequest = new AddBlackListReqDTO();
            blacklistRequest.setCardId(request.getCardId());
            blacklistRequest.setCardType(request.getCardType());
            blacklistRequest.setThirdUserId(request.getThirdUserId());

            String gatewayMsg = gatewayResponse != null ? gatewayResponse.getMsg() : null;
            if (StringUtils.hasText(gatewayMsg)) {
                blacklistRequest.setReason(gatewayMsg);
            } else {
                blacklistRequest.setReason("支付中心返回非200, code=" + (gatewayResponse != null ? gatewayResponse.getCode() : "null"));
            }

            log.warn("支付失败，准备添加黑名单, orderNo={}, cardId={}, paymentVendor={}, gatewayCode={}, gatewayMsg={}",
                    request.getOrderNo(), request.getCardId(), request.getPaymentVendor(),
                    gatewayResponse != null ? gatewayResponse.getCode() : "null", gatewayMsg);

            BlackListOperateResult blacklistResult = blacklistClient.addBlackList(blacklistRequest);
            log.info("支付失败添加黑名单结果, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(),
                    blacklistResult != null ? blacklistResult.getRetCode() : "null",
                    blacklistResult != null ? blacklistResult.getRetMsg() : "null");
        } catch (Exception e) {
            log.error("支付失败添加黑名单异常, orderNo={}, cardId={}", request.getOrderNo(), request.getCardId(), e);
        }
    }

    /**
     * 记录接口流水日志（带签约状态）。
     * 使用 APP_PAY_SIGN_REQUEST 流水表，每次 INSERT 新记录。
     */
    private void writeLog(String operationType, String thirdUserId, String requestSignSeq, String paymentVendor, String signChannel, Object request, Object response, String signStatus) {
        if (!StringUtils.hasText(requestSignSeq)) {
            return;
        }
        try {
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(requestSignSeq);
            logRecord.setThirdUserId(thirdUserId);
            logRecord.setPaymentVendor(paymentVendor);
            logRecord.setSignChannel(signChannel);
            logRecord.setDisplayAccount(extractDisplayAccount(request));
            logRecord.setOperationType(convertOperationType(operationType));
            logRecord.setRequestBody(request == null ? null : JSON.toJSONString(request));
            logRecord.setResponseBody(response == null ? null : JSON.toJSONString(response));
            if (response instanceof BaseRespDTO baseRespDTO) {
                logRecord.setResultCode(baseRespDTO.getRetCode());
                logRecord.setResultMsg(baseRespDTO.getRetMsg());
            }
            logRecord.setSignStatus(signStatus);
            logRecord.setCreateTms(LocalDateTime.now());
            paySignRequestMapper.insert(logRecord);
        } catch (Exception e) {
            log.error("记录流水日志异常, requestSignSeq={}, 不影响主事务", requestSignSeq, e);
        }
    }
}
