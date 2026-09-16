package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PayRefundRules.buildPayRefundDetail;
import static com.chinasofti.huateng.paysign.support.PayRefundRules.fillRefundResponseFields;
import static com.chinasofti.huateng.paysign.support.PayRefundRules.validateRefundPayTxn;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildRequestRefundBizData;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestRefund;
import static com.chinasofti.huateng.paysign.support.PaySignValues.convertPayStatus;
import static com.chinasofti.huateng.paysign.support.PaySignValues.defaultString;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.RefundGatewayPort;
import com.chinasofti.huateng.paysign.service.RefundDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 退款领域服务：退款发起（支付 API 3.1）与两套退款补偿的真实现。
 *
 * <p><b>2026-09-15 由 {@code PaymentDomainServiceImpl} 纯搬迁而来</b>。动机是那个类当时
 * <b>1166 行</b>、同时管四件事（支付发起、支付回调收口、退款发起、两套退款补偿）。方法体、
 * 日志措辞、判断顺序、常量取值与注释全部逐字保留，<b>NEVER 在搬迁批次里顺手改逻辑</b>。
 * 等价性由 {@code PaySignFacadeFixture} 经 {@code PaySignServiceImpl} 门面下钻的特征测试、
 * 以及 {@code PaymentRefundQueryCompensationTest} / {@code PaymentRefundSummaryCompensationTest}
 * 两组行为护栏守着（后两者的每一条断言期望值在本次搬迁中一字未改）。</p>
 *
 * <p><b>拆分动机不止「行数不好看」，当天有一次真实教训</b>：在 1166 行的原文件上做编辑时，
 * {@code old_string} 匹配落到了方法内部，误删了 {@code resolveRefundQueryStatus} 的方法体中段
 * （已当场恢复）。同一段文字在超长文件里更容易出现多处近似匹配，于是
 * <b>文件大小本身就是缺陷密度</b> —— 这是拆分的直接理由之一，记在这里以免后人把四件事再合回去。</p>
 *
 * <p><b>三个入口都不带 {@code @Transactional}，理由分别写在各自方法头，NEVER 加</b>：
 * {@code requestRefund} 是「留痕 → 出网 → 回写」（批次 5B 摘除，ADR-D8）；
 * {@code compensateRefundQuery} 逐行出网、被事务包住等于把刚修掉的形状原地复现；
 * {@code compensateRefundSummary} 每行都是单语句幂等 UPDATE、整批一个事务只会攒锁并连带回滚。</p>
 *
 * <p><b>退款账本的唯一真相是 {@code PAY_REFUND_DETAIL}</b>；{@code PAY_TXN_DETAIL} 的
 * {@code REFUND_AMOUNT} / {@code REFUND_STATUS} 是**派生汇总**，只能由
 * {@code PayTxnDetailMapper.updateRefundSummary} 按明细全量重算，<b>NEVER 累加式更新</b>
 * （该 SQL 因此可重复执行且不会重复累加，详见 {@code PayTxnDetailMapper.xml} 内注释）。</p>
 *
 * <p><b>两个补偿端点不可互相替代，NEVER 合并</b>：{@code compensateRefundQuery} <b>出网</b>
 * 调支付中心 §3.2 refundQuery，把停在 {@code PROCESSING} 的退款推到终态；
 * {@code compensateRefundSummary} <b>不出网</b>，只重算本地汇总，且其中 B 类
 * （明细已 {@code SUCCESS} 而原支付订单在 {@code PAY_TXN_DETAIL} 里根本不存在，
 * 2026-09-15 实测目标库有 <b>6 条</b>）<b>不可自愈、只记 WARN 等人工</b>。</p>
 *
 * <p><b>字段注入而非构造器注入是刻意的</b>：与 {@code PaymentDomainServiceImpl} /
 * {@code CallbackDomainServiceImpl} 保持同一种装配风格，测试夹具靠 {@code ReflectionTestUtils}
 * 按字段名注入 mock，改成构造器注入会让夹具与那两组护栏测试整批失效。</p>
 */
@Service
public class RefundDomainServiceImpl implements RefundDomainService {
    private static final Logger log = LoggerFactory.getLogger(RefundDomainServiceImpl.class);

    private final PayTxnDetailMapper payTxnDetailMapper;
    private final PayRefundDetailMapper payRefundDetailMapper;
    /**
     * 支付中心**退款方向**出向调用的唯一出口（2026-09-16，ADR-D113 续）。
     *
     * <p>本类此前同时注 {@code PaySignProperties} 与 {@code PaySignGateway}，而前者**只**为取
     * {@code requestRefundUrl} / {@code refundQueryUrl} 两个 URL 存在。两者一起换成本端口后，
     * 本类协作者 4 → 3。<b>NEVER 把那两个加回来。</b>
     */
    private final RefundGatewayPort refundGatewayPort;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public RefundDomainServiceImpl(
            PayTxnDetailMapper payTxnDetailMapper,
            PayRefundDetailMapper payRefundDetailMapper,
            RefundGatewayPort refundGatewayPort) {
        this.payTxnDetailMapper = payTxnDetailMapper;
        this.payRefundDetailMapper = payRefundDetailMapper;
        this.refundGatewayPort = refundGatewayPort;
    }

    /**
     * 支付 API 3.1 请求退款。
     *
     * <p>调用方只传 orderNo/refundAmount；pay-sign 根据原支付订单补齐 merchantOrderNo，
     * 生成 refundOrderNo，并负责退款明细入库和原支付订单退款汇总回写。</p>
     */
    /*
     * 本方法 NEVER 加回 @Transactional（批次 5B / 2026-09-15 摘除，ADR-D8 三件套同批完成）。
     *
     * 摘之前那个注解包住的是三条写 + 一次出网，顺序是：
     *   ① payRefundDetailMapper.insert          落 PAY_REFUND_DETAIL（REFUND_STATUS='INIT'）
     *   ② payRefundDetailMapper.markRequesting  REQUEST_COUNT+1 / 'PROCESSING' / LAST_REQUEST_TIME / REQUEST_BODY
     *   ③ paySignGateway.request(requestRefundUrl)   ← 出网，退款真的发给了支付中心
     *   ④ updateRefundRequestResult              回写 SUCCESS / RETRY
     *   ⑤ payTxnDetailMapper.updateRefundSummary 按明细重算原单已退总额
     *
     * 带着它比摘掉更危险：① 与 ② 在 ③ 出网时都还没提交。此刻 Pod 被杀、或等待超过 Druid
     * remove-abandoned-timeout（resource/micro/sql-datasource/src/main/resources/sql.properties:44）
     * 导致连接被强杀、commit 抛 connection closed，整个事务被丢弃 —— 而支付中心那边请求已经发出、
     * 可能已受理甚至已退款成功。结果不是「停在某个状态」，而是**本地连这一行都不存在**：
     * REFUND_ORDER_NO 与 REQUEST_BODY 都查不到，事后既无从对账也无从补偿。
     * 这与 requestPay 方法头逐字论证过的形状同型，那里的结论是「留痕 → 出网 → 回写」，
     * 末句即 NEVER 用「保持原子」换「可能整段丢失」。
     *
     * 摘掉后每条 SQL 自动提交，最坏停在 REFUND_STATUS='PROCESSING'
     * （REQUEST_COUNT=1、LAST_REQUEST_TIME 有值、REQUEST_BODY 完整、REFUND_NO / CHANNEL_REFUND_NO 为空），
     * 可查、可补偿：由本类 compensateRefundQuery() 拿支付中心 §3.2 refundQuery 回查收口，
     * 触发方是 web-admin 的 Quartz（POST /internal/payment/compensateRefundQuery）。
     *
     * 代价是 ④ 与 ⑤ 不再原子：④ 已把明细置 SUCCESS 而 ⑤ 的汇总没重算时，
     * PAY_TXN_DETAIL.REFUND_AMOUNT / REFUND_STATUS 会短暂落后于明细。这个中间态由 (2) 的回查兜住 ——
     * compensateRefundQuery 在把明细置为终态后**无条件再调一次 updateRefundSummary**，
     * 而该 SQL 是按 PAY_REFUND_DETAIL 全量重算的幂等语句（详见 PayTxnDetailMapper.xml 内注释），
     * 重复执行不会重复累加。NEVER 因为「这里已经算过一次」就把回查里那次省掉 ——
     * 省掉后 ④ 成功、⑤ 失败的那一笔永远不会有人补。
     */
    @Override
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
            GatewayReply reply = refundGatewayPort.requestRefund(bizData);
            PaySignGatewayResponse gatewayResponse = reply.raw();
            log.info("REQUEST_REFUND 支付平台返回, orderNo={}, refundOrderNo={}, gatewayResponse={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(gatewayResponse));

            response.setOrderNo(refundDetail.getOrderNo());
            response.setRefundOrderNo(refundDetail.getRefundOrderNo());
            fillGatewayFields(response, reply);

            if (reply instanceof GatewayReply.Rejected rejected) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, rejected.messageOr("请求退款接口失败"));
                fillGatewayFields(response, reply);
                updateRefundRequestResult(refundDetail, "RETRY", response, gatewayResponse);
                return response;
            }
            fillSuccess(response);
            fillRefundResponseFields(response, refundDetail, gatewayResponse);
            updateRefundRequestResult(refundDetail, "SUCCESS", response, gatewayResponse);
            // 汇总 MUST 在明细置为 SUCCESS 之后执行：SQL 是按 PAY_REFUND_DETAIL 重算的，
            // 顺序颠倒会漏掉本笔。金额与状态都不再由这里传入，避免「用 UPDATE 前的旧快照
            // 算已退总额」以及重复执行重复累加（详见 PayTxnDetailMapper.xml 内注释）。
            int summaryAffected = payTxnDetailMapper.updateRefundSummary(payTxn.getOrderNo());
            if (summaryAffected == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, orderNo={}, refundOrderNo={}",
                        payTxn.getOrderNo(), refundDetail.getRefundOrderNo());
            }
            return response;
        } catch (Exception e) {
            log.error("处理请求退款异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /** 退款回查的扫描窗口（天）：只回查最近 N 天的 TXN_DATE，同时用于分区裁剪。 */
    private static final int REFUND_QUERY_SCAN_DAYS = 7;

    /** 距最近一次发起退款至少多少分钟才回查，避开正常同步应答的时间窗。 */
    private static final int REFUND_QUERY_STALE_MINUTES = 5;

    /** 未得终态时的退避间隔（秒），写进 NEXT_REQUEST_TIME。 */
    private static final int REFUND_QUERY_RETRY_DELAY_SECONDS = 300;

    /** 单轮扫描上限，与解约侧的 BATCH_SIZE 口径一致。 */
    private static final int REFUND_QUERY_BATCH_SIZE = 200;

    /*
     * 退款回查补偿（批次 5B 新增，与 requestRefund 摘事务同批 —— ADR-D8 要求「移出事务 + 落状态 + 补偿」一起做完）。
     *
     * 本方法 NEVER 加 @Transactional：它逐行出网调支付中心，被事务包住就是把刚修掉的形状原地复现。
     * 每行的两条写（finishFromQuery / delayNextRefundQuery）都是单语句 CAS，自动提交即可。
     *
     * 本模块 NEVER 自带 @Scheduled（全模块一个都没有，见 AGENTS.md §2.2.1）：
     * 触发方是 web-admin 的 Quartz `sys_job`，入口 POST /internal/payment/compensateRefundQuery，
     * 调用方可反复调用直到 scanned 为 0。
     */
    @Override
    public CompensateNotifyRespDTO compensateRefundQuery() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            if (!refundGatewayPort.refundQueryConfigured()) {
                log.error("未配置 pay.sign.refund-query-url，退款回查无法进行，停在 PROCESSING 的退款单本轮无人收口");
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "未配置退款查询地址");
                return response;
            }
            String txnDateFrom = LocalDateTime.now().minusDays(REFUND_QUERY_SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<PayRefundDetail> pending = payRefundDetailMapper.selectCompensableRefundQuery(
                    txnDateFrom, REFUND_QUERY_STALE_MINUTES, REFUND_QUERY_BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            // 扫描循环走 OutboxScan（ADR-D46），NEVER 手写 for + try/catch：
            // 「单条失败不中断整批 / 每行只计一次 / 兜住两侧异常」三条不变量已在骨架里固化并有测试。
            OutboxScan.Result scan = OutboxScan.run(pending,
                    this::settleRefundByQuery,
                    row -> log.warn("退款回查本轮未收口，等下次重扫, refundOrderNo={}, txnDate={}",
                            row.getRefundOrderNo(), row.getTxnDate()),
                    (row, e) -> log.error("单条退款回查异常，NEVER 因此中断整批, refundOrderNo={}, txnDate={}",
                            row.getRefundOrderNo(), row.getTxnDate(), e));
            response.setScanned(scan.scanned());
            response.setSubmitted(scan.success());
            response.setSkipped(scan.failed());
            log.info("退款回查补偿完成, txnDateFrom={}, scanned={}, settled={}, pendingAgain={}",
                    txnDateFrom, scan.scanned(), scan.success(), scan.failed());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("退款回查补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 回查一笔停在 {@code PROCESSING} 的退款并尝试收口。
     *
     * @return {@code true} 仅当「支付中心给出终态」且「CAS 真的推进了这一行」
     */
    private boolean settleRefundByQuery(PayRefundDetail row) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        // 两个键都送、值都填我方的 REFUND_ORDER_NO，这是 2026-09-15 实测逼出来的写法，NEVER 只送一个。
        //
        // 文档（docs/external/支付中心网关接口文档.md §3.2）写的是 refundOrderNo / merchantRefundNo
        // 「至少填一个」，我方原先只送 refundOrderNo（§3.1 请求退款时上送的就是这个键，
        // 值即 PAY_REFUND_DETAIL.REFUND_ORDER_NO）。但真实网关的应答是
        //     {"code":9999,"msg":"退款流水号或商户退款流水号必填"}
        // —— 它认为两个都没填。注意它的措辞是「流水号」而文档写「订单号」，字段名与文档并不一致。
        // 这与已记录的「网关路径必须实测、漏 /v1 返 600 而不是 404」是同型缺陷的第二个变种：
        // **出向 bizData 的字段名同样不能只看文档，MUST 拿真实应答验证**。
        //
        // 后果不是报错而是**永久空转**：resolveRefundQueryStatus 拿不到终态 → delayNextRefundQuery
        // 推 5 分钟 → 下轮再来，退款单永远收不了口，且每轮都返 0000「成功」，调度日志一片绿。
        // 实测痕迹：RF2026062516090566197559552 的 NEXT_REQUEST_TIME 被反复往后推。
        //
        // 为什么两个都送而不是只换成 merchantRefundNo：文档明写「至少填一个」，多送一个不违规；
        // 而我方**只有**自己生成的这一个号可送 —— 网关侧的 refundNo / channelRefundNo 我方
        // 43 条 SUCCESS 退款里全为 NULL、从来没保存过。若网关坚持要它自己的 refundNo，
        // 那不是本方法能修的，MUST 找 bestonepay 要字段清单（连带补齐 §3.1 应答的落库）。
        bizData.put("refundOrderNo", row.getRefundOrderNo());
        bizData.put("merchantRefundNo", row.getRefundOrderNo());
        GatewayReply queryReply = refundGatewayPort.queryRefund(bizData);
        log.info("退款回查支付中心返回, refundOrderNo={}, txnDate={}, gatewayResponse={}",
                row.getRefundOrderNo(), row.getTxnDate(), JSON.toJSONString(queryReply.raw()));

        String settledStatus = resolveRefundQueryStatus(queryReply);
        if (settledStatus == null) {
            int delayed = payRefundDetailMapper.delayNextRefundQuery(
                    row.getRefundOrderNo(), row.getTxnDate(), REFUND_QUERY_RETRY_DELAY_SECONDS);
            if (delayed == 0) {
                log.info("退款回查未得终态且退避 CAS 命中 0 行，说明这一行已被别的路径收口, refundOrderNo={}, txnDate={}",
                        row.getRefundOrderNo(), row.getTxnDate());
            }
            return false;
        }

        PayRefundDetail update = new PayRefundDetail();
        update.setRefundOrderNo(row.getRefundOrderNo());
        update.setTxnDate(row.getTxnDate());
        update.setRefundStatus(settledStatus);
        PaySignGatewayResponse gatewayResponse = queryReply.raw();
        Map<String, Object> data = gatewayResponse.getData();
        update.setMerchantRefundNo(stringValue(data.get("merchantRefundNo"), null));
        update.setRefundNo(stringValue(data.get("refundNo"), null));
        update.setChannelRefundNo(stringValue(data.get("channelRefundNo"), null));
        update.setRefundTime(stringValue(data.get("refundTime"), null));
        update.setPayCenterCode(gatewayResponse.getCode() == null ? null : String.valueOf(gatewayResponse.getCode()));
        update.setPayCenterMsg(gatewayResponse.getMsg());
        update.setResponseBody(JSON.toJSONString(gatewayResponse));

        int affected = payRefundDetailMapper.finishFromQuery(update);
        if (affected == 0) {
            // CAS 命中 0 行 = 这一行已不是 PROCESSING（退款回调或另一副本先收口了）。
            // NEVER 当成功继续：继续下去会拿本次回查结果去重算汇总，把别人刚写对的口径覆盖掉。
            log.warn("退款回查收口 CAS 命中 0 行，已被其它路径收口，本轮不重算汇总, refundOrderNo={}, txnDate={}, 回查状态={}",
                    row.getRefundOrderNo(), row.getTxnDate(), settledStatus);
            return false;
        }

        // 汇总 MUST 在明细置终态之后执行，且这里 MUST 无条件执行一次 ——
        // requestRefund 摘掉事务后存在「明细已 SUCCESS、汇总没重算」的中间态，只有这里能兜住它。
        // 该 SQL 按 PAY_REFUND_DETAIL 全量重算，重复执行不会重复累加（见 PayTxnDetailMapper.xml 内注释）。
        int summaryAffected = payTxnDetailMapper.updateRefundSummary(row.getOrderNo());
        if (summaryAffected == 0) {
            log.error("退款回查后汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, orderNo={}, refundOrderNo={}",
                    row.getOrderNo(), row.getRefundOrderNo());
        }
        log.info("退款回查已收口, refundOrderNo={}, txnDate={}, refundStatus={}, summaryAffected={}",
                row.getRefundOrderNo(), row.getTxnDate(), settledStatus, summaryAffected);
        return true;
    }

    /**
     * 把 §3.2 退款查询应答里的 {@code status} 归一成本地终态，无法判定时返回 {@code null}。
     *
     * <p><b>白名单</b>：只认 {@code SUCCESS} 与 {@code FAIL} 两个终态，其余一律当「还没到终态」退避重来。
     * {@link com.chinasofti.huateng.paysign.support.PaySignValues#convertPayStatus} 在这里**只借用它的
     * 大小写与同义词归一**（PAID→SUCCESS、FAILED→FAIL），它对空值回落的 {@code PROCESSING}
     * 与任何未知措辞都会被下面这层白名单挡掉。<b>NEVER 改成「不是 SUCCESS 就当 FAIL」</b> ——
     * 那会把一笔仍在渠道处理中的退款写成失败，而 PAY_TXN_DETAIL 的已退总额是按本表重算的。</p>
     */
    private String resolveRefundQueryStatus(GatewayReply reply) {
        if (!(reply instanceof GatewayReply.Accepted accepted) || accepted.data() == null) {
            return null;
        }
        String status = stringValue(accepted.data().get("status"), null);
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = convertPayStatus(status);
        if ("SUCCESS".equals(normalized) || "FAIL".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    /** 退款汇总跨表对账的扫描窗口（天）：只对最近 N 天的 TXN_DATE，同时用于分区裁剪。 */
    private static final int REFUND_SUMMARY_SCAN_DAYS = 7;

    /** 单轮扫描上限，与退款回查侧的 BATCH_SIZE 口径一致。 */
    private static final int REFUND_SUMMARY_BATCH_SIZE = 200;

    /**
     * 退款汇总跨表对账补偿：把 {@code PAY_TXN_DETAIL} 的两列汇总重算回与
     * {@code PAY_REFUND_DETAIL}（唯一账本）一致。
     *
     * <p><b>本方法 NEVER 加 {@code @Transactional}</b>（本类刻意不带事务，见类注释与 ADR-D8 / ADR-D48）。
     * 三条理由：① 每条 {@code updateRefundSummary} 都是按明细全量重算的单语句幂等 UPDATE，
     * 自动提交即已正确，不需要相互原子；② 整批包一个事务会把 200 行原支付订单的排他锁攒到批次末尾才放，
     * 期间上游对这些单的退款 / 回调全部堵在同一批行上；③ 整批一个事务时任何一行抛异常都会连带
     * 回滚前面已经算对的那些，逐单独立收口严格更安全。<b>NEVER 加回。</b></p>
     *
     * <p>扫两类、处置相反，见 {@code PayRefundDetailMapper} 两个扫表方法的 javadoc。
     * <b>A 类的判据是「影响行数 &gt; 0」而不是「没抛异常」</b>：0 行意味着这一单在重算前
     * 已被别的路径改掉或已消失，计入 {@code skipped} 留给下一轮，NEVER 计成已修。</p>
     */
    @Override
    public CompensateNotifyRespDTO compensateRefundSummary() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            String txnDateFrom = LocalDateTime.now().minusDays(REFUND_SUMMARY_SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<String> drifted = payRefundDetailMapper.selectDriftedRefundSummary(
                    txnDateFrom, REFUND_SUMMARY_BATCH_SIZE);
            List<String> orphans = payRefundDetailMapper.selectOrphanRefundOrders(
                    txnDateFrom, REFUND_SUMMARY_BATCH_SIZE);

            // 两轮都走 OutboxScan（ADR-D46），NEVER 手写 for + try/catch：
            // 「单条失败不中断整批 / 每行只计一次 / 兜住两侧异常」三条不变量已在骨架里固化并有测试。
            OutboxScan.Result driftScan = OutboxScan.run(drifted,
                    orderNo -> payTxnDetailMapper.updateRefundSummary(orderNo) > 0,
                    orderNo -> log.warn("退款汇总重算影响 0 行，该单在重算前已被别的路径改掉或已消失，"
                            + "本轮不计入已修，留给下一轮, orderNo={}", orderNo),
                    (orderNo, e) -> log.error("单条退款汇总重算异常，NEVER 因此中断整批, orderNo={}", orderNo, e));

            // B 类：一行都不改。deliver 恒返 false，于是全部落进 onFailure 计入 skipped ——
            // updateRefundSummary 对这一类影响 0 行（PAY_TXN_DETAIL 里没有这个 ORDER_NO），
            // 调它既修不好、又会把一批真正的坏账混进「已扫过」的口径里。NEVER 在这里调它。
            OutboxScan.Result orphanScan = OutboxScan.run(orphans,
                    orderNo -> false,
                    orderNo -> log.warn("退款明细已 SUCCESS 但原支付订单不存在，MUST 人工核对这笔退款对应哪张原单，"
                            + "本任务不会自愈, orderNo={}", orderNo),
                    (orderNo, e) -> log.error("单条孤儿退款订单登记异常，NEVER 因此中断整批, orderNo={}", orderNo, e));

            response.setScanned(driftScan.scanned() + orphanScan.scanned());
            response.setSubmitted(driftScan.success() + orphanScan.success());
            response.setSkipped(driftScan.failed() + orphanScan.failed());
            log.info("退款汇总跨表对账补偿完成, txnDateFrom={}, scanned={}, submitted={}, skipped={}, "
                            + "其中不可自愈（原单不存在）={}",
                    txnDateFrom, response.getScanned(), response.getSubmitted(), response.getSkipped(),
                    orphanScan.scanned());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("退款汇总跨表对账补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
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
    /** 入参由应答体换成 {@link GatewayReply}（ADR-D113 续）：成功码判定已收进端口。 */
    private void fillGatewayFields(RequestRefundResult response, GatewayReply reply) {
        PaySignGatewayResponse gatewayResponse = reply == null ? null : reply.raw();
        if (gatewayResponse == null) {
            return;
        }
        response.setCode(gatewayResponse.getCode());
        response.setMsg(gatewayResponse.getMsg());
        response.setSuccess(reply instanceof GatewayReply.Accepted);
        response.setData(gatewayResponse.getData());
    }
}
