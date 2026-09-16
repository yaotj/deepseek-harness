package com.chinasofti.huateng.recon.model;

/**
 * 分片接收回执，与 {@code RECON_BATCH_PART} 一行对应。
 *
 * @param batchId     批次标识
 * @param source      来源标识
 * @param fileType    文件类型
 * @param partNo      分片号，从 0 开始连续编号
 * @param byteCount   分片字节数
 * @param recordCount 分片行数（由源声明，服务端不逐行数）
 * @param amountTotal 分片金额合计，单位分（由源声明，用于与源端总账核对）
 * @param sha256      分片内容 SHA-256，服务端边收边算并与声明值比对
 * @param path        分片落盘绝对路径
 * @param status      分片状态
 *
 * @implNote {@code partNo} / {@code byteCount} / {@code recordCount} / {@code amountTotal}
 *     <b>MUST 用装箱类型</b>：MyBatis 构造器映射按装箱类型精确查找构造器，基本类型分量会在查出
 *     数据行时抛 {@code NoSuchMethodException}（详见 {@link SourceProgress}）。
 */
public record PartReceipt(String batchId,
                          String source,
                          ReconFileType fileType,
                          Integer partNo,
                          Long byteCount,
                          Long recordCount,
                          Long amountTotal,
                          String sha256,
                          String path,
                          ReconPartStatus status) {
}
