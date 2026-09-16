package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import com.chinasofti.huateng.transquery.constant.TransQueryErrorCodeEnum;
import com.chinasofti.huateng.transquery.merchant.MerchantParty;
import com.chinasofti.huateng.transquery.merchant.MerchantPartyResolver;
import com.chinasofti.huateng.transquery.station.StationNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF8A-34 订单详情查询。
 *
 * <p>2026-09-14 从 ticket-server 的 {@code query/TransQueryHandler}（626 行，已随 r770 删除）拆出，
 * 与 IF8A-05 / IF8A-41 互不调用。
 * 同批删掉两段死代码：空实现的 {@code enrichTradeOrderNos(List)} 与无任何调用点的
 * {@code mergeTransRecord(List)} + {@code setNonNull}。<b>NEVER 加回</b> —— 详情已按
 * {@code orderNo} 直查，不存在「合并进出站两条记录」的场景。</p>
 *
 * <p>数据来源：{@code GATE_TXN_PAY}（进出站 / 商户 / 金额）+ {@code PAY_TXN_DETAIL}（支付明细），
 * 均经 RPC。</p>
 *
 * <p><b>2026-09-14：字段映射改为复用 {@link TransRecordAssembler#assemble}，删掉本类原有的
 * {@code copyGateFields}（23 个 setter）+ {@code copyPayFields}。</b>
 * 原先「同一份 GATE_TXN_PAY + PAY_TXN_DETAIL → TransRecordDTO 映射」在列表侧与详情侧各存一份，
 * 而两侧口径本来就要求逐字段一致（本文件与 assembler 的注释都在反复强调这件事），
 * 靠两段代码各自维护 = 上游加列时改一处漏一处、运行时静默 null。
 * <b>NEVER 再在本类里写第二份字段拷贝</b>；详情独有的加工只允许写成 assemble 之后的 enrich
 * （站名 {@code enrichSingleStationNames}、商户号兜底 {@code resolveMerchantParties}）。</p>
 *
 * <p>复用后有三处行为变化，均为向列表侧口径收敛、且都是修正：
 * ① {@code payAmount} 在 {@code TOTAL_AMOUNT} 为 null 时原先输出字面量字符串 {@code "null"}
 * （{@code String.valueOf(null Integer)}），现在输出 null；
 * ② {@code cardNum} / {@code payChannelCode} 等字符串字段缺失时由 null 变空串（Fastjson2 会丢掉 null
 * 字段，前端当成「后端没这个字段」，空串才与列表侧一致）；
 * ③ {@code entryStationName} / {@code exitStationName} 落库为空时会回落到站点编码
 * {@code IN_STATION} / {@code OUT_STATION}，随后被 {@code enrichSingleStationNames} 解析成真站名
 * （原先为空就一直空）。</p>
 */
@Component
public class TransDetailQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(TransDetailQueryHandler.class);

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Autowired
    private PaySignClient paySignClient;

    @Autowired
    private StationNameResolver stationNameResolver;

    @Autowired
    private MerchantPartyResolver merchantResolver;

    @Autowired
    private DailyTicketClient dailyTicketClient;

    /**
     * 获取订单详情 (IF8A-34)。
     */
    public RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request) {
        RequestTransDetailResult response = new RequestTransDetailResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())
                    || !StringUtils.hasText(request.getOrderNo())) {
                response.setRetCode(TransQueryErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("thirdUserId和orderNo不能为空");
                return response;
            }

            GateTxnPayListDTO gateRecord = gateTxnPayClient.queryByOrderNo(request.getOrderNo());
            if (gateRecord == null) {
                response.setRetCode(TransQueryErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg(TransQueryErrorCodeEnum.NO_DATA.getMsg());
                return response;
            }

            // 校验订单归属，防止越权查询。**NEVER 删这一段**：orderNo 可枚举，缺了它任何人都能按
            // 单号查任意用户的行程与金额。
            if (!StringUtils.hasText(gateRecord.getThirdUserId())
                    || !gateRecord.getThirdUserId().equals(request.getThirdUserId())) {
                response.setRetCode(TransQueryErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg("订单不存在或不属于该用户");
                return response;
            }

            TransRecordDTO record = TransRecordAssembler.assemble(
                    gateRecord, queryPayDetail(request.getOrderNo()));
            enrichSingleStationNames(record);
            resolveMerchantParties(record);
            enrichDailyTicketPayInfo(record);

            response.setRetCode(TransQueryErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TransQueryErrorCodeEnum.SUCCESS.getMsg());
            response.setTicketTransRecord(record);
        } catch (Exception e) {
            log.error("IF8A-34 获取订单详情异常, request={}", request, e);
            response.setRetCode(TransQueryErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TransQueryErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    private PayTxnDetailDTO queryPayDetail(String orderNo) {
        QueryPayTxnBatchReqDTO batchReq = new QueryPayTxnBatchReqDTO();
        batchReq.setOrderNos(Collections.singletonList(orderNo));
        RequestPayTxnBatchResult batchResult = paySignClient.queryPayTxnBatch(batchReq);
        List<PayTxnDetailDTO> payDetails = batchResult != null ? batchResult.getPayTxnDetailList() : null;
        if (CollectionUtils.isEmpty(payDetails)) {
            return null;
        }
        for (PayTxnDetailDTO detail : payDetails) {
            if (detail != null && orderNo.equals(detail.getOrderNo())) {
                return detail;
            }
        }
        return null;
    }

    /**
     * 站名解析（详情场景，只有进出站两个编码）。
     *
     * <p>2026-09-14 顺手删掉一行「取了返回值却不赋值」的死语句（原
     * {@code stationNameMap.getOrDefault(...)} 单独成句），行为不变。</p>
     */
    private void enrichSingleStationNames(TransRecordDTO record) {
        if (StringUtils.hasText(record.getEntryStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(record.getEntryStationName()));
            Map<String, String> stationNameMap = stationNameResolver.resolveStationNames(codes);
            record.setEntryStationName(
                    stationNameMap.getOrDefault(record.getEntryStationName(), record.getEntryStationName()));
        }
        if (StringUtils.hasText(record.getExitStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(record.getExitStationName()));
            Map<String, String> stationNameMap = stationNameResolver.resolveStationNames(codes);
            record.setExitStationName(
                    stationNameMap.getOrDefault(record.getExitStationName(), record.getExitStationName()));
        }
    }

    /**
     * 补齐商户号：仅当 {@code GATE_TXN_PAY} 未落库时才按变更日期兜底。
     *
     * <p>{@code GATE_TXN_PAY.ATTRIBUTABLE_PARTY / RECEIVING_PARTY} 是权威值，
     * 由 {@code gate/GateResponseAssembler} 在 IF1A-01 闸机检票应答时按「单边/补站回退城交」
     * 等业务规则算好并随订单落库。<b>NEVER 用 merchant-change-date 覆盖已落库的值</b>：
     * 本方法的乘车日期取自 {@code entryDate}，而 {@code IN_TIME} 存在配对错误的历史数据
     * （订单 GT20260904140544263917011：库内 IN_TIME=20260820162936、OUT_TIME=20260904140544，跨 15 天），
     * 一旦覆盖就会把「进站时间错」放大成「资金归属方错」——该笔库内 ATTRIBUTABLE_PARTY=qddt，
     * 被旧逻辑按 20260820 &lt; 20260901 判成老商户、改写成 cjdsj（2026-09-07 修复）。
     *
     * <p>同时这也是与列表侧对齐：IF8A-05 的 {@link TransRecordAssembler#assemble} 一直是直接取库内值、
     * 不做覆盖，旧逻辑让同一笔订单在列表与详情返回不同商户号。</p>
     */
    private void resolveMerchantParties(TransRecordDTO record) {
        boolean attributableMissing = !StringUtils.hasText(record.getAttributableParty());
        boolean receivingMissing = !StringUtils.hasText(record.getReceivingParty());
        if (!attributableMissing && !receivingMissing) {
            return;
        }

        String rideDate = null;
        if (StringUtils.hasText(record.getEntryDate()) && record.getEntryDate().length() >= 8) {
            rideDate = record.getEntryDate().substring(0, 8);
        } else if (StringUtils.hasText(record.getExitDate()) && record.getExitDate().length() >= 8) {
            rideDate = record.getExitDate().substring(0, 8);
        }

        MerchantParty fallback = merchantResolver.resolveFor(rideDate);

        if (attributableMissing && StringUtils.hasText(fallback.attributableParty())) {
            record.setAttributableParty(fallback.attributableParty());
        }
        if (receivingMissing && StringUtils.hasText(fallback.receivingParty())) {
            record.setReceivingParty(fallback.receivingParty());
        }
        log.info("IF8A-34 商户号兜底, orderNo={}, rideDate={}, useOld={}, attributableParty={}, receivingParty={}",
                record.getTradeOrderNo(), rideDate, fallback.useOld(),
                record.getAttributableParty(), record.getReceivingParty());
    }

    /**
     * 日票免扣费单补三个支付字段：{@code payTradeOrderNo} / {@code payOrderNoDate} / {@code payChannelCode}。
     *
     * <p>日票过闸不产生 {@code PAY_TXN_DETAIL}，于是 {@link TransRecordAssembler#assemble} 里这三个字段
     * 恒为空串（2026-09-14 端到端实测：免扣费单 GT20260914180952303000039 三个字段都是 {@code ""}）。
     * 本方法按 {@code GATE_TXN_PAY.TICKET_CODE} 回溯到购票订单 {@code DAILY_TICKET_ORDER} 补上。</p>
     *
     * <p><b>{@code payOrderNoDate} 填的是「购票付款时刻」，不是本次过闸时刻</b>——长周期日票会显示成
     * 很早的时间，这是**有意为之**（用户 2026-09-15 确认口径）。同样有意为之的是**无条件填**：
     * 不加「购票日 == 乘车日才填」的条件，那会制造一个说不清的半填状态。</p>
     *
     * <p><b>本方法内 MUST catch 全部异常只记日志</b>：三个支付字段是详情的补充信息，
     * daily-ticket-server 不可用时详情本体（行程、金额、商户号）仍然有效，
     * 而 {@code DailyTicketClient} 是**不吞异常**的，不接住就会把整条 IF8A-34 打成 9999。</p>
     */
    private void enrichDailyTicketPayInfo(TransRecordDTO record) {
        if (!StringUtils.hasText(record.getTicketCode())) {
            return;
        }
        if (StringUtils.hasText(record.getPayTradeOrderNo())) {
            return;
        }
        try {
            QueryDailyTicketPayInfoReqDTO req = new QueryDailyTicketPayInfoReqDTO();
            req.setTicketCode(record.getTicketCode());
            QueryDailyTicketPayInfoResult payInfo = dailyTicketClient.queryDailyTicketPayInfo(req);
            if (payInfo == null) {
                log.warn("IF8A-34 日票支付信息为空响应, orderNo={}, ticketCode={}",
                        record.getTradeOrderNo(), record.getTicketCode());
                return;
            }
            if (StringUtils.hasText(payInfo.getPayTradeOrderNo())) {
                record.setPayTradeOrderNo(payInfo.getPayTradeOrderNo());
            }
            if (StringUtils.hasText(payInfo.getPayOrderNoDate())) {
                record.setPayOrderNoDate(payInfo.getPayOrderNoDate());
            }
            if (StringUtils.hasText(payInfo.getPayChannelCode())) {
                record.setPayChannelCode(payInfo.getPayChannelCode());
            }
            log.info("IF8A-34 日票支付信息已补齐, orderNo={}, ticketCode={}, payTradeOrderNo={}, payOrderNoDate={}, payChannelCode={}",
                    record.getTradeOrderNo(), record.getTicketCode(), record.getPayTradeOrderNo(),
                    record.getPayOrderNoDate(), record.getPayChannelCode());
        } catch (Exception e) {
            log.error("IF8A-34 日票支付信息补齐失败（详情本体不受影响）, orderNo={}, ticketCode={}",
                    record.getTradeOrderNo(), record.getTicketCode(), e);
        }
    }
}
