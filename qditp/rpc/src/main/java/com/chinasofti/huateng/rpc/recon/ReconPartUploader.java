package com.chinasofti.huateng.rpc.recon;

import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 分片写出通道的工厂，四个源服务共用。
 *
 * <p>分片粒度按「记录数或未压缩字节数先到先滚」控制，默认 20 万条 / 64MB —— 400 万条明细约落
 * 20~40 片，与推荐区间一致。**NEVER 把上限调到百万级**：单片越大，重传成本越高，且服务端
 * {@code recon.max-part-bytes}（默认 128MB）会直接拒收。</p>
 *
 * <p>临时目录默认落在容器内 {@code /home/javaapp/app/recon-export}，分片一经上送即删除，
 * 因此磁盘占用峰值只有「并发文件类型数 × 单片上限」，不是全量数据大小。</p>
 */
@Service
public class ReconPartUploader {

    private final ReconClient client;

    private final Path tempRoot;

    private final long maxPartRecords;

    private final long maxPartBytes;

    /**
     * 构造分片上传工厂。
     *
     * @param client         recon-server 客户端
     * @param tempDir        分片临时目录，取自配置 {@code recon.export.temp-dir}
     * @param maxPartRecords 单片最大记录数，取自配置 {@code recon.export.max-part-records}
     * @param maxPartBytes   单片最大未压缩字节数，取自配置 {@code recon.export.max-part-bytes}
     */
    public ReconPartUploader(ReconClient client,
                             @Value("${recon.export.temp-dir:/home/javaapp/app/recon-export}") String tempDir,
                             @Value("${recon.export.max-part-records:200000}") long maxPartRecords,
                             @Value("${recon.export.max-part-bytes:67108864}") long maxPartBytes) {
        this.client = client;
        this.tempRoot = Path.of(tempDir).toAbsolutePath().normalize();
        this.maxPartRecords = maxPartRecords;
        this.maxPartBytes = maxPartBytes;
    }

    /**
     * 打开一个分片写出通道。返回对象 MUST 用 try-with-resources 包裹，成功路径 MUST 调用
     * {@link ReconPartSink#commit()}。
     *
     * @param batchId  批次标识
     * @param source   来源标识
     * @param fileType 文件类型
     * @return 分片写出通道
     */
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
