package com.chinasofti.huateng.web.service.recon;

import java.util.List;
import com.chinasofti.huateng.web.domain.recon.ReconUploadFile;


public interface IReconUploadFileService
{

    ReconUploadFile selectReconUploadFileById(Long id);


    List<ReconUploadFile> selectReconUploadFileList(ReconUploadFile file);


    ReconUploadFile selectByCategoryAndDate(String fileCategory, String reconDate);


    int insertReconUploadFile(ReconUploadFile file);


    int updateReconUploadFile(ReconUploadFile file);


    int deleteReconUploadFileById(Long id);
}
