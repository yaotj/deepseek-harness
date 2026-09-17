package com.chinasofti.huateng.rpc.recon;

import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import com.chinasofti.huateng.model.recon.ReconRecord;
import com.chinasofti.huateng.model.recon.ReconSourceCompleteReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 对账分片写出与上送的收口对象，源服务抽取时**逐行写入**、按阈值自动滚片、每片即时上送。
 */
public final class ReconPartSink implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReconPartSink.class);

    private final ReconClient client;

    private final String batchId;

    private final String source;

    private final ReconFileTypeEnum fileType;

    private final Path workDir;

    private final long maxPartRecords;

    private final long maxPartBytes;

    private int partNo;

    private long partRecords;

    private long partBytes;

    private long partAmount;

    private long totalRecords;

    private long totalAmount;

    private Writer writer;

    private MessageDigest digest;

    private Path partFile;

    private boolean committed;

    private boolean closed;

    ReconPartSink(ReconClient client, String batchId, String source, ReconFileTypeEnum fileType,
                  Path workDir, long maxPartRecords, long maxPartBytes) {
        this.client = client;
        this.batchId = batchId;
        this.source = source;
        this.fileType = fileType;
        this.workDir = workDir;
        this.maxPartRecords = maxPartRecords;
        this.maxPartBytes = maxPartBytes;
    }

    /**
     * 写一行记录。
     * @param line 管道分隔的一行，不含行尾换行符。
     * @param amountCents 该行参与对账的金额，单位分；汇总行传该行的金额合计。
     */
    public void write(String line, long amountCents) {
        if (committed || closed) {
            throw new IllegalStateException("分片写出通道已关闭 batchId=" + batchId + ", source=" + source);
        }
        try {
            ensureOpen();
            writer.write(line);
            writer.write(ReconRecord.LINE_SEPARATOR);
        } catch (IOException ex) {
            throw new UncheckedIOException("写对账分片失败 partNo=" + partNo, ex);
        }
        partRecords++;
        totalRecords++;
        partBytes += line.length() + 1L;
        partAmount += amountCents;
        totalAmount += amountCents;
        if (partRecords >= maxPartRecords || partBytes >= maxPartBytes) {
            flushPart();
        }
    }

    /**
     * 收口：把最后一片写出并上送，然后向 recon-server 声明本源本文件类型已完成。
     */
    public void commit() {
        if (committed) {
            return;
        }
        flushPart();
        ReconSourceCompleteReqDTO request = new ReconSourceCompleteReqDTO();
        request.setTotalParts(partNo);
        request.setTotalRecords(totalRecords);
        request.setTotalAmount(totalAmount);
        client.declareComplete(batchId, source, fileType, request);
        committed = true;
        log.info("对账分片导出完成 batchId={}, source={}, fileType={}, parts={}, records={}, amount={}",
                batchId, source, fileType, partNo, totalRecords, totalAmount);
    }

    /** 已写出并上送成功的分片数。 */
    public int getUploadedParts() {
        return partNo;
    }

    /** 已写出的记录总数。 */
    public long getTotalRecords() {
        return totalRecords;
    }

    /** 已写出的金额合计，单位分。 */
    public long getTotalAmount() {
        return totalAmount;
    }

    /**
     * 关闭通道：清理本地临时文件；若未 {@link #commit()} 则向 recon-server 声明失败。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeQuietly();
        deleteQuietly(partFile);
        deleteQuietly(workDir);
        if (committed) {
            return;
        }
        try {
            ReconSourceCompleteReqDTO request = new ReconSourceCompleteReqDTO();
            request.setTotalParts(partNo);
            request.setTotalRecords(totalRecords);
            request.setTotalAmount(totalAmount);
            request.setFailReason("源服务抽取未收口，已上送 " + partNo + " 片");
            client.declareComplete(batchId, source, fileType, request);
        } catch (RuntimeException ex) {
            log.error("声明对账抽取失败也失败了 batchId={}, source={}, fileType={}, msg={}",
                    batchId, source, fileType, ex.getMessage(), ex);
        }
    }

    private void ensureOpen() throws IOException {
        if (writer != null) {
            return;
        }
        Files.createDirectories(workDir);
        partFile = workDir.resolve(fileType.name().toLowerCase() + "-part-" + String.format("%08d", partNo) + ".dat");
        digest = newDigest();
        OutputStream out = new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(partFile), 1 << 20), digest);
        writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        partRecords = 0;
        partBytes = 0;
        partAmount = 0;
    }

    private void flushPart() {
        if (writer == null || partRecords == 0) {
            closeQuietly();
            return;
        }
        String sha256;
        long byteCount;
        try {
            writer.flush();
            writer.close();
            writer = null;
            sha256 = HexFormat.of().formatHex(digest.digest());
            byteCount = Files.size(partFile);
        } catch (IOException ex) {
            throw new UncheckedIOException("收尾对账分片失败 partNo=" + partNo, ex);
        }
        log.info("准备上送对账分片 batchId={}, source={}, fileType={}, partNo={}, records={}, bytes={}",
                batchId, source, fileType, partNo, partRecords, byteCount);
        client.uploadPart(batchId, source, fileType, partNo, partRecords, partAmount, sha256, partFile);
        deleteQuietly(partFile);
        partFile = null;
        partNo++;
        partRecords = 0;
        partBytes = 0;
        partAmount = 0;
    }

    private void closeQuietly() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ex) {
            log.warn("关闭对账分片写出流失败 partNo={}, msg={}", partNo, ex.getMessage());
        }
        writer = null;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("清理对账分片临时文件失败 path={}, msg={}", path, ex.getMessage());
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 不支持 SHA-256", ex);
        }
    }
}
