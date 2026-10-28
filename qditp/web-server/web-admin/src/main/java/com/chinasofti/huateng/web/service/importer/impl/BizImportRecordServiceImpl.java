package com.chinasofti.huateng.web.service.importer.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.chinasofti.huateng.web.mapper.importer.BizImportRecordMapper;
import com.chinasofti.huateng.web.domain.importer.BizImportRecord;
import com.chinasofti.huateng.web.service.importer.IBizImportRecordService;

/**
 * 批量导入记录 Service 实现
 *
 * @author tuanjie
 */
@Service
public class BizImportRecordServiceImpl implements IBizImportRecordService
{
    @Autowired
    private BizImportRecordMapper bizImportRecordMapper;

    @Override
    public int insertBizImportRecord(BizImportRecord record)
    {
        return bizImportRecordMapper.insertBizImportRecord(record);
    }

    @Override
    public int updateBizImportRecord(BizImportRecord record)
    {
        return bizImportRecordMapper.updateBizImportRecord(record);
    }

    @Override
    public List<BizImportRecord> selectBizImportRecordList(BizImportRecord record)
    {
        return bizImportRecordMapper.selectBizImportRecordList(record);
    }

    @Override
    public BizImportRecord selectBizImportRecordById(Long importId)
    {
        return bizImportRecordMapper.selectBizImportRecordById(importId);
    }

    @Override
    public BizImportRecord selectBizImportRecordByBatchNo(String batchNo)
    {
        return bizImportRecordMapper.selectBizImportRecordByBatchNo(batchNo);
    }
}
