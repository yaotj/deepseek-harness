package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.config.FtpComponent;
import com.chinasofti.huateng.acc.es.server.service.IFileService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author rxwnc
 */
@RestController
@RequestMapping(value = "/file")
@AllArgsConstructor
@Slf4j
public class FileController {

    private final IFileService fileService;

    private final FtpComponent ftpComponent;

    /**
     * 个性化任务execl表格导入
     * @param uploadFile
     * @return
     */
    @PostMapping("/upload")
    public ResultVO<?> upLoad(@RequestParam("file") MultipartFile uploadFile) {
        return fileService.upload(uploadFile);
    }

    /**
     * 个性化文件下载
     * @param file
     * @param request
     * @param response
     */
    @GetMapping("/download")
    public void downLoad(@RequestParam("file") String file, HttpServletRequest request, HttpServletResponse response) {
        response.setCharacterEncoding("utf-8");
        response.setContentType("multipart/form-data");
        response.setHeader("Content-Disposition","attachment;fileName=" + file);
        FileInputStream fileInputStream = null;
        OutputStream os = null;
        try {
            fileInputStream = new FileInputStream(ftpComponent.getExcelDir() + file);
            os =  response.getOutputStream();
            byte[] b = new byte[1024];
            int length;
            while ((length = fileInputStream.read(b)) > 0) {
                os.write(b,0,length);
            }
        } catch (FileNotFoundException e) {
            log.error("找不到指定的文件");
        } catch (IOException e) {
            log.error("IO异常");
        }finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    log.error("输出流关闭异常");
                }
            }
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    log.error("文件流关闭异常");
                }
            }
        }

    }
}

