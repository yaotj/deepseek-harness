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

/**
 * HCE 卡黑名单文件生成并上传 ACC FTP。
 *
 * <p>流程：查 BLACKLIST 里 HCE 票种（0442/0443）的生效行（与判黑同口径，RELEASING 仍算黑名单）
 * → 分配文件版本号 → 生成 PRM.0013.9700.&lt;版本&gt; 二进制文件落本地目录 → 上传 ACC FTP。
 *
 * <p>版本号在独立事务里先分配先提交：即便后续写文件或上传失败，版本也已消耗、只进不退，
 * ACC 侧永远按更大的版本号识别新文件。上传失败的文件留在本地目录可人工补传，
 * 不做自动重传（下一轮调度会产出覆盖性的新版本全量文件）。
 */
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

    /**
     * 生成 HCE 黑名单文件并上传 ACC FTP。
     *
     * @return 执行结果；FTP 未配置或上传失败时 resultCode 非 0000
     */
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
            // FTP 未配置视为环境未就绪：文件已落本地，不判失败也不判成功，人工核对后补传。
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

    /**
     * 分配文件版本号：独立事务 + 行锁，只进不退。
     *
     * <p>REQUIRES_NEW 刻意为之：版本号先提交，即便外层后续步骤失败也不回滚——
     * 同名同版本文件被两轮内容不同的生成覆盖，比「跳号」更难排查。
     */
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
