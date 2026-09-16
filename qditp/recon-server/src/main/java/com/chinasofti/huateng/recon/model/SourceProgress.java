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
 *
 * @implNote 四个数值分量 <b>MUST 用装箱类型</b>（{@code Integer} / {@code Long}），
 *     NEVER 退回 {@code int} / {@code long}。MyBatis 走构造器映射时，
 *     resultMap 里 {@code javaType="int"} / {@code "long"} 经 TypeAliasRegistry 解析出来的是
 *     {@code java.lang.Integer} / {@code java.lang.Long}，随后用
 *     {@code getDeclaredConstructor(装箱类型...)} 精确查找构造器——基本类型不会自动拆箱匹配，
 *     一旦真正查出数据行就必抛 {@code NoSuchMethodException}（空结果集不会触发，因此极易假通过）。
 *     装箱后各调用点 MUST 显式判空兜底，NEVER 直接参与算术或 {@code >=} 比较。
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
