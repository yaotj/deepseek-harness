package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** {@code ALIPAY_PAY_TXN_DETAIL} 数据访问。 */
@Mapper
public interface AlipayPayTxnDetailMapper {

    int insert(AlipayPayTxnDetail record);

    AlipayPayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);

    List<AlipayPayTxnDetail> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    AlipayPayTxnDetail selectByEntryId(@Param("entryId") String entryId);

    AlipayPayTxnDetail selectByExitId(@Param("exitId") String exitId);

    /** 出网前留痕：请求次数 +1、状态压回 {@code PROCESSING}、记首次与最近请求时间。 */
    int markRequesting(@Param("orderNo") String orderNo);

    /** 回写支付中心同步应答（受理结果），不带状态机 —— 本方就是这一步的唯一写入者。 */
    int updateRequestResult(AlipayPayTxnDetail record);

    /** 支付回调回写，带「非终态」状态白名单。 */
    int updatePayCallback(AlipayPayTxnDetail record);

    /** 支付结果查询接口的回写，WHERE 额外要求当前状态不是 {@code SUCCESS}。 */
    int updatePayQueryResultIfNotSuccess(AlipayPayTxnDetail record);

    /** 按 {@code ALIPAY_REFUND_TXN_DETAIL} 重算本单的已退金额与退款状态。 */
    int updateRefundSummary(@Param("orderNo") String orderNo);

    /** 运营后台分页查询。 */
    List<AlipayPayTxnDetail> selectPagedList(@Param("thirdUserId") String thirdUserId,
                                            @Param("cardId") String cardId,
                                            @Param("debitRequestResult") String debitRequestResult,
                                            @Param("invoice") String invoice,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);

    int countPagedList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardId") String cardId,
                       @Param("debitRequestResult") String debitRequestResult,
                       @Param("invoice") String invoice,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate);

    /** 按卡号统计未结清订单，供 blacklist-server 盘点该卡欠费是否结清。 */
    int countUnsettledByCardId(@Param("cardId") String cardId);
}
