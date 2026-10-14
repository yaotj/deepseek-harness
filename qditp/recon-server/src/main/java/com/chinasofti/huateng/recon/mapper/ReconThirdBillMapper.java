package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.thirdparty.ThirdPartyBill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconThirdBillMapper {

    /** 幂等入库：以 (BILL_DATE, CHANNEL_TRADE_NO) 为幂等键，存在则更新金额/状态。 */
    int mergeBatch(@Param("records") List<ThirdPartyBill> records, @Param("batchNo") String batchNo);

    /** 取某自然日全部渠道账单。 */
    List<ThirdPartyBill> selectByBillDate(@Param("billDate") String billDate);
}
