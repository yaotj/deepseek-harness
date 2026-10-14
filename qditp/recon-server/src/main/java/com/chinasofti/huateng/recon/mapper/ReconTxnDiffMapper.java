package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.thirdparty.TxnDiffRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconTxnDiffMapper {

    /** 重跑前按自然日清空旧差异。 */
    int deleteByBillDate(@Param("billDate") String billDate);

    /** 批量写入差异明细。 */
    int insertBatch(@Param("records") List<TxnDiffRecord> records);
}
