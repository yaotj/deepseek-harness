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

/** `GATE_TXN_PAY` 的只读查询侧：运营分页、APP 交易记录 / 账单统计 / 订单详情 / 账务信息、 以及解约与黑名单解除要用的未结清判定。 */
public interface GateTxnPayQueryService {
    ResultVO<Map<String, Object>> page(String orderNo, String cardId, String thirdUserId, String signChannelCode,
                                        String cardType, String debitStatus, String startDate, String endDate,
                                        Integer pageNum, Integer pageSize);

    /** 按交易业务键查询 GT 订单号（供 ticket-server 关联查询）。 */
    GateTxnPayRespDTO queryOrderByBizKey(GateTxnPayReqDTO request);

    /**
     * 分页查询进出站交易记录（供 ticket-server RPC 调用）。
     *
     * @param debitRequestResult 扣款结果过滤：null 或空=全部，"0"=已扣款成功，"1"=未扣款成功。
     * @param issueChannelCode 发行渠道过滤：null 或空=全渠道，{@code 07}=支付宝出行。
     */
    List<GateTxnPayListDTO> selectTransList(@Param("thirdUserId") String thirdUserId,
                                            @Param("cardIdList") List<String> cardIdList,
                                            @Param("cardType") String cardType,
                                            @Param("cardTypeList") List<String> cardTypeList,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("ticketCode") String ticketCode,
                                            @Param("debitRequestResult") String debitRequestResult,
                                            @Param("issueChannelCode") String issueChannelCode,
                                            @Param("offset") Integer offset,
                                            @Param("limit") Integer limit);
    /** 统计 IF8A-05 分页查询结果总数（供 ticket-server RPC 调用）。 */
    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("cardTypeList") List<String> cardTypeList,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode,
                       @Param("debitRequestResult") String debitRequestResult,
                       @Param("issueChannelCode") String issueChannelCode);

    /** IF8A-41 账单统计（供 ticket-server RPC 调用）。 */
    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);

    /** 按订单号查询交易记录（供 ticket-server RPC 调用）。 */
    GateTxnPayListDTO selectByOrderNo(@Param("orderNo") String orderNo);
    /** 查询用户指定支付渠道下是否存在未结清扣费订单（DEBIT_STATUS 非 SUCCESS）。 */
    GateTxnPayFailedOrderRespDTO hasFailedOrder(@Param("thirdUserId") String thirdUserId,
                                                @Param("paymentVendor") String paymentVendor,
                                                @Param("requestTime") LocalDateTime requestTime);

    /** 查询单张卡下是否存在未结清扣费订单（供 blacklist-server 盘点黑名单可解除性调用）。 */
    CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@Param("cardId") String cardId);

    /** 综管台离线码交易统计：日期窗（yyyy-MM-dd，闭区间）必填，返回按车站分组列表与全窗汇总。 */
    ResultVO<Map<String, Object>> offlineStats(String startDate, String endDate);

    /** 综管台批量退超时罚金的圈单查询：日期窗（yyyy-MM-dd，闭区间）必填，按车站分页圈出候选单。 */
    ResultVO<Map<String, Object>> overtimeRefundablePage(String stationCode, String startDate, String endDate,
                                                         Integer pageNum, Integer pageSize);
    /** IF8A-35 查询用户账务信息：返回未支付订单数与扣费失败订单数。 */
    RequestUserAccInfoResult requestUserAccInfo(RequestUserAccInfoReqDTO request);
}
