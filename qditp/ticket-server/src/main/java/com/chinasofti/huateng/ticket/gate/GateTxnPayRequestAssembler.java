package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.ticket.station.StationNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 出站扣费入参 {@link GateTxnPayReqDTO} 的组装：设备报文字段映射 + ticket-server 响应透传 + 中文站名回填。
 *
 * <p>2026-09-14 从 {@code fep-dev-server} 的 {@code fep.dev.gate} 包整体迁入（ADR-D62），
 * 字段映射与判断逐行未改。迁移理由：这里的入参有一半来自 ticket-server 自己的响应
 * （{@code applyTicketResponse} 透传的 12 个字段），留在接入层等于把这 12 个字段跨进程传出去再传回来。</p>
 *
 * <p>2026-09-14 后续（ADR-D66）：站名查询**不再自带一份**，改注 {@link StationNameResolver}。
 * 迁入时本类带来的 {@code fetchStationNames} 与 {@code station/} 下那份**逐字同形**
 * （同一个 {@code requestStationNameBatch}、同一个 {@code "0000"} 判定、失败都返空 Map），
 * 而后者已被 {@code query} / {@code alipay} / {@code ridestatus} 三方共用。
 * <b>NEVER 在本类再写一份站名查询</b>；依赖方向 {@code gate → station} 是允许的
 * （{@code station/} 只依赖 {@code rpc} / {@code model} / {@code mapper}，不反向依赖业务包）。</p>
 *
 * <p><b>但 {@code AlipayIndustryDetailAssembler.queryStationLineInfo} NEVER 一起合并</b> ——
 * 那里要 {@code lineCode} / {@code lineName}，批量 SQL {@code selectStationNameBatch} 不返回线路字段。</p>
 *
 * <p>本类<b>只负责组装、不负责发送</b>：{@code gateTxnPayClient.requestGateTxnPay} 的调用与
 * 异常兜底在 {@link GateFarePaymentOrchestrator}，避免「组装失败」与「远端失败」两类问题共用一个 catch。</p>
 */
@Component
class GateTxnPayRequestAssembler {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayRequestAssembler.class);

    private final StationNameResolver stationNameResolver;

    public GateTxnPayRequestAssembler(StationNameResolver stationNameResolver) {
        this.stationNameResolver = stationNameResolver;
    }
    /**
     * 按设备报文 + ticket-server 响应组装扣费入参，并回填进出站中文站名。
     *
     * <p>2026-09-14：设备报文那 20 个继承字段的搬运已收口到
     * {@link GateTxnPayReqDTO#fromVerifyResult}（那是 DTO 自己的知识），本方法只留**业务规则**：
     * 透传 ticket-server 响应、赋 industryDetail、回填中文站名。
     * <b>NEVER 把那 20 行 setter 抄回本类</b>；同样 <b>NEVER 把下面这三段挪进 DTO</b> ——
     * 它们各自依赖 ticket-server 响应与 para-server 查询，不是「字段搬运」。</p>
     *
     * @param industryDetail 支付宝出行的 21 键行业明细，仅 issueChannelCode=07 有值；非 07 传 null
     */
    public GateTxnPayReqDTO assemble(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse,
                                    String industryDetail) {
        GateTxnPayReqDTO payRequest = GateTxnPayReqDTO.fromVerifyResult(request);
        // 支付宝出行的 21 键行业明细，仅 issueChannelCode=07 有值；非 07 恒为 null。
        // 只在这里赋值一次，下游存进 GATE_TXN_PAY.INDUSTRY_DETAIL、扣费与重试都复用同一份。
        payRequest.setIndustryDetail(industryDetail);
        applyTicketResponse(payRequest, request, ticketResponse);
        // 补充进出站中文站名（由 para-server 查询）
        fillStationNames(payRequest, request.getLastHandleStationCode(), request.getHandleStationCode());
        log.info("IF1A-01 扣费请求关键字段, cardId={}, ticketStatus={}, orderExpType={}, offlineFlag={}, entryStationName={}, exitStationName={}",
                request.getCardId(), payRequest.getTicketStatus(), payRequest.getOrderExpType(),
                payRequest.getOfflineFlag(), payRequest.getEntryStationName(), payRequest.getExitStationName());
        return payRequest;
    }
    /**
     * 透传 ticket-server 返回的票卡状态、订单异常类型、离线码标识、日票相关字段与签约信息。
     */
    private void applyTicketResponse(GateTxnPayReqDTO payRequest, NotifyVerifyResultReqDTO request,
                                    NotifyVerifyResultRespDTO ticketResponse) {
        if (ticketResponse == null) {
            log.warn("IF1A-01 ticketResponse 为null，"
                            + "ticketStatus/orderExpType/offlineFlag/payChannelCode/requestSignSeq 将为null, cardId={}",
                    request.getCardId());
            return;
        }
        payRequest.setTicketStatus(ticketResponse.getTicketStatus());
        payRequest.setOrderExpType(ticketResponse.getOrderExpType());
        payRequest.setOfflineFlag(ticketResponse.getOfflineFlag());
        payRequest.setTicketCode(ticketResponse.getTicketCode());
        payRequest.setCountingTimes(ticketResponse.getCountingTimes());
        payRequest.setCountingFlag(ticketResponse.getCountingFlag());
        payRequest.setAttributableParty(ticketResponse.getAttributableParty());
        payRequest.setReceivingParty(ticketResponse.getReceivingParty());
        payRequest.setPayChannelCode(ticketResponse.getPayChannelCode());
        // 签约信息 MUST 从 ticketResponse 取，NEVER 从 request 取。
        // 迁入同进程（ADR-D62）后理由变了，逐行核对 GateCardTypeEnricher 的赋值面得到：
        //   payUserId       —— 只写 response，request 上根本没有被赋值过 ⇒ response 是唯一来源；
        //   requestSignSeq  —— request / response 都写，取哪边都一样，取 response 与上一行统一；
        //   paymentVendor   —— 只写 request，response 上没有这个字段。
        // 所以本方法的 paymentVendor 不是「透传 response 的 paymentVendor」，而是复用
        // payChannelCode（ticket-server 侧两者同源于 USER_ITP_REG_INFO.CHANNEL）。
        // 这里留了一个漂移口子：GateCardTypeEnricher 写 request.paymentVendor 用的是自己那份取值，
        // 本方法用的是 response.payChannelCode，两者哪天不同源就会静默分叉。
        // 修法只有一个方向 —— 让扣费入参也从 request.getPaymentVendor() 取，
        // NEVER 在响应体里加同义字段（那是对外契约，见 docs/domain §对外接口判据）。
        // 已发生事故：2026-08-26 免密扣款链路 paymentVendor/requestSignSeq 全程为 null，
        // 只能由 pay-sign-server 回查 account-server 兜底，且 gate-txn-pay-server 里
        // 依赖 paymentVendor='0B' 的钱包优惠与地铁换乘推送分支一直不触发。
        if (StringUtils.hasText(ticketResponse.getPayChannelCode())) {
            payRequest.setPaymentVendor(ticketResponse.getPayChannelCode());
        }
        if (StringUtils.hasText(ticketResponse.getRequestSignSeq())) {
            payRequest.setRequestSignSeq(ticketResponse.getRequestSignSeq());
        }
        if (StringUtils.hasText(ticketResponse.getPayUserId())) {
            payRequest.setPayUserId(ticketResponse.getPayUserId());
        }
        // NEVER 在这里打 ticketResponse 的 discountFee / discountInfo：
        // ticket-server 的 IF1A-01 响应从不给这两个字段赋值（它只在 APP 查询路径里
        // 从 PAY_TXN_DETAIL 反读，见 TransQueryHandler / TransRecordAssembler），
        // 打出来恒为 null，会让人误以为扣费方向上有折扣数据在流动。
        // 折扣是 gate-txn-pay 自算的（para 票价 + 钱包累计 + DISCOUNT_LEVEL 档位，
        // 且仅 paymentVendor='0B' 才进分支），不由本链路上送。
        log.info("IF1A-01 透传 ticketResponse 字段到 payRequest, cardId={}, "
                        + "ticketStatus={}, orderExpType={}, offlineFlag={}, "
                        + "companionFlag={}, ticketCode={}, countingTimes={}, "
                        + "countingFlag={}, attributableParty={}, receivingParty={}, "
                        + "payChannelCode={}, requestSignSeq={}",
                request.getCardId(), ticketResponse.getTicketStatus(),
                ticketResponse.getOrderExpType(), ticketResponse.getOfflineFlag(),
                ticketResponse.getCompanionFlag(), ticketResponse.getTicketCode(),
                ticketResponse.getCountingTimes(), ticketResponse.getCountingFlag(),
                ticketResponse.getAttributableParty(), ticketResponse.getReceivingParty(),
                ticketResponse.getPayChannelCode(),
                ticketResponse.getRequestSignSeq());
    }
    /**
     * 根据进出站编码查询中文站名并写入扣费请求。
     * <p>站名查询委托给 {@link StationNameResolver}（ADR-D66），它内部一次批量查回进出站两个站名，
     * 并且已被 {@code query} / {@code alipay} / {@code ridestatus} 三方共用。
     * <b>NEVER 在本方法内再写一份 RPC 调用</b>。</p>
     * <p><b>NEVER 把 {@code AlipayIndustryDetailAssembler.buildStationInfoMap} 也改成批量</b>
     * ——那里要 lineCode / lineName，批量 SQL（{@code AppParaQueryMapper.selectStationNameBatch}）
     * 不返回线路字段。</p>
     * <p>2026-09-15（ADR-D83 续，用户点头后落地）：<b>进出站站名各自独立回填，进站码为空不再连出站站名一起丢</b>。
     * 原语义是「{@code entryStationCode} 为空即整体 return」，于是**出站码明明有值、名字本可查到，却因为进站码为空
     * 而一起丢掉** —— 离线码进站报文未上传时进站码就是空或占位 {@code FFFF}，正是这条语义把
     * {@code EXIT_STATION_NAME} 也一并置空，列表侧只能回落显示站点编码。这是行为变更、影响原本为 null 的列，
     * 因此单独一批做。<b>NEVER 回退成「进站码为空即整体 return」。</b></p>
     * <p>本方法现在只是**首值与兜底**：这两列的 owner 自 ADR-D83 起是 gate-txn-pay-server 的
     * {@code StationNameBackfiller}（它在算价拿到权威进站码之后覆盖一次）。因此本方法写错或写不全都能被下游纠正，
     * 但 <b>NEVER 因此把本方法删掉</b> —— 非离线码路径不走重算，这里就是唯一写入点。</p>
     */
    private void fillStationNames(GateTxnPayReqDTO payRequest, String entryStationCode, String exitStationCode) {
        // MUST 用 LinkedHashSet 逐个 add，NEVER 用 Set.of(entryStationCode, exitStationCode)：
        // 进出同站（乘客同站进出、AGM 上送两个相同站码）时 Set.of 对重复元素直接抛
        // IllegalArgumentException，且它还拒绝 null（两个码都可能为 null）。
        // 去重责任由这个 Set 承担 —— 原实现是靠 Arrays.stream().distinct() 做的。
        Set<String> stationCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(entryStationCode)) {
            stationCodes.add(entryStationCode);
        }
        if (StringUtils.hasText(exitStationCode)) {
            stationCodes.add(exitStationCode);
        }
        if (stationCodes.isEmpty()) {
            return;
        }
        Map<String, String> stationNames = stationNameResolver.resolveStationNames(stationCodes);
        String entryName = stationNames.get(entryStationCode);
        if (StringUtils.hasText(entryName)) {
            payRequest.setEntryStationName(entryName);
        }
        String exitName = stationNames.get(exitStationCode);
        if (StringUtils.hasText(exitName)) {
            payRequest.setExitStationName(exitName);
        }
    }
}
