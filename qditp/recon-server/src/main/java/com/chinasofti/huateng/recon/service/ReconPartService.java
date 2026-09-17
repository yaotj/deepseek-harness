package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.recon.mapper.ReconPartMapper;
import com.chinasofti.huateng.recon.model.PartReceipt;
import com.chinasofti.huateng.recon.model.ReconFileType;
import com.chinasofti.huateng.recon.model.ReconPartStatus;
import com.chinasofti.huateng.recon.storage.ReconStorageProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ReconPartService {
    private final ReconPartMapper partMapper;
    private final ReconBatchService batchService;
    private final ReconStorageProperties properties;

    public ReconPartService(ReconPartMapper partMapper, ReconBatchService batchService, ReconStorageProperties properties) {
        this.partMapper = partMapper;
        this.batchService = batchService;
        this.properties = properties;
    }

    /**
     * 接收一个分片：边收边算 SHA-256，校验通过后落盘并落库。
     *
     * @param declaredRecords 源声明的行数
     * @param declaredAmount  源声明的金额合计（单位分），写入 {@code AMOUNT_TOTAL} 供收齐校验比对
     */
    public PartReceipt receive(String batchId, String source, ReconFileType fileType, int partNo,
                               long declaredRecords, long declaredAmount, String declaredSha256,
                               InputStream input) throws IOException {
        validateBatchId(batchId);
        validatePart(source, partNo, declaredRecords, declaredSha256);
        if (batchService.get(batchId) == null) throw new IllegalArgumentException("对账批次不存在: " + batchId);
        PartReceipt existing = partMapper.selectByKey(batchId, source, fileType.name(), partNo);
        if (existing != null) {
            if (!existing.sha256().equalsIgnoreCase(declaredSha256)) throw new IllegalStateException("重复分片内容不一致");
            return existing;
        }

        Path directory = Path.of(properties.getStorageRoot(), batchId, source, fileType.name().toLowerCase());
        Files.createDirectories(directory);
        Path target = directory.resolve("part-" + String.format("%08d", partNo) + ".dat");
        Path temporary = directory.resolve(target.getFileName() + ".receiving");
        TransferStats stats = copyAndDigest(input, temporary);
        if (stats.bytes() > properties.getMaxPartBytes()) {
            Files.deleteIfExists(temporary);
            throw new IllegalArgumentException("分片超过大小限制");
        }
        if (!stats.sha256().equalsIgnoreCase(declaredSha256)) {
            Files.deleteIfExists(temporary);
            throw new IllegalArgumentException("分片 SHA-256 校验失败");
        }
        moveAtomically(temporary, target);
        try {
            partMapper.insert(batchId, source, fileType.name(), partNo, stats.bytes(), declaredRecords,
                    declaredAmount, stats.sha256(), target.toString(), ReconPartStatus.RECEIVED.name());
        } catch (RuntimeException duplicate) {
            if (!isDuplicateKeyViolation(duplicate)) throw duplicate;
            Files.deleteIfExists(target);
            PartReceipt concurrent = partMapper.selectByKey(batchId, source, fileType.name(), partNo);
            if (concurrent == null || !concurrent.sha256().equalsIgnoreCase(stats.sha256())) throw new IllegalStateException("并发接收的分片内容不一致", duplicate);
            return concurrent;
        }
        return partMapper.selectByKey(batchId, source, fileType.name(), partNo);
    }

    private TransferStats copyAndDigest(InputStream input, Path temporary) throws IOException {
        MessageDigest digest = sha256();
        long bytes = 0;
        try (InputStream in = new BufferedInputStream(input); OutputStream out = new BufferedOutputStream(Files.newOutputStream(temporary))) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                bytes += read;
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        return new TransferStats(bytes, HexFormat.of().formatHex(digest.digest()));
    }

    private void validatePart(String source, int partNo, long records, String sha256) {
        if (source == null || !source.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("非法来源标识");
        if (partNo < 0 || partNo > 99_999_999) throw new IllegalArgumentException("非法分片序号");
        if (records < 0 || records > properties.getMaxPartRecords()) throw new IllegalArgumentException("分片记录数超过限制");
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("非法 SHA-256");
    }

    private void validateBatchId(String batchId) {
        if (batchId == null || !batchId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("非法批次标识");
        }
    }

    private void moveAtomically(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JDK 不支持 SHA-256", e); }
    }

    /**
     * 异常链上是否有唯一键冲突。
     *
     * @param ex 捕获到的异常
     * @return 链上出现过唯一键冲突即 true
     */
    private static boolean isDuplicateKeyViolation(Throwable ex) {
        for (Throwable cursor = ex; cursor != null && cursor != cursor.getCause(); cursor = cursor.getCause()) {
            if (cursor instanceof DuplicateKeyException) return true;
        }
        return false;
    }

    private record TransferStats(long bytes, String sha256) { }
}
