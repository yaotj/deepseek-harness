package com.chinasofti.huateng.ticket.gate;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entrytxn.EntryTxnQueryService;
import com.chinasofti.huateng.ticket.station.StationLineResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 支付宝出行 21 键 {@code industryDetail} 组装。
 *
 * <p>2026-09-14 从 {@code fep-dev-server} 的 {@code fep.dev.gate} 包整体迁入，**方法体逐行未改**，
 * 只换了两处依赖：</p>
 * <ul>
 *   <li>{@code rpc.ticket.TicketClient#queryEntryDevice} 改为进程内的
 *       {@link EntryTxnQueryService#queryEntryDevice} —— 迁入后那是自己调自己，而且
 *       ticket-server 的启动类**没有 {@code @EnableRpcTicket}**、{@code TicketClient} bean
 *       根本不存在，照搬会启动失败（编译能过、启动才炸）。<b>NEVER 改回 RPC 形态。</b></li>
 *   <li>线程池改注 {@code ticket.config.TicketAsyncConfig} 的 {@code industryDetailExecutor}
 *       （原 {@code FepDevExecutorConfig} 已随迁移删除）。</li>
 * </ul>
 *
 * <p>迁移理由：这 21 键里有 9 个（进出站线路码 / 名称、进站设备号、entryId / exitId、cardNum、
 * cardIssueCode）在 {@code GATE_TXN_PAY} 没有对应列、只有出站这一刻拿得到，而它依赖的三类数据
 * （本次报文 + para 线路信息 + 本库进站明细）**全部在 ticket-server 侧**。留在接入层等于让设备前置
 * 去认识支付宝的报文规格，并且为了拿进站设备号还要反向 RPC 回 ticket-server。</p>
 *
 * <p>支付宝出行的卡机构编号 {@code alipay.trip.card-issue-code} 只用于 {@code cardIssueCode} 键。
 * 其余 {@code alipay.trip.*} 配置（scene / vendor / subject / body / order.timeout.minutes）与
 * {@code pay.center.callback-url} 已随「直连支付宝扣费」一起下沉到 gate-txn-pay-server，
 * 本类 <b>NEVER 再注入 AlipayPaySignClient</b> —— 出账口只有 PaySignInitiator 一处。</p>
 */
@Component
class AlipayIndustryDetailAssembler {
    private static final Logger log = LoggerFactory.getLogger(AlipayIndustryDetailAssembler.class);

    private static final String ENTRY_ID_SUFFIX = "01";
    private static final String EXIT_ID_SUFFIX = "02";

    private final EntryTxnQueryService entryTxnQueryService;
    private final StationLineResolver stationLineResolver;
    private final String alipayCardIssueCode;
    private final Executor industryDetailExecutor;

    public AlipayIndustryDetailAssembler(
            EntryTxnQueryService entryTxnQueryService,
            StationLineResolver stationLineResolver,
            @Value("${alipay.trip.card-issue-code:0007}") String alipayCardIssueCode,
            @Qualifier("industryDetailExecutor") Executor industryDetailExecutor) {
        this.entryTxnQueryService = entryTxnQueryService;
        this.stationLineResolver = stationLineResolver;
        this.alipayCardIssueCode = alipayCardIssueCode;
        this.industryDetailExecutor = industryDetailExecutor;
    }
    /**
     * 组装支付宝出行的 21 键 {@code industryDetail}，整块交给 gate-txn-pay-server 落单存下。
     *
     * <p><b>这是这 21 键唯一的产生点。</b>其中 9 个（进出站线路码 / 名称、进站设备号、
     * entryId / exitId、cardNum、cardIssueCode）在 `GATE_TXN_PAY` 没有对应列，只有出站这一刻
     * 靠这里的两个并行 RPC + 本次报文才拿得到；**NEVER 在下游按订单字段重算** —— 重算出来的
     * 键名与值都与支付宝要的不一致，扣费会被判成行程解析失败。</p>
     *
     * <p>失败时返回 null 而**不是**中断落单：订单先落下来，行程在 APP、对账与欠费判定里都看得见，
     * 这笔的扣费会收敛成 RETRY 等人工介入；反过来「组不出明细就不落单」会让整趟行程凭据消失，
     * 那是更坏的一种失败。注意 {@code queryStationLineInfo} / {@code applyEntryDeviceCode}
     * 各自已有兜底，走到本方法 catch 的只剩非预期异常。</p>
     *
     * <p><b>2026-09-14：三个 RPC 拍平成同一层并行，NEVER 退回「外层 supplyAsync 里再
     * supplyAsync + join」的嵌套写法。</b>原写法在 {@code ForkJoinPool.commonPool} 上侥幸不死，
     * 换成本类专属的<b>有界</b>线程池后就是**必然死锁**：外层任务占着一个线程阻塞等两个内层任务，
     * 并发一上来池子被外层任务占满、内层永远排不到线程。并行度与取值与原实现完全一致
     * （仍是三个 RPC 同时发），唯一差别是 {@code orderDate} 现在取自调用线程而非异步线程，
     * 相差在毫秒级、该键是「订单日期」到秒，无影响。</p>
     */
    public String assemble(NotifyVerifyResultReqDTO request) {
        try {
            String entryStationCode = request.getLastHandleStationCode();
            String exitStationCode = request.getHandleStationCode();
            // 三个独立 RPC 同层并行：进站设备号、进站线路信息、出站线路信息
            CompletableFuture<String> entryDeviceFuture =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return entryTxnQueryService.queryEntryDevice(request.getCardId());
                        } catch (RuntimeException e) {
                            log.warn("查询进站设备异常, cardId={}", request.getCardId(), e);
                            return null;
                        }
                    }, industryDetailExecutor);
            CompletableFuture<RequestStationLineInfoResult> entryStationFuture =
                    CompletableFuture.supplyAsync(() -> queryStationLineInfo(entryStationCode), industryDetailExecutor);
            CompletableFuture<RequestStationLineInfoResult> exitStationFuture =
                    CompletableFuture.supplyAsync(() -> queryStationLineInfo(exitStationCode), industryDetailExecutor);

            Map<String, Object> stationInfo = buildStationInfoMap(
                    request, entryStationFuture.join(), exitStationFuture.join());
            applyEntryDeviceCode(stationInfo, entryDeviceFuture.join(), request.getCardId());
            stationInfo.put("cardIssueCode", alipayCardIssueCode);

            String industryDetail = JSON.toJSONString(stationInfo);
            log.info("IF1A-01 支付宝出行行业明细已组装, cardId={}, industryDetail={}",
                    request.getCardId(), industryDetail);
            return industryDetail;
        } catch (Exception e) {
            log.error("IF1A-01 支付宝出行行业明细组装异常，本笔仍落单但扣费会落 RETRY, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
            return null;
        }
    }
    /**
     * 把已查回的进出站线路信息合并为支付宝 industryDetail Map。
     * entryDate 从 lastHandleDateTime 转换，exitDate 从 handleDateTime 转换；
     * entryId/exitId 由各辅助方法构建；orderExpType 根据 trxType 是否为超时出站决定。
     *
     * <p>本方法<b>不再自己发 RPC</b>（原先内部还起两个 supplyAsync），两个线路信息由
     * {@link #assemble} 在同一层并行查好后传入 —— 见该方法注释里的死锁说明。</p>
     */
    private Map<String, Object> buildStationInfoMap(NotifyVerifyResultReqDTO request,
                                                    RequestStationLineInfoResult entryStationInfo,
                                                    RequestStationLineInfoResult exitStationInfo) {
        String entryStationCode = request.getLastHandleStationCode();
        String exitStationCode = request.getHandleStationCode();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        detail.put("cardNum", blankIfNull(request.getCardId()));
        detail.put("channelAgreementNo", ""); // 占位保序，真值由 alipay-pay-sign-server 的 BizDataBuilder 注入
        detail.put("tikcetTransSeq", blankIfNull(request.getTicketTransSeq()));

        detail.put("entryLineCode", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineCode, entryStationCode)));
        detail.put("entryLineName", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineName, entryStationCode)));
        detail.put("entryStationCode", blankIfNull(entryStationCode));
        detail.put("entryStationName", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getStationName, entryStationCode)));
        detail.put("entryDeviceCode", ""); // 占位保序，真值由 applyEntryDeviceCode 注入
        detail.put("entryDate", blankIfNull(convertHandleDateTime(request.getLastHandleDateTime())));
        detail.put("entryId", blankIfNull(buildTripId(request.getItpUserId(), request.getLastHandleDateTime(), ENTRY_ID_SUFFIX)));

        detail.put("exitId", blankIfNull(buildTripId(request.getItpUserId(), request.getHandleDateTime(), EXIT_ID_SUFFIX)));
        detail.put("exitLineCode", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineCode, exitStationCode)));
        detail.put("exitLineName", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineName, exitStationCode)));
        detail.put("exitStationCode", blankIfNull(exitStationCode));
        detail.put("exitStationName", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getStationName, exitStationCode)));
        detail.put("exitDeviceCode", blankIfNull(request.getDeviceId()));
        detail.put("exitDate", blankIfNull(convertHandleDateTime(request.getHandleDateTime())));

        detail.put("orderExpType", "03".equals(request.getTrxType()) ? "5" : "0");
        detail.put("fineAmount", blankIfNull(request.getOvertimeAmount()));
        return detail;
    }
    /**
     * industryDetail 的字段值 MUST 用空串兜底，NEVER 留 null。
     * Fastjson2 默认丢弃 null 值字段，支付中心侧会直接看不到该 key，
     * 排查时极易误判成对端丢字段（2026-09-11 的 entryDeviceCode / cardIssueCode 就是这么漏的）。
     */
    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * 注入进站设备号。ticket-server 查不到时保持空串（不是 null，见 {@link #blankIfNull}），
     * 字段仍在报文里，同时打 warn 留痕。
     */
    private void applyEntryDeviceCode(Map<String, Object> stationInfo, String entryDeviceCode, String cardId) {
        if (StringUtils.hasText(entryDeviceCode)) {
            stationInfo.put("entryDeviceCode", entryDeviceCode.trim());
            return;
        }
        log.warn("IF1A-01 未取到进站设备号，industryDetail 的 entryDeviceCode 置空串, cardId={}", cardId);
    }

    private <T> String getOrFallback(T info, Function<T, String> getter, String fallback) {
        if (info == null) return fallback;
        String value = getter.apply(info);
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 查询车站线路信息：stationCode 为空时直接返回 null；调用失败时
     * {@link StationLineResolver} 内部记 warn 并返回 null，由调用方用 stationCode 自身兜底。
     *
     * <p>2026-09-14 RPC 组装下沉到 {@code station/StationLineResolver}（此前 gate / supplement /
     * notify 三处各写一遍）。**本方法刻意不判 {@code retCode}，保持改造前的行为** ——
     * 拿到什么用什么，只在 {@code getLineCode()} / {@code getLineName()} 为 null 时才回落站码。
     * 要改成判 retCode MUST 单独一笔 + 端到端复测，见 {@link StationLineResolver} 类注释。
     *
     * <p><b>NEVER 把本方法改成批量接口</b>——这里要 lineCode / lineName，而批量 SQL
     * （{@code AppParaQueryMapper.selectStationNameBatch}）不返回线路字段。
     * {@code GateTxnPayRequestAssembler} 那边只要站名，才用得了批量。</p>
     */
    private RequestStationLineInfoResult queryStationLineInfo(String stationCode) {
        return stationLineResolver.resolveLineInfo(stationCode);
    }
    /**
     * 将设备上报的时间字符串（yyyyMMddHHmmss...）转换为友好格式（yyyy-MM-dd HH:mm:ss）。
     * 长度不足14位或为 null 时原样返回，不抛异常。
     */
    private String convertHandleDateTime(String handleDateTime) {
        if (handleDateTime == null || handleDateTime.length() < 14) {
            return handleDateTime;
        }
        return handleDateTime.substring(0, 4) + "-" +
                handleDateTime.substring(4, 6) + "-" +
                handleDateTime.substring(6, 8) + " " +
                handleDateTime.substring(8, 10) + ":" +
                handleDateTime.substring(10, 12) + ":" +
                handleDateTime.substring(12, 14);
    }

    /**
     * 构建进站/出站唯一标识：itpUserId（已转十进制） + 紧凑日期（yyyyMMddHHmmss） + 后缀(01/02)。
     * 日期部分为空时不使用，保证ID至少由用户ID+后缀构成。
     */
    private String buildTripId(String itpUserId, String handleDateTime, String suffix) {
        String dateCompact = compactDate(convertHandleDateTime(handleDateTime));
        return itpUserId + (StringUtils.hasText(dateCompact) ? dateCompact : "") + suffix;
    }

    /**
     * 从格式化日期字符串中抽离纯数字，取前14位（yyyyMMddHHmmss），用于拼接 tripId。
     */
    private String compactDate(String date) {
        if (!StringUtils.hasText(date)) {
            return "";
        }
        String compact = date.replaceAll("[^0-9]", "");
        return compact.length() > 14 ? compact.substring(0, 14) : compact;
    }
}
