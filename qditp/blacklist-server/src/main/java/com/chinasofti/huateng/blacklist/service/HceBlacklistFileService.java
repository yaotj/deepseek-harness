package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.config.HceBlacklistFileProperties;
import com.chinasofti.huateng.blacklist.constant.BlacklistErrorCodeEnum;
import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.HceBlacklistFileState;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.mapper.HceBlacklistFileStateMapper;
import com.chinasofti.huateng.blacklist.service.hce.HceBlacklistFileBuilder;
import com.chinasofti.huateng.blacklist.service.hce.HceBlacklistFtpUploader;
import com.chinasofti.huateng.model.app.HceBlacklistFileGenRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


@Service
public class HceBlacklistFileService {

    private static final Logger log = LoggerFactory.getLogger(HceBlacklistFileService.class);

    private final HceBlacklistFileProperties properties;
    private final BlacklistMapper blacklistMapper;
    private final HceBlacklistFileStateMapper stateMapper;
    private final HceBlacklistFtpUploader ftpUploader;

    public HceBlacklistFileService(HceBlacklistFileProperties properties,
                                   BlacklistMapper blacklistMapper,
                                   HceBlacklistFileStateMapper stateMapper,
                                   HceBlacklistFtpUploader ftpUploader) {
        this.properties = properties;
        this.blacklistMapper = blacklistMapper;
        this.stateMapper = stateMapper;
        this.ftpUploader = ftpUploader;
    }


    public HceBlacklistFileGenRespDTO generateAndUpload() {
        HceBlacklistFileGenRespDTO response = new HceBlacklistFileGenRespDTO();

        List<Blacklist> rows = blacklistMapper.selectHceBlacklist();
        response.setCardCount(rows.size());

        long version = allocateVersion();
        response.setVersion(version);
        String fileName = properties.getFilePrefix() + String.format("%06d", version);
        response.setFileName(fileName);

        Path localFile;
        try {
            localFile = writeLocalFile(fileName, HceBlacklistFileBuilder.build(version, rows));
        } catch (IOException e) {
            log.error("HCE黑名单文件写本地失败, fileName={}", fileName, e);
            return fail(response, "写本地文件失败: " + e.getMessage());
        }
        log.info("HCE黑名单文件已生成, fileName={}, version={}, cardCount={}, path={}",
                fileName, version, rows.size(), localFile);

        if (!StringUtils.hasText(properties.getFtpHost())) {
            log.error("HCE黑名单文件已生成但未上传: FTP 未配置(blacklist.hce-file.ftp-host), fileName={}", fileName);
            return fail(response, "FTP 未配置，文件仅落本地: " + localFile);
        }
        try {
            ftpUploader.upload(localFile, fileName);
        } catch (Exception e) {
            log.error("HCE黑名单文件上传FTP失败, fileName={}, localPath={}", fileName, localFile, e);
            return fail(response, "上传FTP失败: " + e.getMessage() + "，本地文件: " + localFile);
        }

        response.setUploaded(true);
        response.setResultCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(BlacklistErrorCodeEnum.SUCCESS.getMsg());
        return response;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public long allocateVersion() {
        String paramType = properties.getParamType();
        HceBlacklistFileState state = stateMapper.selectForUpdate(paramType);
        if (state == null || state.getCurrentVersion() == null) {
            stateMapper.insert(paramType, 1L);
            return 1L;
        }
        long next = state.getCurrentVersion() + 1;
        stateMapper.updateVersion(paramType, next);
        return next;
    }

    private Path writeLocalFile(String fileName, byte[] content) throws IOException {
        Path dir = Paths.get(properties.getLocalDir());
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.write(file, content);
        return file;
    }

    private HceBlacklistFileGenRespDTO fail(HceBlacklistFileGenRespDTO response, String message) {
        response.setResultCode(BlacklistErrorCodeEnum.FAIL.getCode());
        response.setResultMsg(BlacklistErrorCodeEnum.FAIL.getMsg());
        response.setMessage(message);
        response.setUploaded(false);
        return response;
    }
}
