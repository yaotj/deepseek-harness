package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.thirdparty.ItpTxnRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayTxnDetailMapper {

    List<ItpTxnRecord> selectByTxnDate(@Param("txnDate") String txnDate);
}
