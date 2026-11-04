package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.page.ReconClearingRowView;
import com.chinasofti.huateng.recon.model.page.ReconDailyStatView;
import com.chinasofti.huateng.recon.model.page.ReconThirdBillPageView;
import com.chinasofti.huateng.recon.model.page.ReconTxnDiffPageView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 综管台报表页只读查询（对标旧 ITP「报表管理」组：对账差异 / 渠道账单 / 日统计 / 清分）。 */
@Mapper
public interface ReconReportMapper {

    /** 对账差异明细分页（{@code RECON_TXN_DIFF}），按账单日倒序。 */
    List<ReconTxnDiffPageView> selectTxnDiffPage(@Param("billDateStart") String billDateStart,
                                                  @Param("billDateEnd") String billDateEnd,
                                                  @Param("payChannel") String payChannel,
                                                  @Param("diffSide") String diffSide,
                                                  @Param("orderNo") String orderNo,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /** 统计 selectTxnDiffPage 同口径总条数。 */
    long countTxnDiffPage(@Param("billDateStart") String billDateStart,
                          @Param("billDateEnd") String billDateEnd,
                          @Param("payChannel") String payChannel,
                          @Param("diffSide") String diffSide,
                          @Param("orderNo") String orderNo);

    /** 渠道账单行分页（{@code RECON_THIRD_BILL}），按账单日倒序。 */
    List<ReconThirdBillPageView> selectThirdBillPage(@Param("billDateStart") String billDateStart,
                                                      @Param("billDateEnd") String billDateEnd,
                                                      @Param("paymentChannel") String paymentChannel,
                                                      @Param("paymentVendor") String paymentVendor,
                                                      @Param("merchantOrderNo") String merchantOrderNo,
                                                      @Param("offset") int offset,
                                                      @Param("limit") int limit);

    /** 统计 selectThirdBillPage 同口径总条数。 */
    long countThirdBillPage(@Param("billDateStart") String billDateStart,
                            @Param("billDateEnd") String billDateEnd,
                            @Param("paymentChannel") String paymentChannel,
                            @Param("paymentVendor") String paymentVendor,
                            @Param("merchantOrderNo") String merchantOrderNo);

    /**
     * 日统计纯查询聚合（{@code PAY_TXN_DETAIL}，不建聚合表）。
     *
     * @param groupBy VENDOR=按支付账户 + 交易日期；HOUR=按当天小时（要求单日窗）
     */
    List<ReconDailyStatView> selectDailyStats(@Param("groupBy") String groupBy,
                                              @Param("txnDateStart") String txnDateStart,
                                              @Param("txnDateEnd") String txnDateEnd);

    /** 业务清分明细：渠道账单按「账单日 + 支付渠道/商户」聚合。 */
    List<ReconClearingRowView> selectClearingRows(@Param("billDateStart") String billDateStart,
                                                   @Param("billDateEnd") String billDateEnd,
                                                   @Param("paymentChannel") String paymentChannel,
                                                   @Param("paymentVendor") String paymentVendor);
}
