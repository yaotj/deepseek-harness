package com.chinasofti.huateng.web.mapper.recon;

import java.util.List;
import com.chinasofti.huateng.web.domain.recon.ReconUploadFile;

/**
 * 对账文件上传 Mapper
 */
public interface ReconUploadFileMapper
{
    /**
     * 按ID查询
     */
    ReconUploadFile selectReconUploadFileById(Long id);

    /**
     * 查询上传文件列表（默认按创建时间倒序）
     */
    List<ReconUploadFile> selectReconUploadFileList(ReconUploadFile file);

    /**
     * 新增上传记录
     */
    int insertReconUploadFile(ReconUploadFile file);

    /**
     * 删除上传记录
     */
    int deleteReconUploadFileById(Long id);
}
