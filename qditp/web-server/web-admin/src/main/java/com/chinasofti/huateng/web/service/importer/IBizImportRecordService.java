package com.chinasofti.huateng.web.service.importer;

import java.util.List;
import com.chinasofti.huateng.web.domain.importer.BizImportRecord;

/**
 * 批量导入记录 Service 接口
 *
 * @author tuanjie
 */
public interface IBizImportRecordService
{
    /**
     * 新增导入记录
     */
    int insertBizImportRecord(BizImportRecord record);

    /**
     * 更新导入记录
     */
    int updateBizImportRecord(BizImportRecord record);

    /**
     * 查询导入记录列表
     */
    List<BizImportRecord> selectBizImportRecordList(BizImportRecord record);

    /**
     * 查询导入记录详情
     */
    BizImportRecord selectBizImportRecordById(Long importId);

    /**
     * 按批次号查询
     */
    BizImportRecord selectBizImportRecordByBatchNo(String batchNo);
}
