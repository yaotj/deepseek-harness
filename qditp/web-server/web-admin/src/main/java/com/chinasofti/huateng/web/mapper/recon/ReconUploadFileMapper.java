package com.chinasofti.huateng.web.mapper.recon;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.chinasofti.huateng.web.domain.recon.ReconUploadFile;


public interface ReconUploadFileMapper
{

    ReconUploadFile selectReconUploadFileById(Long id);


    List<ReconUploadFile> selectReconUploadFileList(ReconUploadFile file);


    ReconUploadFile selectByCategoryAndDate(@Param("fileCategory") String fileCategory,
                                            @Param("reconDate") String reconDate);


    int insertReconUploadFile(ReconUploadFile file);


    int updateReconUploadFile(ReconUploadFile file);


    int deleteReconUploadFileById(Long id);
}
