package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface GateTxnPayService {
    GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request);

    /**
     * 对支付失败/未支付的订单单独重试支付。
     *
     * @param orderNo 订单号
     * @return 支付结果
     */
    GateTxnPayRespDTO retryPay(String orderNo);

    ResultVO<Map<String, Object>> page(String orderNo, String cardId, String thirdUserId, String signChannelCode,
                                        String cardType, String debitStatus, String startDate, String endDate,
                                        Integer pageNum, Integer pageSize);

    ResultVO<RequestRefundResult> requestRefund(String orderNo, GateTxnPayRefundRequest request);

    /** 按交易业务键查询 GT 订单号（供 ticket-server 关联查询）。 */
    GateTxnPayRespDTO queryOrderByBizKey(GateTxnPayReqDTO request);

    // ==================== IF8A-05 APP 交易记录列表 ====================

    /**
     * 分页查询进出站交易记录（供 ticket-server RPC 调用）。
     */
    List<GateTxnPayListDTO> selectTransList(@Param("thirdUserId") String thirdUserId,
                                            @Param("cardIdList") List<String> cardIdList,
                                            @Param("cardType") String cardType,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("ticketCode") String ticketCode,
                                            @Param("offset") Integer offset,
                                            @Param("limit") Integer limit);

    /**
     * 统计 IF8A-05 分页查询结果总数（供 ticket-server RPC 调用）。
     */
    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode);

    // ==================== IF8A-34 APP 订单详情 ====================

    /**
     * 按订单号查询交易记录（供 ticket-server RPC 调用）。
     */
    GateTxnPayListDTO selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单。
     */
    GateTxnPayFailedOrderRespDTO hasFailedOrder(@Param("thirdUserId") String thirdUserId,
                                                @Param("paymentVendor") String paymentVendor,
                                                @Param("requestTime") LocalDateTime requestTime);
}
