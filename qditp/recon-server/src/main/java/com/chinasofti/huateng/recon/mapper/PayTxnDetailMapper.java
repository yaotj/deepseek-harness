package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.thirdparty.ItpTxnRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayTxnDetailMapper {

    /** 取某自然日(TXN_DATE=YYYYMMDD)的 ITP 侧支付台账，作为第三方对账的 ITP 侧源。 */
    List<ItpTxnRecord> selectByTxnDate(@Param("txnDate") String txnDate);
}
