package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * `GATE_TXN_PAY` 的**只读查询侧**：运营分页、APP 交易记录 / 账单统计 / 订单详情 / 账务信息、
 * 以及解约与黑名单解除要用的未结清判定。
 *
 * <p>与 {@link GateTxnPayService} 的分界线是**有没有写**：本接口的每个方法都只 SELECT，
 * 不改任何状态、不调任何远端服务，因此实现类只需要 `GateTxnPayMapper` 一个协作者。
 * 出站扣费、补款、退款、状态收敛、补偿重算全部留在 {@code GateTxnPayService}。
 *
 * <p>**NEVER 往本接口加带写入或 RPC 的方法** —— 那条线一破，「查询侧可以随便重试、
 * 不必考虑幂等」这个前提就不再成立，而调用方（web-admin 分页、ticket-server、
 * blacklist-server、pay-sign-server 解约校验）都是按只读语义在用它。
 *
 * <p>方法签名与 javadoc 由 {@code GateTxnPayService} 原样搬来，**对外行为完全未变**：
 * 这些方法都不是 HTTP 端点，端点仍在原来的 controller 上，URL 与报文一个都没动。
 */
public interface GateTxnPayQueryService {
    ResultVO<Map<String, Object>> page(String orderNo, String cardId, String thirdUserId, String signChannelCode,
                                        String cardType, String debitStatus, String startDate, String endDate,
                                        Integer pageNum, Integer pageSize);

    /** 按交易业务键查询 GT 订单号（供 ticket-server 关联查询）。 */
    GateTxnPayRespDTO queryOrderByBizKey(GateTxnPayReqDTO request);

    // ==================== IF8A-05 APP 交易记录列表 ====================

    /**
     * 分页查询进出站交易记录（供 ticket-server RPC 调用）。
     *
     * @param debitRequestResult 扣款结果过滤：null 或空=全部，"0"=已扣款成功，"1"=未扣款成功
     */
    List<GateTxnPayListDTO> selectTransList(@Param("thirdUserId") String thirdUserId,
                                            @Param("cardIdList") List<String> cardIdList,
                                            @Param("cardType") String cardType,
                                            @Param("cardTypeList") List<String> cardTypeList,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("ticketCode") String ticketCode,
                                            @Param("debitRequestResult") String debitRequestResult,
                                            @Param("offset") Integer offset,
                                            @Param("limit") Integer limit);
    /**
     * 统计 IF8A-05 分页查询结果总数（供 ticket-server RPC 调用）。
     */
    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("cardTypeList") List<String> cardTypeList,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode,
                       @Param("debitRequestResult") String debitRequestResult);
    // ==================== IF8A-41 APP 账单统计 ====================

    /**
     * IF8A-41 账单统计（供 ticket-server RPC 调用）。
     *
     * <p>票种白名单与 {@code cardType → cardTypeList} 的展开仍由 ticket-server 侧完成，
     * 本方法只负责按 {@code cardTypeList} / {@code cardIdList} / {@code TXN_DATE} 区间聚合
     * {@code GATE_TXN_PAY}。四个金额的口径见
     * {@link com.chinasofti.huateng.model.app.TripDataDTO} 类注释。</p>
     *
     * <p>只读接口，不改任何数据。零行时四个金额补 "0.00"、count 为 0，NEVER 返回 null tripData。</p>
     */
    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);
    // ==================== IF8A-34 APP 订单详情 ====================

    /**
     * 按订单号查询交易记录（供 ticket-server RPC 调用）。
     */
    GateTxnPayListDTO selectByOrderNo(@Param("orderNo") String orderNo);
    /**
     * 查询用户指定支付渠道下是否存在未结清扣费订单（DEBIT_STATUS 非 SUCCESS）。
     * requestTime 可为空，为空时查全部历史；调用方 MUST 先判断 resultCode 再用 hasFailedOrder。
     */
    GateTxnPayFailedOrderRespDTO hasFailedOrder(@Param("thirdUserId") String thirdUserId,
                                                @Param("paymentVendor") String paymentVendor,
                                                @Param("requestTime") LocalDateTime requestTime);

    /**
     * 查询单张卡下是否存在未结清扣费订单（供 blacklist-server 盘点黑名单可解除性调用）。
     *
     * <p>结清口径与 {@link #hasFailedOrder} 一致，区别是按 CARD_ID、不带渠道、不带时间下限。
     * 调用方 MUST 先判断 resultCode 再用 hasUnsettled；查询未真正执行时
     * hasUnsettled 固定返回 true，避免调用方漏判后把「查不到」当成「已结清」。</p>
     */
    CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@Param("cardId") String cardId);

    /**
     * 综管台离线码交易统计：日期窗（yyyy-MM-dd，闭区间）必填，返回按车站分组列表与全窗汇总。
     *
     * <p>只读聚合 {@code OFFLINE_FLAG='Y'} 的行。日期窗会换算成 {@code yyyyMMdd} 命中
     * TXN_DATE 月分区裁剪，因此 NEVER 放开成可选。返回 map 固定含 {@code list} 与
     * {@code summary} 两个键，均不为 null。</p>
     */
    ResultVO<Map<String, Object>> offlineStats(String startDate, String endDate);

    /**
     * 综管台批量退超时罚金的圈单查询：日期窗（yyyy-MM-dd，闭区间）必填，按车站分页圈出候选单。
     *
     * <p>近似口径（{@code OVERTIME_AMOUNT>0 + ORDER_EXP_TYPE='1' + TICKET_STATUS='07' + 退款白名单}）
     * 只做候选粗筛，能否真退由批量退款里逐单单笔校验把关，本接口只读、不改状态。
     * 返回 map 固定含 {@code list} 与 {@code total} 两个键。</p>
     */
    ResultVO<Map<String, Object>> overtimeRefundablePage(String stationCode, String startDate, String endDate,
                                                         Integer pageNum, Integer pageSize);
    /**
     * IF8A-35 查询用户账务信息：返回未支付订单数与扣费失败订单数。
     *
     * <p>只读接口，用于 APP 侧欠费提醒。统计范围限定在近若干个月
     * （{@code app.acc-info.query-months}，默认 3），以命中 {@code TXN_DATE} 月分区裁剪；
     * 这是**联机链路**，NEVER 改成不带时间下限的全历史统计。</p>
     *
     * <p>调用方 MUST 先判断 retCode 再用两个数量。查询未真正执行时两个数量为 0，
     * **NEVER** 把它当成「该用户无欠费」——放行类判定（过闸、解约）各有自己的校验，
     * NEVER 改用本接口结果。</p>
     */
    RequestUserAccInfoResult requestUserAccInfo(RequestUserAccInfoReqDTO request);
}
