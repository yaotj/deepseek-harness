package com.chinasofti.huateng.acc.es.server.service.impl;

import com.chinasofti.huateng.acc.es.server.config.FtpComponent;
import com.chinasofti.huateng.acc.es.server.service.IFileService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * @author rxwnc
 */
@Service
@AllArgsConstructor
@Slf4j
public class FileServiceImpl implements IFileService {

    private final FtpComponent ftpComponent;


    @Override
    public ResultVO<String> upload(@RequestParam("file") MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        assert originalFilename != null;
        String[] split = originalFilename.split("\\.");
        String finalName = split[0] + UUID.randomUUID().toString().replace("-","") + "." + split[1];
        try {
            file.transferTo(new File(ftpComponent.getExcelDir() + finalName));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ResultMapper.error();
        }
        return ResultMapper.ok(finalName);
    }

 }
