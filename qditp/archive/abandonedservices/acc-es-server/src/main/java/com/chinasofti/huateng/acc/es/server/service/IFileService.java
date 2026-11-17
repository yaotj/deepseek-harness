package com.chinasofti.huateng.acc.es.server.service;


import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.web.multipart.MultipartFile;


public interface IFileService {

    ResultVO<String> upload(MultipartFile file);
}
