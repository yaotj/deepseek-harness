package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.thirdparty.TxnDiffRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconTxnDiffMapper {

    int deleteByBillDate(@Param("billDate") String billDate);

    int insertBatch(@Param("records") List<TxnDiffRecord> records);
}
