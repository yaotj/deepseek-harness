package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.page.ReconClearingRowView;
import com.chinasofti.huateng.recon.model.page.ReconDailyStatView;
import com.chinasofti.huateng.recon.model.page.ReconThirdBillPageView;
import com.chinasofti.huateng.recon.model.page.ReconTxnDiffPageView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconReportMapper {

    List<ReconTxnDiffPageView> selectTxnDiffPage(@Param("billDateStart") String billDateStart,
                                                  @Param("billDateEnd") String billDateEnd,
                                                  @Param("payChannel") String payChannel,
                                                  @Param("diffSide") String diffSide,
                                                  @Param("orderNo") String orderNo,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    long countTxnDiffPage(@Param("billDateStart") String billDateStart,
                          @Param("billDateEnd") String billDateEnd,
                          @Param("payChannel") String payChannel,
                          @Param("diffSide") String diffSide,
                          @Param("orderNo") String orderNo);

    List<ReconThirdBillPageView> selectThirdBillPage(@Param("billDateStart") String billDateStart,
                                                      @Param("billDateEnd") String billDateEnd,
                                                      @Param("paymentChannel") String paymentChannel,
                                                      @Param("paymentVendor") String paymentVendor,
                                                      @Param("merchantOrderNo") String merchantOrderNo,
                                                      @Param("offset") int offset,
                                                      @Param("limit") int limit);

    long countThirdBillPage(@Param("billDateStart") String billDateStart,
                            @Param("billDateEnd") String billDateEnd,
                            @Param("paymentChannel") String paymentChannel,
                            @Param("paymentVendor") String paymentVendor,
                            @Param("merchantOrderNo") String merchantOrderNo);


    List<ReconDailyStatView> selectDailyStats(@Param("groupBy") String groupBy,
                                              @Param("txnDateStart") String txnDateStart,
                                              @Param("txnDateEnd") String txnDateEnd);

    List<ReconClearingRowView> selectClearingRows(@Param("billDateStart") String billDateStart,
                                                   @Param("billDateEnd") String billDateEnd,
                                                   @Param("paymentChannel") String paymentChannel,
                                                   @Param("paymentVendor") String paymentVendor);
}
