package com.chinasofti.huateng.web.service.recon;

import java.util.List;
import com.chinasofti.huateng.web.domain.recon.ReconUploadFile;

/**
 * 对账文件上传 Service 接口
 */
public interface IReconUploadFileService
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
     * 取同一 (类别, 对账日期) 下的现有记录，替换上传时用它定位要被覆盖的旧文件。
     */
    ReconUploadFile selectByCategoryAndDate(String fileCategory, String reconDate);

    /**
     * 新增上传记录
     */
    int insertReconUploadFile(ReconUploadFile file);

    /**
     * 按主键覆盖上传记录（替换上传保持 id 不变）。
     */
    int updateReconUploadFile(ReconUploadFile file);

    /**
     * 删除上传记录
     */
    int deleteReconUploadFileById(Long id);
}
