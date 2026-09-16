package com.chinasofti.huateng.recon.model;

/**
 * 最终对账文件视图，与 {@code RECON_BATCH_FILE} 一行对应。
 *
 * @param batchId     批次标识
 * @param fileType    文件类型
 * @param fileName    最终文件名，形如 {@code ITP.EXP.20260910}
 * @param path        本地绝对路径
 * @param byteCount   文件字节数
 * @param recordCount 文件行数
 * @param amountTotal 文件金额合计，单位分
 * @param sha256      文件内容 SHA-256
 * @param status      文件状态
 * @param remotePath  FTP 远端路径，投递成功后写入
 *
 * @implNote {@code byteCount} / {@code recordCount} / {@code amountTotal} <b>MUST 用装箱类型</b>：
 *     MyBatis 构造器映射按装箱类型精确查找构造器，基本类型分量会在查出数据行时抛
 *     {@code NoSuchMethodException}（详见 {@link SourceProgress}）。
 */
public record ReconFileView(String batchId,
                            ReconFileType fileType,
                            String fileName,
                            String path,
                            Long byteCount,
                            Long recordCount,
                            Long amountTotal,
                            String sha256,
                            String status,
                            String remotePath) { }
