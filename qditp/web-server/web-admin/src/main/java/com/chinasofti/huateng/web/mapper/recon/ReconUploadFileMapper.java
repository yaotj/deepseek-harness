package com.chinasofti.huateng.web.mapper.recon;

import java.util.List;

import org.apache.ibatis.annotations.Param;

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
     * 取同一 (类别, 对账日期) 下的现有记录 —— 替换语义的入口。
     *
     * <p>该组合由函数式唯一索引 UK_RUF_CATEGORY_DATE 保证至多一行；类别为 null 的历史行不参与，
     * 因此本方法只可能返回命名合规的记录。
     */
    ReconUploadFile selectByCategoryAndDate(@Param("fileCategory") String fileCategory,
                                            @Param("reconDate") String reconDate);

    /**
     * 新增上传记录
     */
    int insertReconUploadFile(ReconUploadFile file);

    /**
     * 按主键覆盖上传记录（替换上传时保持 id 不变，列表里一个 (类别,日期) 恒一行）。
     */
    int updateReconUploadFile(ReconUploadFile file);

    /**
     * 删除上传记录
     */
    int deleteReconUploadFileById(Long id);
}
