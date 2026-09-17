package com.chinasofti.huateng.recon.model;

/**
 * 单个 {@code (批次, 来源, 文件类型)} 的抽取进度，与 {@code RECON_BATCH_SOURCE} 一行对应。
 *
 * @param batchId         批次标识
 * @param source          来源标识
 * @param fileType        文件类型
 * @param status          进度状态
 * @param declaredParts   源声明的分片总数；未声明前为 0
 * @param declaredRecords 源声明的记录总数
 * @param declaredAmount  源声明的金额合计，单位分
 * @param retryCount      已重试次数，用于卡死告警与退避
 * @param failReason      失败或不一致的原因
 */
public record SourceProgress(String batchId,
                             String source,
                             ReconFileType fileType,
                             ReconSourceStatus status,
                             Integer declaredParts,
                             Long declaredRecords,
                             Long declaredAmount,
                             Integer retryCount,
                             String failReason) {
}
