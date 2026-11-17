package com.chinasofti.huateng.web.service.importer;

import java.util.List;
import com.chinasofti.huateng.web.domain.importer.BizImportRecord;


public interface IBizImportRecordService
{

    int insertBizImportRecord(BizImportRecord record);


    int updateBizImportRecord(BizImportRecord record);


    List<BizImportRecord> selectBizImportRecordList(BizImportRecord record);


    BizImportRecord selectBizImportRecordById(Long importId);


    BizImportRecord selectBizImportRecordByBatchNo(String batchNo);
}
