package com.chinasofti.huateng.web.service.recon.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.chinasofti.huateng.web.domain.recon.ReconUploadFile;
import com.chinasofti.huateng.web.mapper.recon.ReconUploadFileMapper;
import com.chinasofti.huateng.web.service.recon.IReconUploadFileService;

/**
 * 对账文件上传 Service 实现
 */
@Service
public class ReconUploadFileServiceImpl implements IReconUploadFileService
{
    @Autowired
    private ReconUploadFileMapper reconUploadFileMapper;

    @Override
    public ReconUploadFile selectReconUploadFileById(Long id)
    {
        return reconUploadFileMapper.selectReconUploadFileById(id);
    }

    @Override
    public List<ReconUploadFile> selectReconUploadFileList(ReconUploadFile file)
    {
        return reconUploadFileMapper.selectReconUploadFileList(file);
    }

    @Override
    public int insertReconUploadFile(ReconUploadFile file)
    {
        return reconUploadFileMapper.insertReconUploadFile(file);
    }

    @Override
    public int deleteReconUploadFileById(Long id)
    {
        return reconUploadFileMapper.deleteReconUploadFileById(id);
    }
}
