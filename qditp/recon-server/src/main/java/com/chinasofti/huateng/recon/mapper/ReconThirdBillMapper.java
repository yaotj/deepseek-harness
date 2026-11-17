package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.thirdparty.ThirdPartyBill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconThirdBillMapper {

    int mergeBatch(@Param("records") List<ThirdPartyBill> records, @Param("batchNo") String batchNo);

    List<ThirdPartyBill> selectByBillDate(@Param("billDate") String billDate);
}
