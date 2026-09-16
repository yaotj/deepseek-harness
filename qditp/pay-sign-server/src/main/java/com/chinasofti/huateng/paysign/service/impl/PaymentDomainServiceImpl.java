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
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PayCallbackLogMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.service.PaymentDomainService;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.PaymentGatewayPort;
import com.chinasofti.huateng.paysign.port.PaymentReply;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import com.chinasofti.huateng.paysign.port.AccountUserView;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付领域服务：免密扣款（支付 API 1.1）与支付结果回调（5.1）的真实现。
 *
 * <p><b>退款（3.1 发起 + 两套补偿）已于 2026-09-15 拆出到 {@link RefundDomainServiceImpl}</b>
 * （纯搬迁；拆分前本类 1166 行、同时管四件事）。随之移出的还有 {@code PAY_REFUND_DETAIL} 的
 * mapper 与 {@code validateRefundPayTxn} / {@code buildPayRefundDetail} / {@code buildRefundOrderNo}
 * 等退款私有辅助方法。<b>NEVER 在本类里加回退款逻辑。</b></p>
 *
 * <p>2026-09-15 由 {@code PaySignWorkflow} **纯搬迁**而来（god class 按业务组拆分的第一组），
 * 方法体、日志措辞、判断顺序、事务注解与注释全部逐字保留，<b>NEVER 在搬迁批次里顺手改逻辑</b>。
 * 搬迁前后的等价性由 {@code PaySignWorkflowFixture} 经 {@code PaySignServiceImpl} 门面下钻的
 * 特征测试守着。</p>
 *
 * <p><b>字段注入而非构造器注入是刻意的</b>：与 {@code PaySignWorkflow} 保持同一种装配风格，
 * 测试夹具靠 {@code ReflectionTestUtils} 按字段名注入 mock，改成构造器注入会让夹具整批失效。</p>
 *
 * <p><b>网关调用统一走 {@link PaySignGateway}</b>：搬迁前本组在 {@code PaySignWorkflow} 里
 * 经四个私有薄壳（{@code requestGatewaySimple} / {@code isGatewaySuccess} /
 * {@code isAlreadyPaidSuccess} / {@code gatewayErrorMsg}）转调 {@code PayGatewayClient}，
 * 搬过来时**不再复制那四层壳**，改为直接调本 Bean 的同义方法。<b>NEVER 在本类里重建
 * {@code isAlreadyPaidSuccess} 的私有副本</b> —— 它是对支付中心应答措辞的硬编码约定，
 * 散成多份后改一处漏一处不会有编译错误，只会让已扣款成功的交易被判成失败并走到拉黑分支。</p>
 */
@Service
public class PaymentDomainServiceImpl implements PaymentDomainService {
    private static final Logger log = LoggerFactory.getLogger(PaymentDomainServiceImpl.class);

    /*
     * 钱包渠道号常量已收口到 support/PaymentChannels，本类只 import static isWallet(...)。
     * 收口前本模块有 5 份同形常量副本、7 处判断、3 种归一化时机，新增渠道要改 7 处，
     * 漏一处既不编译失败也不告警，只会让钱包用户在其中一条链路上被当成普通签约渠道。
     * NEVER 在本类重新声明该常量，也 NEVER 直接写 PaymentVendorEnum.WALLET.getCode() 或字面量。
     */

    private final PaySignProperties paySignProperties;
    private final PayTxnDetailMapper payTxnDetailMapper;
    private final PayCallbackLogMapper payCallbackLogMapper;
    /**
     * 账户域出向调用的唯一出口（ADR-D94 续）。
     *
     * <p>{@code resolvePaySignInfoFromAccount} 补签约信息用。<b>本类已不再注入
     * {@code AccountClient}</b>，NEVER 加回来 —— 新增账户域调用一律加到端口上。</p>
     */
    private final AccountDomainPort accountDomainPort;
    private final BlacklistClient blacklistClient;
    private final GateTxnPayClient gateTxnPayClient;
    /** 支付中心网关的调用与应答判读收口点。 */
    /**
     * 支付中心**扣款方向**出向调用的唯一出口（2026-09-16，ADR-D113 续）。
     *
     * <p>本类此前直接注 {@code PaySignGateway}，于是「取哪个 URL、组哪份 bizData、
     * 怎么判成功（含 {@code isAlreadyPaidSuccess} 这条措辞约定）」散在两个出向点上。
     * <b>NEVER 把 {@code PaySignGateway} 加回本类。</b>
     *
     * <p>{@code paySignProperties} 仍留着：本类还有一个与出网无关的 {@code getTestForceAmount}。</p>
     */
    private final PaymentGatewayPort paymentGatewayPort;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public PaymentDomainServiceImpl(
            PaySignProperties paySignProperties,
            PayTxnDetailMapper payTxnDetailMapper,
            PayCallbackLogMapper payCallbackLogMapper,
            AccountDomainPort accountDomainPort,
            BlacklistClient blacklistClient,
            GateTxnPayClient gateTxnPayClient,
            PaymentGatewayPort paymentGatewayPort) {
        this.paySignProperties = paySignProperties;
        this.payTxnDetailMapper = payTxnDetailMapper;
        this.payCallbackLogMapper = payCallbackLogMapper;
        this.accountDomainPort = accountDomainPort;
        this.blacklistClient = blacklistClient;
        this.gateTxnPayClient = gateTxnPayClient;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    /**
     * 支付 API 1.1 请求支付。
     * 内部调用方只传业务参数，支付网关公共参数和签名在 pay-sign-server 内统一完成。
     */
    /*
     * 本方法 NEVER 加 @Transactional，理由有两条，第二条比锁竞争严重得多。
     *
     * 一、锁跨网络。方法体是「ensurePayTxn + markRequesting 写 PAY_TXN_DETAIL 单行（拿排他锁）
     *    → 调支付中心（PayGatewayClient connect/read/write 各 30s）→ updatePayRequestResult 回写同一行」。
     *    包在事务里，这一行的锁就持满整个 30s 网络往返。上游 gate-txn-pay-server 对同一 orderNo 的
     *    重试（GateTxnPayServiceImpl:409 retryPay / 出站扣费两条路径）会串行堆在同一行上，
     *    与支付结果回调 receivePayResult 争的还是同一张表同一行——2026-08-26 事故就是回调侧这个形态。
     *
     * 二、「我要去扣款了」这个标记直到支付中心返回后才提交。这是资损级缺陷：
     *    事务里 ensurePayTxn 建的 PAY_TXN_DETAIL 记录和 markRequesting 的 REQUEST_COUNT+1 都处于未提交状态，
     *    一旦此刻 Pod 被杀、或等待超过 Druid remove-abandoned-timeout=60 导致连接被强杀、commit 抛
     *    connection closed，整个事务被丢弃 —— 而支付中心那边请求已经发出去、可能已经扣款成功。
     *    结果是「对方已扣款，我方连订单记录都没有」，事后既查不到也对不上账。
     *    catch 块里那段「回退为 RETRY」在这种场景下同样失效：连接已关闭，selectByOrderNo 和
     *    updatePayRequestResult 一起失败，只剩下 :626 那句「订单需人工补偿」的日志。
     *
     * 去掉事务后每条 SQL 自动提交，形成三段：
     *   ① ensurePayTxn + markRequesting 立即落库并提交（无论后面发生什么，请求意图都留下了）
     *   ② 无锁、无事务地调支付中心
     *   ③ updatePayRequestResult 回写 SUCCESS / PROCESSING / RETRY
     * 这个顺序正是资金安全要求的：先留痕、再出网、后回写。
     *
     * 代价是 ① 内部 insert 与 update 不再原子——insert 成功而 markRequesting 失败时，记录在库但
     * 状态未推进。这远好于整段丢弃：订单号可查、可靠回调或 queryResult 收敛，NEVER 用「保持原子」
     * 换「可能整段丢失」。
     */
    @Override
    public RequestPayResult requestPay(RequestPayReqDTO request) {
        RequestPayResult response = new RequestPayResult();
        try {
            // 重试路径：request 中 paymentVendor/requestSignSeq 可能为 null，从 PAY_TXN_DETAIL 补充
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
                    // PAY_TXN_DETAIL 不存在时，从 account-server 查询签约信息补齐 request
                    // 只补字段不建记录，PAY_TXN_DETAIL 统一由下面的 ensurePayTxn 创建
                    resolvePaySignInfoFromAccount(request);
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

            ensurePayTxn(request, existingTxn);
            payTxnDetailMapper.markRequesting(request.getOrderNo());
            log.info("REQUEST_PAY ensurePayTxn完成, orderNo={}, paymentVendor={}, discountFee={}, discountInfo={}",
                    request.getOrderNo(), request.getPaymentVendor(), request.getDiscountFee(), request.getDiscountInfo());

            // URL、bizData 装配、notifyUrl 回落与成功码判读都在 PaymentGatewayAdapter 内（ADR-D113 续）。
            PaymentReply reply = paymentGatewayPort.requestPay(request);
            PaySignGatewayResponse gatewayResponse = reply.raw();

            // 幂等成功 MUST 先判：它在收口前是嵌在「非成功码」里的第二层判断，语义等价。
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

                // 支付宝渠道支付失败，添加黑名单
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
     * 支付 API 5.1 支付回调。
     *
     * <p>fep-app 负责接收支付平台回调并解析 bizData，pay-sign-server 负责保存回调流水
     * 并回写 PAY_TXN_DETAIL 当前状态。</p>
     */
    /*
     * 本方法 NEVER 加 @Transactional。
     *
     * 原因：方法尾部要同步 push gate-txn-pay（syncGateTxnPayStatus，一次出网 HTTP）。
     * 一旦包在事务里，UPDATE PAY_TXN_DETAIL 拿到的行级排他锁会一直持到 RPC 返回并提交，
     * 支付中心对同一笔的重推就会全部堆在同一行上串行等待；等待超过 Druid
     * remove-abandoned-timeout=60（sql.properties:44）后连接被强杀，提交抛
     * TransactionSystemException: JDBC commit failed / connection closed，整个事务丢弃，
     * 响应退化成全局异常处理器的 UUID retCode，支付中心继续重推 —— 自我放大，没有出口。
     *
     * 已发生事故（2026-08-26 21:07~21:14 生产）：订单 GT20260826210647653586419 循环重推 8 分钟，
     * 单次请求耗时 287233ms，UPDATE 实测等锁 44997ms，Oracle V$SESSION 持续
     * enq: TX - row lock contention。循环期间每一轮事务都被强杀回滚，连 PAY_CALLBACK_LOG
     * 都没留下（生产核对：该订单 PAY_CALLBACK_LOG 只有 21:21 之后的 2 条，21:07~21:14 零条），
     * 所谓「回滚不会丢证据」在事务内并不成立——这也是本方法必须无事务的第二个理由。
     * 数据最终在 21:21 收敛（PAY_TXN_DETAIL 5111 与 GATE_TXN_PAY 5119 均为 SUCCESS）。
     *
     * 去掉事务后每条 SQL 自动提交，行锁只持有语句执行期间（毫秒级），RPC 在无锁状态下发起。
     * 这里也不需要原子性：PAY_CALLBACK_LOG 是支付中心结果的唯一凭据，本就要求即使后续
     * 失败也 MUST 保留（见下方注释），回滚反而会丢证据。
     *
     * 重推策略：支付中心侧「未收到成功响应就重试」是其规格（docs/external/支付中心网关接口文档.md §5
     * 回调处理要求），我方无法关闭，只能靠返回码控制。本方法据用户 2026-08-26 决定实施
     * **硬限次**：同一 merchantOrderNo 的 PAY 回调累计推送达 MAX_PAY_CALLBACK_PUSH 次后，
     * 无论处理成功与否一律回 0000 让上游停推，并把该条回调标成 MANUAL 等人工。
     * 这是「不再自我放大」换「不再自动重试」的取舍，NEVER 在放弃重推时不留痕。
     */
    private static final String CALLBACK_TYPE_PAY = "PAY";

    /**
     * 支付结果回调允许支付中心推送的总次数（首推 1 次 + 重推 1 次）。
     *
     * <p>达到该次数后，无论本次是否处理成功都返回 0000，让支付中心停止重推。
     * 代价是「真实失败」不再被上游重试，因此放弃重推时 MUST 打 ERROR 并把
     * PAY_CALLBACK_LOG 的 HANDLE_STATUS 置为 MANUAL，靠人工兜。</p>
     */
    private static final int MAX_PAY_CALLBACK_PUSH = 2;

    @Override
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

            // 硬限次的计数点 MUST 在 insert 之后：本方法无事务，insert 已提交，
            // 这个 COUNT 就是支付中心实际推送次数（含本次）。
            if (StringUtils.hasText(request.getMerchantOrderNo())) {
                pushCount = payCallbackLogMapper.countByMerchantOrderNo(
                        request.getMerchantOrderNo(), CALLBACK_TYPE_PAY);
            }

            // 定位键 MUST 用 merchantOrderNo：回调报文里 orderNo 是**支付中心**的订单号
            // （形如 286268899339436032），而 PAY_TXN_DETAIL.ORDER_NO 存的是我方商户订单号
            // （形如 GT20260826192157073586419），回调把它放在 merchantOrderNo 字段。
            // NEVER 用 request.getOrderNo() 当 WHERE 键，也 NEVER 拿它做兜底——那是一个
            // 必然匹配 0 行的键，只会把「键传错」和「订单不存在」混成同一种现象。
            // 已发生事故：2026-08-26 免密扣款支付宝已扣款成功，回调 UPDATE 命中 0 行，
            // PAY_STATUS 长期停在 PROCESSING，APP 显示扣费失败；生产库 4 笔受害
            // （PAY_TXN_DETAIL 5103/5105/5107/5109）。回调报文已落 PAY_CALLBACK_LOG，
            // 此处直接回绝不会丢证据，且非 0000 会让支付中心重推。
            if (!StringUtils.hasText(request.getMerchantOrderNo())) {
                log.error("支付结果回调缺少 merchantOrderNo，无法定位订单，MUST 人工核对, 支付中心orderNo={}, channelOrderNo={}, status={}",
                        request.getOrderNo(), request.getChannelOrderNo(), request.getStatus());
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "merchantOrderNo不能为空");
                return response;
            }

            PayTxnDetail update = new PayTxnDetail();
            update.setOrderNo(request.getMerchantOrderNo());
            update.setPayStatus(convertPayStatus(request.getStatus()));
            // DEBIT_REQUEST_RESULT MUST 与 PAY_STATUS 同步回写：APP 侧（IF8A-05 列表、IF8A-34 详情）
            // 展示的扣费结果读的是这一列，不是 PAY_STATUS——ticket-server 的 TransRecordAssembler
            // 只搬 debitRequestResult，PAY_STATUS 与 GATE_TXN_PAY.DEBIT_STATUS 都被丢弃。
            // 已发生事故：2026-08-26 回调把 PAY_STATUS 改成 SUCCESS 后，这一列仍停在
            // 同步响应阶段 updateRequestResult 写下的 PROCESSING，APP 一直显示扣费未成功。
            update.setDebitRequestResult(resolveDebitRequestResult(update.getPayStatus()));
            update.setMerchantOrderNo(request.getMerchantOrderNo());
            // 回调报文的 orderNo 是支付中心侧的支付订单号（形如 286275496309587968），
            // 落到独立列。NEVER 塞进 MERCHANT_ORDER_NO——那一列是我方商户订单号。
            update.setPayCenterOrderNo(request.getOrderNo());
            update.setChannelOrderNo(request.getChannelOrderNo());
            update.setTotalAmount(request.getTotalAmount());
            update.setCashAmount(request.getCashAmount());
            update.setCouponAmount(request.getCouponAmount());
            update.setPayUserId(request.getPayUserId());
            update.setPaymentVendor(request.getPaymentVendor());
            update.setPayTime(request.getPayTime());
            // 所有会被稀疏报文覆盖的列在 SQL 里都套了 NVL，重推缺字段不会把首推写好的值抹成 NULL。
            // WHERE 带列级状态白名单（INIT / PROCESSING / RETRY / FAIL），SUCCESS 是终态不在白名单内，
            // 因此重复的 SUCCESS 回调必然命中 0 行——这正是幂等出口，MUST 与「真的没落地」区分开。
            //
            // 影响 0 行有两种完全不同的含义：
            //   ① 当前状态已经是本次要写的状态 ⇒ 本次是重推，落库无需再动，但 MUST 继续往下走
            //      syncGateTxnPayStatus。首推若在「本地已 SUCCESS、远端同步失败」处返回非 0000，
            //      整条重试链路就靠这次重推来重新收敛 GATE_TXN_PAY；在这里 return 等于把
            //      唯一的重试机会掐掉，且 return 非 0000 会让支付中心继续推 —— 亲手造出新的死循环。
            //   ② 其余情况（订单不存在 / 定位键不对 / 被终态或未知状态拦住）⇒ 回调真的没落地，
            //      NEVER 回 0000，静默吞掉等于放弃这笔的最后一次纠错机会，只能等人工发现。
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

            // 本地落地成功后，同步收敛 GATE_TXN_PAY.DEBIT_STATUS。
            // 为什么必须在这里做：gate-txn-pay-server 调 pay-sign 后只会把订单推进到
            // PROCESSING / RETRY，扣款成功与否只有支付中心的回调知道，而回调只发到本服务；
            // 不在这里通知，GATE_TXN_PAY 就永远停在中间态，解约前置校验 countFailedOrder
            // 会把已扣款成功的订单也算成欠费、一直拦住解约（2026-08-26 生产实测）。
            //
            // 失败处理：NEVER 抛异常回滚——本地状态与 PAY_CALLBACK_LOG 是支付中心结果的
            // 唯一凭据，回滚等于丢证据。改为回非 0000 让支付中心重推，下一次重推会
            // 重新走一遍「幂等 UPDATE + 重新同步」，天然形成重试，不依赖扫表任务。
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

    /**
     * 读取 PAY_TXN_DETAIL 当前的 PAY_STATUS，仅用于「updatePayCallback 命中 0 行」时判别是重推还是真没落地。
     *
     * <p>订单不存在或查询异常都返回 null，交由调用方按「未收敛」处理——NEVER 在这里吞成
     * 「已收敛」，那会把真实丢单伪装成幂等命中。</p>
     */
    private String queryPayStatus(String orderNo) {
        try {
            PayTxnDetail existing = payTxnDetailMapper.selectByOrderNo(orderNo);
            return existing == null ? null : existing.getPayStatus();
        } catch (Exception e) {
            log.error("查询支付订单当前状态失败, orderNo={}", orderNo, e);
            return null;
        }
    }

    /**
     * 是否已达重推上限、不再请求支付中心重推。
     *
     * <p>pushCount 为 0 表示计数不可用（merchantOrderNo 为空或计数 SQL 失败），
     * 此时保持原行为（回非 0000 让上游重推），NEVER 因为拿不到计数就静默放行。</p>
     */
    private boolean shouldStopRetry(int pushCount) {
        return pushCount >= MAX_PAY_CALLBACK_PUSH;
    }

    /**
     * 达到重推上限仍未处理成功：返回 0000 让支付中心停止重推，同时把这一笔标成需人工。
     *
     * <p>这是一个**有意的取舍**——用「不再自动重试」换「不再自我放大」。因此这里
     * MUST 同时做两件事：打 ERROR 日志、把 PAY_CALLBACK_LOG.HANDLE_STATUS 置为 MANUAL。
     * NEVER 只回 0000 不留痕，那等于静默丢单。</p>
     */
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
     * 创建支付订单当前态记录。
     *
     * <p>同一个 orderNo 重复请求时视为幂等，不重复插入，只继续走请求支付和次数累加。</p>
     *
     * <p>存在性由调用方传入：{@code requestPay} 入口已经按 orderNo 查过一次
     * （orderNo 是 {@code validateRequestPay} 的必填项，走到这里必然查过），
     * 这里 **NEVER** 再查一遍。{@code existingTxn} 非空即视为已存在，直接返回。</p>
     *
     * <p>catch {@link DuplicateKeyException} 保留是因为「调用方查询」与 insert 之间
     * 仍有并发窗口，真并发才会走到，因此 warn 不带堆栈：能定位到 orderNo 就够，
     * 堆栈没有额外信息。原先靠抛异常兜底，每次幂等重放都会刷一段 ORA-00001 堆栈，
     * 把可疑的真并发和常见的重放混成同一种噪音。</p>
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
        } catch (DuplicateKeyException e) {
            // 撞 UK_PAY_TXN_DETAIL_ORDER (ORDER_NO, TXN_DATE)：:691 的 selectByOrderNo 到这里是
            // 「查后写」，并发重试下两条请求可能都看到 existingTxn==null 而都来插。此时记录已被
            // 兄弟请求建好，本请求继续走调用方的 markRequesting 即可。
            // NEVER 抛出：抛出会让上游 gate-txn-pay-server 的重试拿到 9999，而库里其实是好的。
            log.warn("ensurePayTxn 并发插入重复，orderNo={}, msg={}", request.getOrderNo(), e.getMessage());
        }
    }

    /**
     * 兜底逻辑：当 PAY_TXN_DETAIL 不存在且 request 中签约信息缺失时，
     * 从 account-server 查询 USER_ITP_REG_INFO 补齐 paymentVendor / requestSignSeq / payUserId，
     * 避免 retryPay 等场景因签约信息缺失走不下去。
     *
     * <p>本方法只补字段，NEVER 建 PAY_TXN_DETAIL。建记录统一由 requestPay 里的
     * {@code ensurePayTxn} 一处负责——原先这里也调一次，导致每笔交易插两次：
     * 第一次发生在测试金额覆盖（test-force-amount）之前，AMOUNT 落的是原始金额，
     * 与实际请求支付平台的金额不一致，且第二次必然撞唯一索引刷 ORA-00001 堆栈。</p>
     *
     * <p><b>账户域必须答 {@code retCode=0000} 才采信</b>（ADR-D94）：业务失败应答里残留的
     * {@code channel} / {@code reqContractNo} 曾被直接写进支付入参并真的发起免密扣款。
     * 不采信时**不抛异常、只是不补**，由 {@code validatePaySignInfo} 收成 {@code 8011}。</p>
     */
    private void resolvePaySignInfoFromAccount(RequestPayReqDTO request) {
        if (!StringUtils.hasText(request.getCardId()) || !StringUtils.hasText(request.getThirdUserId())) {
            log.warn("resolvePaySignInfoFromAccount: cardId或thirdUserId为空，跳过, orderNo={}", request.getOrderNo());
            return;
        }
        try {
            // 2026-09-16 起走 AccountDomainPort（ADR-D94 续）。「必须答成功才采信」这条判定
            // 已上移到端口 —— 拿到 Found 就意味着账户域答了 0000，本方法不再自己判 retCode。
            // NotFound 与 Unreachable 在这里**处置相同**（都不补），但日志级别刻意不同：
            // 前者是账户域的正常业务答复，后者是故障、要能被告警捞到。
            // NEVER 把两支合并成 default —— 合并后新增分支不会编译失败，就退回了靠人记规则。
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
            // 端口契约是「NEVER 抛异常」，这层只兜住装配阶段的意外。
            // MUST 保留：本方法只负责补字段，任何异常都 NEVER 放大成 requestPay 不可用 ——
            // 补不到时下游 validatePaySignInfo 会收成 8011，那是设计好的出口。
            log.error("resolvePaySignInfoFromAccount: 补签约信息异常, orderNo={}", request.getOrderNo(), e);
        }
    }

    /**
     * 把账户域窄视图落到支付入参上。
     *
     * <p><b>钱包（{@code 0B}）自 2026-09-15 起与非钱包一样必须带 {@code requestSignSeq}</b>，
     * 因此这里对钱包**同时**填 {@code payUserId}（= {@code thirdPayId}）与
     * {@code requestSignSeq}（= {@code reqContractNo}），非钱包仍只填 {@code requestSignSeq}。</p>
     *
     * <p>原实现是「互斥两支：钱包只填 payUserId」，注释写着 NEVER 两个都填，理由是
     * {@code validatePaySignInfo} 对两类必填项不同、都填会掩盖「该用户其实没签约」。
     * 那条理由在支付中心的真实契约下站不住：§1.1 requestPay 的 withholding 场景
     * **强制要求 requestSignSeq**（2026-09-15 实测，只送 payUserId 时网关返
     * {@code code=9999「代扣签约请求流水号不能为空」}），钱包因此永远扣不出去。
     * 「掩盖未签约」这个担忧改由 {@code validatePaySignInfo} 对钱包**两个字段都校验**来兜。</p>
     *
     * <p>视图里的字段已由适配器 trim 并把空白串收成 {@code null}，因此这里只判 {@code null}。</p>
     */
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
        // response.orderNo 是我方商户订单号（requestPay 应答里 data.merchantOrderNo），
        // response.merchantOrderNo 才是支付中心自己的支付订单号（data.orderNo）——命名反了但口径固定。
        // 支付中心订单号 MUST 单独落库：退款报文 §3.1 的「原支付订单号」只认它。
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
        GateTxnPaySyncStatusReqDTO syncRequest = new GateTxnPaySyncStatusReqDTO();
        syncRequest.setOrderNo(orderNo);
        syncRequest.setPayStatus(payStatus);
        syncRequest.setRemark("支付结果回调");
        try {
            GateTxnPayRespDTO syncResponse = gateTxnPayClient.syncDebitStatus(syncRequest);
            // MUST 显式检查 retCode：本项目的 RPC 包装方法不抛异常，
            // 「没抛异常」不等于远端已收敛（AGENTS.md §5.2 已记录过同类事故）。
            if (syncResponse == null || !"0000".equals(syncResponse.getRetCode())) {
                log.error("扣费订单状态同步失败，待支付中心重推, orderNo={}, payStatus={}, 返回={}",
                        orderNo, payStatus, syncResponse);
                return false;
            }
            log.info("扣费订单状态同步成功, orderNo={}, payStatus={}", orderNo, payStatus);
            return true;
        } catch (Exception e) {
            log.error("扣费订单状态同步异常，待支付中心重推, orderNo={}, payStatus={}", orderNo, payStatus, e);
            return false;
        }
    }

    /**
     * 回填应答里来自网关的四个字段。
     *
     * <p>入参由 {@code PaySignGatewayResponse} 换成 {@link PaymentReply}（2026-09-16，ADR-D113 续）：
     * {@code success} 此前调 {@code paySignGateway.isSuccess}，而判读已收进端口 ——
     * 用 {@link PaymentReply#successful()} 取，口径与收口前一致（{@code AlreadyPaid} 也算成功，
     * 因为收口前它走的正是 {@code fillSuccess} 那一支）。</p>
     */
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

    /**
     * 支付失败且为支付宝渠道时，添加黑名单。
     *
     * <p>入参 {@code gatewayResponse} 只代表**本次 HTTP 调用**没拿到成功应答，不等于「这笔钱没扣成」。
     * 网络超时、连接被 Druid 强杀、虚拟线程被 pin 住导致响应迟到，都会让一笔**已经在支付中心扣款成功**
     * 的交易在本端表现为失败。已发生事故：2026-08-26 21:07 订单
     * {@code GT20260826210647653586419} 在支付中心侧支付成功，本端因事务被丢弃判成扣费失败，
     * 测试卡 {@code 0178606904586419} 被以 {@code REASON=操作失败} 拉进黑名单，乘客直接过不了闸。</p>
     *
     * <p>因此拉黑 MUST 先用只读接口 §1.2 payQuery 向支付中心确认这笔的真实状态，**只有支付中心明确回
     * FAIL 才拉黑**。查不到、查询失败、状态是 SUCCESS 或任何中间态，一律不拉黑——
     * 误拉黑的代价（乘客被闸机拒绝）远高于漏拉黑（下次扣款再失败会再走到这里）。
     * NEVER 把「查不到就当失败」写成兜底。</p>
     */
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
     * 用支付中心只读接口 §1.2 payQuery 查这笔的真实状态，返回归一化后的状态
     * （{@code SUCCESS} / {@code FAIL} / 其它中间态原文），无法判定时返回 {@code null}。
     *
     * <p>bizData 传 {@code merchantOrderNo}：支付中心把我方订单号叫 merchantOrderNo
     * （支付回调、requestPay 应答都是这个口径，见 {@code receivePayResult} 用
     * {@code request.getMerchantOrderNo()} 定位本地订单），它自己的 orderNo 是另一个号。</p>
     *
     * <p>只用于「拉黑前二次确认」这一个判断，NEVER 拿它回写 PAY_TXN_DETAIL——
     * 状态回写归回调与 {@code updatePayCallback} 的状态机管，两处都写会出现两套口径。</p>
     */
    private String queryGatewayPayStatus(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            // URL 未配置时端口自己打 ERROR 并返 Rejected(null)，这里按「取不到状态」处理 —— 与收口前逐字同义。
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
