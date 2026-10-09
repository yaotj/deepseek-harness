package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PayTxnRules.buildPayCallbackLog;
import static com.chinasofti.huateng.paysign.support.PayTxnRules.resolveDebitRequestResult;
import static com.chinasofti.huateng.paysign.support.PayTxnRules.validatePaySignInfo;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestPay;
import static com.chinasofti.huateng.paysign.support.PaySignValues.convertPayStatus;
import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;
import static com.chinasofti.huateng.paysign.support.PaymentChannels.isWallet;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PayCallbackLogMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.paysign.service.PaymentDomainService;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.paysign.port.PaymentGatewayPort;
import com.chinasofti.huateng.paysign.port.PaymentReply;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import com.chinasofti.huateng.paysign.port.AccountUserView;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.paysign.domain.PaySignDuplicateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 支付领域服务：免密扣款（支付 API 1.1）与支付结果回调（5.1）的真实现。 */
@Service
public class PaymentDomainServiceImpl implements PaymentDomainService {
    private static final Logger log = LoggerFactory.getLogger(PaymentDomainServiceImpl.class);

    /** 钱包渠道号常量已收口到 support/PaymentChannels，本类只 import static isWallet(...)。 */

    /**
     * 支付中心网关 §1.1 的 {@code scene} 合法取值（{@code docs/external/支付中心网关接口文档.md:123} 逐字）。
     *
     * <p>本集合只用于打 WARN，NEVER 升级成校验白名单直接拒绝：上游 {@code fep-app-server} 在
     * {@code scene} 为空时会把 {@code channelType} 透传进来，{@code gate-txn-pay-server} 的仓库默认值
     * 长期是非法的 {@code AGM_GATE}（线上靠 env 覆盖成 {@code withholding}），硬拒绝会打挂在跑的免密扣款。
     */
    private static final Set<String> GATEWAY_PAY_SCENES = Set.of("scan", "app", "withholding", "wap", "qrcode");

    private final PaySignProperties paySignProperties;
    private final PayTxnDetailMapper payTxnDetailMapper;
    private final PayCallbackLogMapper payCallbackLogMapper;
    /** 账户域出向调用的唯一出口（ADR-D94 续）。 */
    private final AccountDomainPort accountDomainPort;
    private final BlacklistClient blacklistClient;
    /** 闸机域**扣费状态收敛方向**出向调用的唯一出口（2026-09-17，ADR-D119）。 */
    private final DebitSyncPort debitSyncPort;
    /** 支付中心网关的调用与应答判读收口点。 */
    /** 支付中心**扣款方向**出向调用的唯一出口（2026-09-16，ADR-D113 续）。 */
    private final PaymentGatewayPort paymentGatewayPort;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public PaymentDomainServiceImpl(
            PaySignProperties paySignProperties,
            PayTxnDetailMapper payTxnDetailMapper,
            PayCallbackLogMapper payCallbackLogMapper,
            AccountDomainPort accountDomainPort,
            BlacklistClient blacklistClient,
            DebitSyncPort debitSyncPort,
            PaymentGatewayPort paymentGatewayPort) {
        this.paySignProperties = paySignProperties;
        this.payTxnDetailMapper = payTxnDetailMapper;
        this.payCallbackLogMapper = payCallbackLogMapper;
        this.accountDomainPort = accountDomainPort;
        this.blacklistClient = blacklistClient;
        this.debitSyncPort = debitSyncPort;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    /** 支付 API 1.1 请求支付。 */
    /** 本方法 NEVER 加 @Transactional，理由有两条，第二条比锁竞争严重得多。 */
    @Override
    public RequestPayResult requestPay(RequestPayReqDTO request) {
        RequestPayResult response = new RequestPayResult();
        try {
            PayTxnDetail existingTxn = null;
            if (request != null && StringUtils.hasText(request.getOrderNo())) {
                existingTxn = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
                if (existingTxn != null) {
                    if (!StringUtils.hasText(request.getPaymentVendor())) {
                        request.setPaymentVendor(existingTxn.getPaymentVendor());
                    }
                    if (!StringUtils.hasText(request.getRequestSignSeq())) {
                        request.setRequestSignSeq(existingTxn.getRequestSignSeq());
                    }
                    if (!StringUtils.hasText(request.getPayUserId())) {
                        request.setPayUserId(existingTxn.getPayUserId());
                    }
                } else if (!StringUtils.hasText(request.getPaymentVendor())
                        || !StringUtils.hasText(request.getRequestSignSeq())) {
                    resolvePaySignInfoFromAccount(request);
                }
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

            warnIfSceneOutsideGatewayEnum(request);
            ensurePayTxn(request, existingTxn);
            payTxnDetailMapper.markRequesting(request.getOrderNo());
            log.info("REQUEST_PAY ensurePayTxn完成, orderNo={}, paymentVendor={}, discountFee={}, discountInfo={}",
                    request.getOrderNo(), request.getPaymentVendor(), request.getDiscountFee(), request.getDiscountInfo());

            PaymentReply reply = paymentGatewayPort.requestPay(request);
            PaySignGatewayResponse gatewayResponse = reply.raw();

            if (reply instanceof PaymentReply.AlreadyPaid) {
                log.info("REQUEST_PAY 订单已支付成功（幂等），orderNo={}", request.getOrderNo());
                fillSuccess(response);
                fillGatewayFields(response, reply);
                updatePayRequestResult(request.getOrderNo(), "SUCCESS", response, null);
                return response;
            }
            if (reply instanceof PaymentReply.Rejected rejected) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, rejected.messageOr("请求支付接口失败"));
                fillGatewayFields(response, reply);
                String transIn = reply.data() != null ? stringValue(reply.data().get("transIn"), null) : null;
                updatePayRequestResult(request.getOrderNo(), "RETRY", response, transIn);

                // 自动拉黑只对支付宝系两个通道开（03 支付宝 / 05 支付宝出行，见 PaymentVendorEnum）。
                // 这个白名单是 2026-08-26 生产事故的止血手段、NEVER 当成「漏写了其他通道」随手补全 ——
                // 那次测试卡 0178606904586419 被误拉黑，直接后果是乘客过不了闸。
                // 地铁 APP 自有钱包是 0B（PaymentVendorEnum.WALLET），刻意落在白名单之外：
                // 2026-09-10 用户明确裁决「钱包渠道扣款失败不进黑名单」，欠款走人工追收，
                // 见 docs/ops/生产环境清单.md 的「P2 — 钱包渠道扣款失败不进黑名单」。
                // 2026-09-20 再次评估后用户维持原判，**NEVER 把 0B 加进来、也 NEVER 改成「非空即拉黑」**。
                // 真要恢复自动拉黑，MUST 先复核 8-26 那次的误判条件（该条件目前只有上面那份 ops 文档记着、
                // 代码注释在 ADR-D87 拆分时已丢失），并保留下面 addBlacklistForPaymentFailure 里的
                // queryGatewayPayStatus 二次确认。
                String paymentVendor = request.getPaymentVendor();
                if (StringUtils.hasText(paymentVendor)
                        && ("03".equals(paymentVendor) || "05".equals(paymentVendor))) {
                    addBlacklistForPaymentFailure(request, gatewayResponse);
                }

                return response;
            }
            fillSuccess(response);
            fillGatewayFields(response, reply);
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

    /** 支付 API 5.1 支付回调。 */
    /** 本方法 NEVER 加 @Transactional。 */
    private static final String CALLBACK_TYPE_PAY = "PAY";

    /** 支付结果回调允许支付中心推送的总次数（首推 1 次 + 重推 1 次）。 */
    private static final int MAX_PAY_CALLBACK_PUSH = 2;

    @Override
    // 无 @Transactional 是刻意的、NEVER 加回（2026-08-26 生产事故：事务内调 syncDebitStatus，单请求 287 秒、行锁 45 秒、PAY_CALLBACK_LOG 零条落库）。
    public PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        int pushCount = 0;
        try {
            if (request == null || !StringUtils.hasText(request.getOrderNo())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "orderNo不能为空");
                return response;
            }
            PayCallbackLog callbackLog = buildPayCallbackLog(request, rawBody);
            payCallbackLogMapper.insert(callbackLog);

            if (StringUtils.hasText(request.getMerchantOrderNo())) {
                pushCount = payCallbackLogMapper.countByMerchantOrderNo(
                        request.getMerchantOrderNo(), CALLBACK_TYPE_PAY);
            }

            if (!StringUtils.hasText(request.getMerchantOrderNo())) {
                log.error("支付结果回调缺少 merchantOrderNo，无法定位订单，MUST 人工核对, 支付中心orderNo={}, channelOrderNo={}, status={}",
                        request.getOrderNo(), request.getChannelOrderNo(), request.getStatus());
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "merchantOrderNo不能为空");
                return response;
            }

            PayTxnDetail update = new PayTxnDetail();
            update.setOrderNo(request.getMerchantOrderNo());
            update.setPayStatus(convertPayStatus(request.getStatus()));
            update.setDebitRequestResult(resolveDebitRequestResult(update.getPayStatus()));
            update.setMerchantOrderNo(request.getMerchantOrderNo());
            update.setPayCenterOrderNo(request.getOrderNo());
            update.setChannelOrderNo(request.getChannelOrderNo());
            update.setTotalAmount(request.getTotalAmount());
            update.setCashAmount(request.getCashAmount());
            update.setCouponAmount(request.getCouponAmount());
            update.setPayUserId(request.getPayUserId());
            update.setPaymentVendor(request.getPaymentVendor());
            update.setPayTime(request.getPayTime());
            int affected = payTxnDetailMapper.updatePayCallback(update);
            if (affected == 0) {
                String currentPayStatus = queryPayStatus(request.getMerchantOrderNo());
                if (!update.getPayStatus().equals(currentPayStatus)) {
                    log.error("支付结果回调未命中任何订单，MUST 人工核对支付中心与本地口径, merchantOrderNo={}, 支付中心orderNo={}, channelOrderNo={}, status={}, payTime={}, 本地PAY_STATUS={}",
                            request.getMerchantOrderNo(), request.getOrderNo(), request.getChannelOrderNo(),
                            request.getStatus(), request.getPayTime(), currentPayStatus);
                    if (shouldStopRetry(pushCount)) {
                        giveUpRetry(response, request.getMerchantOrderNo(), pushCount, "支付订单不存在或状态不允许更新");
                        return response;
                    }
                    fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "支付订单不存在或状态不允许更新");
                    return response;
                }
                log.info("支付结果回调重复推送，本地已是目标状态，跳过回写继续收敛扣费订单, merchantOrderNo={}, payStatus={}, pushCount={}",
                        request.getMerchantOrderNo(), currentPayStatus, pushCount);
            }

            if (!syncGateTxnPayStatus(request.getMerchantOrderNo(), update.getPayStatus())) {
                if (shouldStopRetry(pushCount)) {
                    giveUpRetry(response, request.getMerchantOrderNo(), pushCount, "扣费订单状态同步失败");
                    return response;
                }
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "扣费订单状态同步失败，待支付中心重推");
                return response;
            }

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("处理支付结果回调异常, request={}", JSON.toJSONString(request), e);
            if (request != null && shouldStopRetry(pushCount)) {
                giveUpRetry(response, request.getMerchantOrderNo(), pushCount, "处理支付结果回调异常：" + e.getMessage());
                return response;
            }
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /** 读取 PAY_TXN_DETAIL 当前的 PAY_STATUS，仅用于「updatePayCallback 命中 0 行」时判别是重推还是真没落地。 */
    private String queryPayStatus(String orderNo) {
        try {
            PayTxnDetail existing = payTxnDetailMapper.selectByOrderNo(orderNo);
            return existing == null ? null : existing.getPayStatus();
        } catch (Exception e) {
            log.error("查询支付订单当前状态失败, orderNo={}", orderNo, e);
            return null;
        }
    }

    /** 是否已达重推上限、不再请求支付中心重推。 */
    private boolean shouldStopRetry(int pushCount) {
        return pushCount >= MAX_PAY_CALLBACK_PUSH;
    }

    /** 达到重推上限仍未处理成功：返回 0000 让支付中心停止重推，同时把这一笔标成需人工。 */
    private void giveUpRetry(PaySignCallbackResult response, String merchantOrderNo, int pushCount, String reason) {
        log.error("支付结果回调已推送{}次仍未处理成功，达到上限{}，放弃重推并返回0000，MUST 人工处理, merchantOrderNo={}, 原因={}",
                pushCount, MAX_PAY_CALLBACK_PUSH, merchantOrderNo, reason);
        try {
            payCallbackLogMapper.markManualByMerchantOrderNo(merchantOrderNo, CALLBACK_TYPE_PAY,
                    reason == null ? null : reason.substring(0, Math.min(reason.length(), 300)));
        } catch (Exception e) {
            log.error("标记回调需人工处理失败, merchantOrderNo={}", merchantOrderNo, e);
        }
        fillSuccess(response);
    }

    /**
     * {@code scene} 不在网关枚举内时只打 WARN、照常放行。
     *
     * <p>NEVER 改成拒绝：{@code gate-txn-pay-server} 的仓库默认值是非法的 {@code AGM_GATE}、
     * {@code fep-app-server} 在 {@code scene} 缺失时会把 {@code channelType} 透传进来，
     * 拒绝等于把在跑的免密扣款打挂。这里的 WARN 只用于让「上游送了网关不认的场景值」在日志里可检索。
     */
    private void warnIfSceneOutsideGatewayEnum(RequestPayReqDTO request) {
        String scene = request.getScene();
        if (!GATEWAY_PAY_SCENES.contains(scene)) {
            log.warn("REQUEST_PAY scene 不在支付中心网关枚举内（仅告警不拒绝）, orderNo={}, scene={}, 网关枚举={}",
                    request.getOrderNo(), scene, GATEWAY_PAY_SCENES);
        }
    }

    /**
     * 创建支付订单当前态记录。
     *
     * @param existingTxn 调用方已查到的现存记录，null 表示不存在、需要插入
     */
    private void ensurePayTxn(RequestPayReqDTO request, PayTxnDetail existingTxn) {
        if (existingTxn != null) {
            log.info("ensurePayTxn 订单已存在，跳过插入, orderNo={}", request.getOrderNo());
            return;
        }
        PayTxnDetail record = new PayTxnDetail();
        record.setOrderNo(request.getOrderNo());
        record.setPayType("PAY");
        record.setPayStatus("INIT");
        record.setThirdUserId(request.getThirdUserId());
        record.setCardId(request.getCardId());
        record.setCardType(request.getCardType());
        record.setPaymentVendor(request.getPaymentVendor());
        record.setRequestSignSeq(request.getRequestSignSeq());
        record.setPayUserId(request.getPayUserId());
        record.setAmount(request.getAmount());
        record.setRefundStatus("NONE");
        record.setRefundAmount(0);
        record.setRequestCount(0);
        record.setTxnDate(resolveTxnDate(request.getTxnDate()));
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDiscountInfo(request.getDiscountInfo());
        record.setDiscountFee(request.getDiscountFee());
        try {
            payTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!PaySignDuplicateKey.isConflict(e)) { throw e; }
            log.warn("ensurePayTxn 并发插入重复，orderNo={}, msg={}", request.getOrderNo(), e.getMessage());
        }
    }

    /**
     * 登记一条「已完成、不经支付中心」的支付流水。
     *
     * <p><b>与 {@link #requestPay} 的唯一区别、也是本方法存在的全部理由：不出网。</b>
     * {@code requestPay} 在建行后无条件 {@code markRequesting} + 调支付中心，
     * 拿它来处理「BOM 现场已收款」的单子等于对乘客重复收费（ADR-D136）。
     *
     * <p>落库口径（**NEVER 改**，改一处就要同步看齐 {@code RegisterCompletedPayTxnReqDTO} 的类注释）：
     * {@code PAY_STATUS='SUCCESS'} + {@code DEBIT_REQUEST_RESULT='SUCCESS'}（与 {@code GATE_TXN_PAY.DEBIT_STATUS} 同源）、
     * {@code AMOUNT} = ITP 实收（BOM 代收单为 0）、{@code MERCHANT_ORDER_NO} = 我方订单号、
     * {@code PAY_CENTER_ORDER_NO} / {@code CHANNEL_ORDER_NO} **留空**（确实没有渠道流水，
     * 编不出来的号 NEVER 造假，退款侧 {@code PayRefundRules} 正靠它为空来拦这类单）。
     *
     * <p>幂等：先查后插 + {@code UK_PAY_TXN_DETAIL_ORDER} 唯一键兜底（cause 链判定，见 AGENTS.md §5.2），
     * 重复登记一律返成功，调用方可安全重试。
     */
    @Override
    public BaseRespDTO registerCompletedTxn(RegisterCompletedPayTxnReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        String validMsg = validateRegisterCompletedTxn(request);
        if (validMsg != null) {
            response.setRetCode(PaySignErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(validMsg);
            log.warn("REGISTER_COMPLETED_TXN 参数校验失败, request={}, msg={}", request, validMsg);
            return response;
        }
        PayTxnDetail existing = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
        if (existing != null) {
            response.setRetCode("0000");
            response.setRetMsg("成功");
            log.info("REGISTER_COMPLETED_TXN 流水已存在，按幂等返回成功, orderNo={}, payStatus={}",
                    request.getOrderNo(), existing.getPayStatus());
            return response;
        }
        LocalDateTime now = LocalDateTime.now();
        PayTxnDetail record = new PayTxnDetail();
        record.setOrderNo(request.getOrderNo());
        record.setPayType("PAY");
        record.setPayStatus("SUCCESS");
        record.setDebitRequestResult("SUCCESS");
        record.setThirdUserId(request.getThirdUserId());
        record.setCardId(request.getCardId());
        record.setCardType(request.getCardType());
        record.setPaymentVendor(request.getPaymentVendor());
        record.setRequestSignSeq(request.getRequestSignSeq());
        record.setPayUserId(request.getPayUserId());
        record.setAmount(request.getAmount());
        record.setTotalAmount(request.getAmount());
        record.setMerchantOrderNo(request.getOrderNo());
        record.setRefundStatus("NONE");
        record.setRefundAmount(0);
        record.setRequestCount(0);
        record.setPayTime(DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now));
        record.setResponseTime(now);
        record.setTxnDate(resolveTxnDate(request.getTxnDate()));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            payTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!PaySignDuplicateKey.isConflict(e)) { throw e; }
            log.warn("REGISTER_COMPLETED_TXN 并发插入重复，按幂等返回成功, orderNo={}, msg={}",
                    request.getOrderNo(), e.getMessage());
        }
        response.setRetCode("0000");
        response.setRetMsg("成功");
        log.info("REGISTER_COMPLETED_TXN 已登记不经支付中心的支付流水, orderNo={}, amount={}, txnDate={}, reason={}",
                request.getOrderNo(), request.getAmount(), record.getTxnDate(), request.getReason());
        return response;
    }

    /**
     * 校验「已完成流水」登记入参。
     *
     * <p>**NEVER 复用 {@code validateRequestPay}**：那套要求 {@code scene} / {@code subject} / {@code body}
     * 等只对支付中心有意义的字段，对「现场已收款」的单子没有语义。
     *
     * <p>{@code amount} 允许为 0（BOM 代收单的常态），但 **NEVER 允许负数**；
     * {@code txnDate} 不强制（缺失时按当天补），但 {@code orderNo} 缺了就没有幂等键、一律拒。
     */
    private String validateRegisterCompletedTxn(RegisterCompletedPayTxnReqDTO request) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (request.getAmount() == null) {
            return "amount不能为空";
        }
        if (request.getAmount() < 0) {
            return "amount不能为负数: " + request.getAmount();
        }
        return null;
    }

    /** 兜底逻辑：当 PAY_TXN_DETAIL 不存在且 request 中签约信息缺失时。 */
    private void resolvePaySignInfoFromAccount(RequestPayReqDTO request) {
        if (!StringUtils.hasText(request.getCardId()) || !StringUtils.hasText(request.getThirdUserId())) {
            log.warn("resolvePaySignInfoFromAccount: cardId或thirdUserId为空，跳过, orderNo={}", request.getOrderNo());
            return;
        }
        try {
            switch (accountDomainPort.queryUser(
                    request.getThirdUserId(), request.getCardId(), request.getCardType())) {
                case AccountQuery.Found<AccountUserView> found -> {
                    applyAccountUserView(request, found.value());
                    log.info("resolvePaySignInfoFromAccount: 从account-server补充签约信息, orderNo={}, paymentVendor={}, requestSignSeq={}",
                            request.getOrderNo(), request.getPaymentVendor(), request.getRequestSignSeq());
                }
                case AccountQuery.NotFound<AccountUserView> notFound ->
                        log.warn("resolvePaySignInfoFromAccount: account-server 未答成功，不采信本次应答, orderNo={}, cardId={}, retCode={}, retMsg={}",
                                request.getOrderNo(), request.getCardId(), notFound.retCode(), notFound.retMsg());
                case AccountQuery.Unreachable<AccountUserView> unreachable ->
                        log.error("resolvePaySignInfoFromAccount: 未获 account-server 业务答复, orderNo={}",
                                request.getOrderNo(), unreachable.cause());
            }
        } catch (Exception e) {
            log.error("resolvePaySignInfoFromAccount: 补签约信息异常, orderNo={}", request.getOrderNo(), e);
        }
    }

    /** 把账户域窄视图落到支付入参上。 */
    private void applyAccountUserView(RequestPayReqDTO request, AccountUserView view) {
        if (view.channel() != null) {
            request.setPaymentVendor(view.channel());
        } else {
            log.warn("resolvePaySignInfoFromAccount: account-server未返回channel, cardId={}", request.getCardId());
        }
        boolean wallet = isWallet(request.getPaymentVendor());
        if (wallet) {
            if (view.thirdPayId() != null) {
                request.setPayUserId(view.thirdPayId());
            } else {
                log.warn("resolvePaySignInfoFromAccount: 钱包用户未返回thirdPayId, cardId={}", request.getCardId());
            }
        }
        if (view.reqContractNo() != null) {
            request.setRequestSignSeq(view.reqContractNo());
        } else {
            log.warn("resolvePaySignInfoFromAccount: account-server未返回reqContractNo, cardId={}, wallet={}",
                    request.getCardId(), wallet);
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
        record.setPayCenterOrderNo(response.getMerchantOrderNo());
        record.setChannelOrderNo(response.getChannelOrderNo());
        record.setDebitRequestResult(resolveDebitRequestResult(payStatus));
        record.setResponseTime(LocalDateTime.now());
        record.setTransIn(transIn);
        payTxnDetailMapper.updateRequestResult(record);
    }

    /**
     * 通知 gate-txn-pay-server 把 GATE_TXN_PAY.DEBIT_STATUS 收敛到终态。
     *
     * @return true 表示远端已确认收敛（含幂等命中），false 表示需要支付中心重推
     */
    private boolean syncGateTxnPayStatus(String orderNo, String payStatus) {
        return switch (debitSyncPort.syncDebitStatus(orderNo, payStatus, "支付结果回调")) {
            case RpcOutcome.Ok ignored -> {
                log.info("扣费订单状态同步成功, orderNo={}, payStatus={}", orderNo, payStatus);
                yield true;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("扣费订单状态同步被闸机域拒绝，待支付中心重推, orderNo={}, payStatus={}, retCode={}, retMsg={}",
                        orderNo, payStatus, rejected.retCode(), rejected.retMsg());
                yield false;
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("扣费订单状态同步未获答复，待支付中心重推, orderNo={}, payStatus={}",
                        orderNo, payStatus, unreachable.cause());
                yield false;
            }
        };
    }

    /** 回填应答里来自网关的四个字段。 */
    private void fillGatewayFields(RequestPayResult response, PaymentReply reply) {
        PaySignGatewayResponse gatewayResponse = reply == null ? null : reply.raw();
        if (gatewayResponse == null) {
            return;
        }
        response.setCode(gatewayResponse.getCode());
        response.setMsg(gatewayResponse.getMsg());
        response.setSuccess(reply.successful());
        response.setData(gatewayResponse.getData());
    }

    /** 支付失败且为支付宝渠道时，添加黑名单。 */
    private void addBlacklistForPaymentFailure(RequestPayReqDTO request, PaySignGatewayResponse gatewayResponse) {
        try {
            String confirmedStatus = queryGatewayPayStatus(request.getOrderNo());
            if (!"FAIL".equals(confirmedStatus)) {
                log.warn("支付调用失败但支付中心未确认为失败，跳过拉黑, orderNo={}, cardId={}, paymentVendor={}, 支付中心状态={}, 本次网关code={}, msg={}",
                        request.getOrderNo(), request.getCardId(), request.getPaymentVendor(), confirmedStatus,
                        gatewayResponse != null ? gatewayResponse.getCode() : "null",
                        gatewayResponse != null ? gatewayResponse.getMsg() : "null");
                return;
            }

            AddBlackListReqDTO blacklistRequest = new AddBlackListReqDTO();
            blacklistRequest.setCardId(request.getCardId());
            blacklistRequest.setCardType(request.getCardType());
            blacklistRequest.setThirdUserId(request.getThirdUserId());
            // 渠道填 99 未知：本链路只知道支付通道（paymentVendor），拿不到卡的业务渠道，
            // NEVER 拿 paymentVendor 当 channelCode —— 那是支付通道、不是渠道。
            blacklistRequest.setChannelCode("99");
            blacklistRequest.setBlackSource("01");
            blacklistRequest.setBlackCause("01");
            blacklistRequest.setBizNo(request.getOrderNo());
            blacklistRequest.setCreateBy("pay-sign-server");

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

    /** 用支付中心只读接口 §1.2 payQuery 查这笔的真实状态，返回归一化后的状态。 */
    private String queryGatewayPayStatus(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            GatewayReply reply = paymentGatewayPort.queryPayStatus(orderNo);
            if (!(reply instanceof GatewayReply.Accepted accepted) || accepted.data() == null) {
                return null;
            }
            String status = stringValue(accepted.data().get("status"), null);
            if (!StringUtils.hasText(status)) {
                return null;
            }
            return convertPayStatus(status);
        } catch (Exception e) {
            log.error("查询支付中心支付状态异常，按不拉黑处理, orderNo={}", orderNo, e);
            return null;
        }
    }
}
