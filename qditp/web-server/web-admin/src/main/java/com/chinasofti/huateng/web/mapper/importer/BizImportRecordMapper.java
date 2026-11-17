package com.chinasofti.huateng.web.mapper.importer;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.web.domain.importer.BizImportRecord;


public interface BizImportRecordMapper
{

    int insertBizImportRecord(BizImportRecord record);


    int updateBizImportRecord(BizImportRecord record);


    List<BizImportRecord> selectBizImportRecordList(BizImportRecord record);


    BizImportRecord selectBizImportRecordById(Long importId);


    BizImportRecord selectBizImportRecordByBatchNo(@Param("batchNo") String batchNo);
}
