package com.chinasofti.huateng.web.mapper.importer;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.web.domain.importer.BizImportRecord;

/**
 * 批量导入记录 Mapper
 *
 * @author tuanjie
 */
public interface BizImportRecordMapper
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
    BizImportRecord selectBizImportRecordByBatchNo(@Param("batchNo") String batchNo);
}
