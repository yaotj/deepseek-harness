package com.chinasofti.huateng.rpc.recon;

import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;


@Service
public class ReconPartUploader {

    private final ReconClient client;

    private final Path tempRoot;

    private final long maxPartRecords;

    private final long maxPartBytes;


    public ReconPartUploader(ReconClient client,
                             @Value("${recon.export.temp-dir:/home/javaapp/app/recon-export}") String tempDir,
                             @Value("${recon.export.max-part-records:200000}") long maxPartRecords,
                             @Value("${recon.export.max-part-bytes:67108864}") long maxPartBytes) {
        this.client = client;
        this.tempRoot = Path.of(tempDir).toAbsolutePath().normalize();
        this.maxPartRecords = maxPartRecords;
        this.maxPartBytes = maxPartBytes;
    }


    public ReconPartSink open(String batchId, String source, ReconFileTypeEnum fileType) {
        if (batchId == null || !batchId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("非法批次标识: " + batchId);
        }
        if (source == null || !source.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("非法来源标识: " + source);
        }
        Path workDir = tempRoot.resolve(batchId).resolve(source).resolve(fileType.name().toLowerCase());
        if (!workDir.startsWith(tempRoot)) {
            throw new IllegalArgumentException("非法分片临时目录");
        }
        return new ReconPartSink(client, batchId, source, fileType, workDir, maxPartRecords, maxPartBytes);
    }
}
