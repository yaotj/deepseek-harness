package com.chinasofti.huateng.transquery.query;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayTxnBriefDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelDetailDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 支付宝出行行程查询（乘车记录列表 + 乘车记录详情）。
 *
 * <p>本类沿用 {@link FepAppErrorCodeEnum} 而非本模块的 {@code TransQueryErrorCodeEnum}：
 * 这两条是支付宝渠道的对外契约，retCode 取值不允许因为换了宿主服务而改变。
 *
 * <p>列表与详情合在同一个类里，是因为 {@code queryPayTxnBrief} 与 {@code mapPayStatusToDebitResult}
 * 被两条链路共用——拆成两个 Handler 等于留两份逐字副本。
 *
 * <p>两条链路的取数口径自 2026-09-18 起完全一致：<b>只用 {@code GATE_TXN_PAY} + {@code ALIPAY_PAY_TXN_DETAIL}</b>。
 * <b>NEVER 回退成从 ticket-server 的 {@code QRCODE_TXN_DETAIL} 取进出站明细</b>——那边
 * {@code companionFlag} 映射的是 {@code TRX_TYPE}、{@code ticketCode} 映射的是 {@code CARD_TYPE}，
 * 且详情侧原先为此要发两次 RPC 再 500ms 超时合并，属被裁决删除的实现。
 */
@Component
public class AlipayTravelQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(AlipayTravelQueryHandler.class);

    /** 支付宝出行的发行渠道码：列表侧靠它把 {@code GATE_TXN_PAY} 收窄到本渠道，同时也是契约里的 {@code payChannelCode}。 */
    private static final String ALIPAY_ISSUE_CHANNEL_CODE = "07";

    /** {@code page} 不传时的页长，与迁入前的 10 一致。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * {@code size} 的上界，超过即钳到这里。
     *
     * <p>与 IF8A-05（`TransListQueryHandler`）的 `pageSize` 上限 **100 对齐**，不是本接口新定的数。
     * <b>为什么必须有上界</b>（2026-09-20 实测）：`size` 原先只有 `<= 0` 回落默认、**没有上界**，
     * 于是 `size=100000` 原样进 SQL 的 `rn <= offset + limit` —— 本渠道数据量小时看不出，
     * 表大了就是一次全窗口扫描 + 一次性把全部行序列化进一个响应。
     * <b>NEVER 去掉这个钳制，也 NEVER 改成「超界即回落 10」</b> —— 上游要 100 条时给 10 条属静默截断，
     * 钳到上界才是可解释的行为。
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * {@code discountFee} / {@code discountInfo} 的唯一取值。
     *
     * <p>按 2026-09-18 的裁决，本域只允许从 {@code GATE_TXN_PAY} 与 {@code ALIPAY_PAY_TXN_DETAIL} 取数，
     * 而两张表都没有渠道优惠列 —— <b>NEVER 拿 {@code DISCOUNT_LEVEL_AMT}（优惠档位阈值）或
     * {@code ORIGINAL_FARE - TOTAL_AMOUNT} 顶上</b>，那不是渠道优惠金额。
     */
    private static final String EMPTY_DISCOUNT = "";

    /** 对外契约里 {@code payOrderNoDate} 的长度：14 位 {@code yyyyMMddHHmmss}，与进出站时间同格式。 */
    private static final int PAY_ORDER_NO_DATE_LENGTH = 14;

    /**
     * 列表 {@code startDate} / {@code endDate} 落到 SQL 时的长度：8 位 {@code yyyyMMdd}。
     *
     * <p>这个长度不是选的，是 {@code GATE_TXN_PAY.TXN_DATE} 的形态决定的 —— 那是
     * {@code VARCHAR2(8)} 且按月分区，谓词 {@code TXN_DATE >= #{startDate}} 走的是**字符串比较**。
     * 因此入参 <b>MUST 先归一成 8 位纯数字再进 SQL</b>，见 {@code normalizeQueryDate}。
     */
    private static final int QUERY_DATE_LENGTH = 8;

    private final AlipayPaySignClient alipayPaySignClient;
    private final GateTxnPayClient gateTxnPayClient;

    /** 构造注入（不是本模块其余 Handler 的字段注入写法）：单测要能直接 new 出来并塞 mock。 */
    @Autowired
    public AlipayTravelQueryHandler(AlipayPaySignClient alipayPaySignClient, GateTxnPayClient gateTxnPayClient) {
        this.alipayPaySignClient = alipayPaySignClient;
        this.gateTxnPayClient = gateTxnPayClient;
    }

    /** 查询乘车记录列表。 */
    public AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录列表,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录列表,参数校验失败");
            return response;
        }
        String startDate;
        String endDate;
        try {
            startDate = normalizeQueryDate(request.getStartDate(), "startDate");
            endDate = normalizeQueryDate(request.getEndDate(), "endDate");
        } catch (IllegalArgumentException e) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录列表,日期参数非法: {}", e.getMessage());
            return response;
        }
        try {
            int pageNum = parseInt(request.getPage(), 0, "page");
            int pageSize = parseInt(request.getSize(), DEFAULT_PAGE_SIZE, "size");
            if (pageSize <= 0) {
                pageSize = DEFAULT_PAGE_SIZE;
            }
            if (pageSize > MAX_PAGE_SIZE) {
                log.warn("支付宝出行-查询乘车记录列表,size 超过上界已钳制, 原值={}, 实际={}", pageSize, MAX_PAGE_SIZE);
                pageSize = MAX_PAGE_SIZE;
            }

            QueryTransListReqDTO gateRequest = new QueryTransListReqDTO();
            gateRequest.setThirdUserId(request.getThirdUserId());
            gateRequest.setStartDate(startDate);
            gateRequest.setEndDate(endDate);
            gateRequest.setDebitRequestResult(request.getDebitRequestResult());
            gateRequest.setIssueChannelCode(ALIPAY_ISSUE_CHANNEL_CODE);
            gateRequest.setOffset(resolveOffset(pageNum, pageSize));
            gateRequest.setLimit(pageSize);

            List<GateTxnPayListDTO> gateRecords = gateTxnPayClient.requestTransList(gateRequest);
            int total = gateTxnPayClient.countTransList(gateRequest);
            int totalPage = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
            log.info("支付宝出行-查询乘车记录列表,扣费订单分页结果, 本页={}条, total={}",
                    gateRecords == null ? 0 : gateRecords.size(), total);

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            response.setPageNumber(pageNum);
            response.setPageSize(pageSize);
            response.setTotalPage(totalPage);
            response.setTotalCount(total);
            response.setTicketTransRecord(assembleRecords(gateRecords));
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录列表 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-查询乘车记录列表,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    /**
     * 两次请求合并：第一次已拿到本页扣费订单，这里只补支付侧的 3 个字段。
     *
     * <p><b>NEVER 退回成「每行一次 RPC」</b>：本页 orderNo 一次批查，条数上限由
     * {@code AlipayPaySignClient.queryPayTxnBrief} 硬拒（Oracle IN 列表 1000）。
     * 命中不到支付明细的行照样出现在列表里（闸机建了单、支付明细还没落），
     * 支付侧字段留空，<b>NEVER 因为查不到支付明细就把整行丢掉</b>。
     * {@code debitRequestResult} 不受本次批查影响 —— 它恒取 {@code GATE_TXN_PAY.DEBIT_STATUS}。
     */
    private List<AlipayTripTravelRecordDTO> assembleRecords(List<GateTxnPayListDTO> gateRecords) {
        if (gateRecords == null || gateRecords.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, AlipayTripPayTxnBriefDTO> briefMap = queryPayTxnBriefs(gateRecords);
        List<AlipayTripTravelRecordDTO> records = new ArrayList<>(gateRecords.size());
        for (GateTxnPayListDTO gate : gateRecords) {
            records.add(buildTravelRecord(gate, briefMap.get(gate.getOrderNo())));
        }
        return records;
    }

    /** 按 {@code orderNo} 建索引 —— <b>NEVER 按下标与入参列表对齐</b>，返回列表不含未命中的 orderNo。 */
    private Map<String, AlipayTripPayTxnBriefDTO> queryPayTxnBriefs(List<GateTxnPayListDTO> gateRecords) {
        Set<String> orderNos = new LinkedHashSet<>();
        for (GateTxnPayListDTO gate : gateRecords) {
            if (StringUtils.hasText(gate.getOrderNo())) {
                orderNos.add(gate.getOrderNo());
            }
        }
        if (orderNos.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, AlipayTripPayTxnBriefDTO> briefMap = new HashMap<>();
        try {
            List<AlipayTripPayTxnBriefDTO> briefs = alipayPaySignClient.queryPayTxnBrief(new ArrayList<>(orderNos));
            if (briefs != null) {
                for (AlipayTripPayTxnBriefDTO brief : briefs) {
                    if (brief != null && StringUtils.hasText(brief.getOrderNo())) {
                        briefMap.put(brief.getOrderNo(), brief);
                    }
                }
            }
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录列表,批量补齐支付明细失败, 请求条数={}", orderNos.size(), e);
        }
        log.info("支付宝出行-查询乘车记录列表,批量补齐支付明细, 请求={}条, 命中={}条", orderNos.size(), briefMap.size());
        return briefMap;
    }

    /**
     * 单条记录组装：**能在第一次请求拿到的字段全部取自 {@code gate}**，只有下面 3 个来自支付明细 ——
     * {@code payTradeOrderNo} / {@code payOrderNoDate} / {@code invoice}。
     * {@code debitRequestResult} 恒由 {@code gate.debitStatus} 映射，见 {@code mapDebitStatusToResult}。
     *
     * <p>金额口径与详情侧逐字一致（原实现取自 {@code QRCODE_TXN_DETAIL}）：
     * {@code payAmount} = 车费 {@code TRX_AMOUNT}，{@code totalAmount} = 车费 + 超时费 {@code TOTAL_AMOUNT}。
     * <b>NEVER 把两者都填 `TOTAL_AMOUNT`</b>，那会让超时那笔看不出差额。
     *
     * <p>{@code discountFee} / {@code discountInfo} <b>恒为空串</b>：这两张表都没有渠道优惠列，
     * 成因与 NEVER 清单见 {@code AlipayTripTravelRecordDTO} 上那两个字段的注释。
     *
     * <p>{@code invoice} 只回显、<b>不参与筛选</b>（2026-09-18 裁决）：列表分页谓词里已没有 invoice 条件。
     */
    private AlipayTripTravelRecordDTO buildTravelRecord(GateTxnPayListDTO gate, AlipayTripPayTxnBriefDTO brief) {
        AlipayTripTravelRecordDTO record = new AlipayTripTravelRecordDTO();
        record.setEntryStationName(gate.getEntryStationName());
        record.setEntryDate(gate.getInTime());
        record.setExitStationName(gate.getExitStationName());
        record.setExitDate(gate.getOutTime());
        record.setPayAmount(toStringOrNull(gate.getTrxAmount()));
        record.setTotalAmount(toStringOrNull(gate.getTotalAmount()));
        record.setOrderExpType(StringUtils.hasText(gate.getOrderExpType()) ? gate.getOrderExpType() : "0");
        record.setTradeOrderNo(gate.getOrderNo());
        record.setPayChannelCode(ALIPAY_ISSUE_CHANNEL_CODE);
        record.setCardNum(gate.getCardId());
        record.setCompanionFlag(gate.getCompanionFlag());
        record.setTicketCode(gate.getTicketCode());
        record.setCountingTimes(toStringOrNull(gate.getCountingTimes()));
        record.setCountingFlag(gate.getCountingFlag());
        record.setDiscountFee(EMPTY_DISCOUNT);
        record.setDiscountInfo(EMPTY_DISCOUNT);
        record.setDebitRequestResult(mapDebitStatusToResult(gate.getDebitStatus()));

        if (brief != null) {
            record.setPayTradeOrderNo(brief.getChannelOrderNo());
            record.setPayOrderNoDate(normalizePayOrderNoDate(brief.getTransTime()));
            record.setInvoice(brief.getInvoice());
        }
        return record;
    }

    /**
     * 算分页 `offset`：<b>MUST 用 long 乘再钳回 int</b>。
     *
     * <p><b>原实现是 `pageNum * pageSize` 两个 int 直乘、没有溢出保护</b>，而 `page` / `size` 都是
     * 上游可控的字符串入参。2026-09-20 实测两个真实的越界返数据：
     * `page=1073741824 & size=4` ⇒ `1073741824 × 4 = 2^32 ≡ 0`，于是**越界页返回了第 0 页的 4 条**
     * （响应里 `pageNumber` 还照样回显 1073741824）；`page=2 & size=2000000000` ⇒ offset 溢出成
     * `-294967296`，谓词 `rn > 负数` 成立，**返回了全部 10 条**。
     *
     * <p>钳到 {@link Integer#MAX_VALUE} 而不是抛异常：越界页属「空结果」不属「参数非法」，
     * 与本接口 `start > end` 返 0 条同一口径（见 ADR-D146 续 4）。`rn > 2147483647` 必然无行，
     * 而 SQL 里 `#{offset} + #{limit}` 是 Oracle 端 NUMERIC 相加、**不会再溢出一次**，
     * 因此传 `Integer.MAX_VALUE` 是安全的。`QueryTransListReqDTO.offset` 是 `Integer`，
     * <b>NEVER 为了塞更大的值去改那个 DTO 的字段类型</b> —— 它是跨模块共享的对内契约。
     *
     * <p>入参前提：`parseInt` 已拒掉负数（回落默认值），所以这里只需管上界。
     */
    private int resolveOffset(int pageNum, int pageSize) {
        long offset = (long) pageNum * pageSize;
        if (offset > Integer.MAX_VALUE) {
            log.warn("支付宝出行-查询乘车记录列表,offset 越界已钳制, page={}, size={}, offset={}",
                    pageNum, pageSize, offset);
            return Integer.MAX_VALUE;
        }
        return (int) offset;
    }

    /** {@code page} / {@code size} 解析失败时回落默认值、不报错 —— 与迁入前的行为逐字一致。 */
    private int parseInt(String value, int defaultValue, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 0 ? defaultValue : parsed;
        } catch (NumberFormatException e) {
            log.warn("{} 参数格式错误: {}", fieldName, value);
            return defaultValue;
        }
    }

    /**
     * 把列表的日期入参归一成 8 位 {@code yyyyMMdd}：剥掉全部非数字字符后取前 8 位。
     *
     * <p><b>为什么必须归一，而不是原样透传</b>（2026-09-20 实测 + 用户裁决）：
     * 谓词是 {@code TXN_DATE >= #{startDate}}，而 {@code TXN_DATE} 是 {@code VARCHAR2(8)} ——
     * 这是**字符串比较**。上游传 {@code 2026-09-20} 时，逐字符比到第 5 位是 {@code '0'(0x30)} vs
     * {@code '-'(0x2D)}，于是 {@code '20260920' >= '2026-09-20'} 恒成立，
     * <b>条件退化成恒真、静默返回未筛选的全量</b>（不报错、上游以为筛过了）。
     * 这比「忽略了条件」更危险，因为它连一行 WARN 都不留。
     *
     * <p><b>为什么是宽容归一而不是严格校验</b>：甲方规格 R6 表145 对这两个字段只写
     * 「String / 开始日期（可选）」，<b>没有规定格式、长度或示例</b>。既然契约没写，
     * 就 NEVER 自造一个必填格式去拒绝上游 —— 因此 {@code 2026-09-20}（10 位带横杠）与
     * {@code 20260920101623}（14 位带时分秒）都接受并归成 {@code 20260920}。
     * 只有剥完不足 8 位（如 {@code 2026}）才抛 {@code IllegalArgumentException}，
     * 由调用点转 {@code 8001} —— 那种入参无论如何都拼不出一个日期。
     *
     * <p>手法与本类的 {@code normalizePayOrderNoDate}、{@code PayTxnCallbackWriter.normalizeTransTime}
     * 同形（都是「剥非数字 + 取前 N 位」），<b>NEVER 为此抽公共工具类</b>：三处的 N 与非法时的处置
     * 各不相同（14 位原样返回 / 14 位原样入库 / 8 位抛异常），合并只会把三套语义拧成一个带开关的方法。
     * <b>也 NEVER 复用 {@code TransQueryParamNormalizer.normalizeDate}</b> —— 那是 IF8A-05 的
     * {@code yyyy-MM-dd} 口径，喂 8 位纯数字会直接抛异常，把合法请求打成 {@code 8001}。
     *
     * @throws IllegalArgumentException 剥完非数字后不足 8 位
     */
    private String normalizeQueryDate(String date, String fieldName) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        String digits = date.replaceAll("\\D", "");
        if (digits.length() < QUERY_DATE_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " 无法归一成 " + QUERY_DATE_LENGTH + " 位 yyyyMMdd: " + date);
        }
        String normalized = digits.substring(0, QUERY_DATE_LENGTH);
        if (!normalized.equals(date)) {
            log.info("支付宝出行-查询乘车记录列表,{} 已归一, 原值={}, 归一后={}", fieldName, date, normalized);
        }
        return normalized;
    }

    private String toStringOrNull(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 查询乘车记录详情。
     *
     * <p><b>应答的 20 个业务字段 MUST 包在 `data` 对象里</b>（2026-09-20 按支付宝侧实测要求确立，ADR-D150）：
     * 顶层只有 `retCode` / `retMsg` + `data`，业务体是 {@code AlipayTripTravelDetailDTO}。
     * <b>NEVER 回退成扁平结构</b> —— 那是同日 ADR-D148 按 R6 §3.72 表148 推出的口径（表148 把字段画在顶层、
     * 之后没有子表），但**对接方实际按 `data` 解析**，外部契约以对方的真实解析行为为准
     * （与「支付中心网关字段名 MUST 实测」同一条判据）。
     *
     * <p>失败分支只填 `retCode` / `retMsg`、`data` 留 null，<b>NEVER 塞空对象凑字段</b>。
     * 列表接口 `findTravelList` 仍是顶层 `ticketTransRecord` 子表（表146），<b>NEVER 顺手也给它包 `data`</b>。
     */
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelDetailRespDTO detailResp = new AlipayTripFindTravelDetailRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId()) || !StringUtils.hasText(request.getOrderNo())) {
            detailResp.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            detailResp.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录详情,参数校验失败");
            return detailResp;
        }
        try {
            GateTxnPayListDTO order = gateTxnPayClient.queryByOrderNo(request.getOrderNo());
            if (order == null) {
                detailResp.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                detailResp.setRetMsg("支付订单不存在");
                log.warn("支付宝出行-查询乘车记录详情,扣费订单不存在, orderNo={}", request.getOrderNo());
                return detailResp;
            }

            JSONObject industryDetail = null;
            if (StringUtils.hasText(order.getIndustryDetail())) {
                try {
                    industryDetail = JSONObject.parseObject(order.getIndustryDetail());
                } catch (Exception e) {
                    log.warn("支付宝出行-查询乘车记录详情,行业明细解析失败, orderNo={}, industryDetail={}",
                            request.getOrderNo(), order.getIndustryDetail(), e);
                }
            }
            log.info("支付宝出行-查询乘车记录详情,扣费订单查询成功, orderNo={}, inStation={}, outStation={}, 有无行业明细={}",
                    request.getOrderNo(), order.getInStation(), order.getOutStation(), industryDetail != null);

            detailResp.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            detailResp.setRetMsg("成功");

            AlipayTripTravelDetailDTO detail = new AlipayTripTravelDetailDTO();
            detail.setEntryStationName(order.getEntryStationName());
            detail.setEntryDate(order.getInTime());
            detail.setExitStationName(order.getExitStationName());
            detail.setExitDate(order.getOutTime());
            detail.setPayAmount(toStringOrNull(order.getTrxAmount()));
            detail.setTotalAmount(toStringOrNull(order.getTotalAmount()));
            detail.setOrderExpType(StringUtils.hasText(order.getOrderExpType()) ? order.getOrderExpType() : "0");

            detail.setTradeOrderNo(order.getOrderNo());
            detail.setDebitRequestResult(mapDebitStatusToResult(order.getDebitStatus()));
            detail.setCardNum(order.getCardId());
            detail.setPayChannelCode(ALIPAY_ISSUE_CHANNEL_CODE);

            // 这 5 个字段 MUST 取自 GATE_TXN_PAY：闸机上送时就落在这张表上，
            // 而进出站明细（QRCODE_TXN_DETAIL）那边 companionFlag 映射的是 TRX_TYPE、countingTimes / countingFlag
            // 压根没映射。NEVER 回退成从 ticket-server 的明细取，也 NEVER 把 countingFlag 硬编码成 "N"。
            detail.setCompanionFlag(order.getCompanionFlag());
            detail.setTicketCode(order.getTicketCode());
            detail.setCountingTimes(toStringOrNull(order.getCountingTimes()));
            detail.setCountingFlag(order.getCountingFlag());
            detail.setDiscountFee(EMPTY_DISCOUNT);
            detail.setDiscountInfo(EMPTY_DISCOUNT);

            // 支付侧三字段走与列表同一个批量端点（这里只有一笔，仍复用它、NEVER 另造单笔接口）。
            // 查不到支付明细时保持为空：那是「闸机建了单、支付明细还没落」，NEVER 拿 GATE_TXN_PAY.OUT_TIME
            // 冒充 payOrderNoDate —— 出站时间不是支付时间。
            // debitRequestResult 不在这三个里：它已在上面由 GATE_TXN_PAY.DEBIT_STATUS 定死，
            // NEVER 再用 ALIPAY_PAY_TXN_DETAIL.PAY_STATUS 覆盖（见该方法上的说明）。
            AlipayTripPayTxnBriefDTO brief = queryPayTxnBrief(order.getOrderNo());
            if (brief != null) {
                detail.setPayTradeOrderNo(brief.getChannelOrderNo());
                detail.setPayOrderNoDate(normalizePayOrderNoDate(brief.getTransTime()));
                detail.setInvoice(brief.getInvoice());
            }
            detailResp.setData(detail);

            log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(detailResp));
            return detailResp;
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情异常, request={}", request, e);
            detailResp.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            detailResp.setRetMsg("系统内部错误");
            log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(detailResp));
            return detailResp;
        }
    }

    /** 详情侧单笔补齐：复用列表那条批量端点，失败只记日志、返 {@code null}（详情本身仍要答出闸机侧字段）。 */
    private AlipayTripPayTxnBriefDTO queryPayTxnBrief(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            List<AlipayTripPayTxnBriefDTO> briefs = alipayPaySignClient.queryPayTxnBrief(List.of(orderNo));
            if (briefs == null || briefs.isEmpty()) {
                log.info("支付宝出行-查询乘车记录详情,未命中支付明细, orderNo={}", orderNo);
                return null;
            }
            return briefs.get(0);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情,补齐支付明细失败, orderNo={}", orderNo, e);
            return null;
        }
    }

    /**
     * 把 {@code ALIPAY_PAY_TXN_DETAIL.TRANS_TIME} 归一成对外契约的 14 位 {@code yyyyMMddHHmmss}。
     *
     * <p>这一列存的是**支付中心回调报文原文、格式不统一**（该列因此才建成 {@code VARCHAR2(32)}）：
     * 2026-09-19 实测库内唯一非空值是 19 位的 {@code 2026-09-18 16:44:41}，而单测钉过的另一形态是
     * 14 位 {@code 20260918021500}。原样透传会让同一份应答里 {@code entryDate} / {@code exitDate}（14 位，
     * 来自 {@code GATE_TXN_PAY.IN_TIME} / {@code OUT_TIME}）与 {@code payOrderNoDate} 两种格式并存，
     * 支付宝侧按定长解析就会错位。14 位这个口径与 IF8A-05 / IF8A-34 一致
     * （{@code TransRecordAssembler} 取的 {@code PAY_TXN_DETAIL.PAY_TIME} 本身就是 14 位，
     * 日票那条回填也用 {@code SimpleDateFormat("yyyyMMddHHmmss")}）。
     *
     * <p>规则：只保留数字，<b>够 14 位就取前 14 位</b>（顺带截掉带毫秒的尾巴）；
     * <b>不足 14 位一律原样返回并打 WARN</b> —— 例如 13 位毫秒时间戳（旧 {@code ALIPAY_PAY_LOG} 有过这种值），
     * <b>NEVER 猜着补零、也 NEVER 按时间戳换算</b>，那会造出一个看着像时间其实错的值。
     * <b>NEVER 改成 `TO_DATE` / `SimpleDateFormat.parse` 去解析这一列</b>：格式不统一，解析必然在某些行上抛异常。
     *
     * <p><b>2026-09-20 起写入侧也归一了</b>（alipay-pay-sign-server 1.1.43 的
     * {@code PayTxnCallbackWriter.normalizeTransTime}，见 ADR-D146 续 3），新落库的行本身就是 14 位，
     * 本方法退化成只兜两类行：归一前的历史行，以及归一失败被原样入库的行。
     * <b>因此 NEVER 删掉本方法</b> —— 它不是冗余，是对「这一列里仍可能出现非 14 位值」这一事实的兜底。
     */
    private String normalizePayOrderNoDate(String transTime) {
        if (!StringUtils.hasText(transTime)) {
            return null;
        }
        String digits = transTime.replaceAll("\\D", "");
        if (digits.length() >= PAY_ORDER_NO_DATE_LENGTH) {
            return digits.substring(0, PAY_ORDER_NO_DATE_LENGTH);
        }
        log.warn("支付时间无法归一成 {} 位，原样返回, transTime={}", PAY_ORDER_NO_DATE_LENGTH, transTime);
        return transTime;
    }

    /**
     * 扣款结果只有 {@code "0"}（成功）/ {@code "1"}（其余）两个取值，NEVER 放渠道文案。
     *
     * <p><b>入参 MUST 是 {@code GATE_TXN_PAY.DEBIT_STATUS}</b>（实测值域 SUCCESS / RETRY / FAIL / INIT）——
     * 订单整体是否扣费成功以主表为准，与 {@code TransRecordAssembler.toAppDebitResult}（IF8A-05 交易列表）
     * 和 {@code ALIPAY_PAY_TXN_DETAIL.PAY_STATUS} 的列注释同一口径。
     * <b>NEVER 改回「有支付明细就用 PAY_STATUS、没有才回落 DEBIT_STATUS」</b>：
     * 支付明细一笔一行、记的是某一次支付尝试的结果，重试成功后主表已 SUCCESS 而旧明细行仍可能 FAIL，
     * 那种写法会把已扣费成功的行对 APP 报成未成功（2026-09-18 实测库内已有 2 行
     * {@code DEBIT_STATUS=RETRY} 而 {@code PAY_STATUS=FAIL} 的组合）。
     */
    private String mapDebitStatusToResult(String debitStatus) {
        return "SUCCESS".equalsIgnoreCase(debitStatus) ? "0" : "1";
    }
}
