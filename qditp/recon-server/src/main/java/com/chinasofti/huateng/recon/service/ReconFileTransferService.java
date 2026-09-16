package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.recon.mapper.ReconFileMapper;
import com.chinasofti.huateng.recon.model.BatchView;
import com.chinasofti.huateng.recon.model.ReconBatchStatus;
import com.chinasofti.huateng.recon.model.ReconFileStatus;
import com.chinasofti.huateng.recon.model.ReconFileType;
import com.chinasofti.huateng.recon.model.ReconFileView;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class ReconFileTransferService {
    private final ReconBatchService batchService;
    private final ReconFileMapper fileMapper;
    private final ReconFtpService ftpService;

    public ReconFileTransferService(ReconBatchService batchService,
                                    ReconFileMapper fileMapper,
                                    ReconFtpService ftpService) {
        this.batchService = batchService;
        this.fileMapper = fileMapper;
        this.ftpService = ftpService;
    }

    /**
     * 把已生成的最终文件投递到 FTP。
     *
     * <p>允许的前置批次状态：{@code GENERATING / UPLOADING / FAILED}（FAILED 是重试入口）。
     * 投递成功后**不判 SUCCESS**——批次是否收口由编排器在全部文件都 UPLOADED 后统一决定，
     * 单个文件在这里只更新自己的状态与远端路径。</p>
     */
    public ReconFileView upload(String batchId, ReconFileType fileType) throws IOException {
        BatchView batch = batchService.get(batchId);
        if (batch == null) throw new IllegalArgumentException("对账批次不存在: " + batchId);
        if (batch.status() != ReconBatchStatus.GENERATING
                && batch.status() != ReconBatchStatus.UPLOADING
                && batch.status() != ReconBatchStatus.FAILED) {
            throw new IllegalStateException("当前批次不可上传文件: " + batch.status());
        }

        ReconFileView file = fileMapper.select(batchId, fileType.name());
        if (file == null || !ReconFileStatus.GENERATED.name().equals(file.status())) throw new IllegalStateException("对账文件尚未生成");
        String remotePath = ftpService.upload(Path.of(file.path()), file.fileName());
        fileMapper.updateUpload(batchId, fileType.name(), ReconFileStatus.UPLOADED.name(), remotePath);
        return fileMapper.select(batchId, fileType.name());
    }
}
