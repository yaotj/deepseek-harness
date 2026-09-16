package com.chinasofti.huateng.recon.model;

/**
 * 已落库分片的汇总，用于与源声明的三项总账比对。
 *
 * @param parts   已落库且状态为 RECEIVED 的分片数
 * @param records 分片记录数之和
 * @param amount  分片金额合计之和，单位分
 *
 * @implNote 三个分量 <b>MUST 用装箱类型</b>：MyBatis 构造器映射按装箱类型精确查找构造器，
 *     基本类型分量会在查出数据行时抛 {@code NoSuchMethodException}（详见 {@link SourceProgress}）。
 *     {@code selectTotals} 的 SQL 已用 {@code NVL} 把聚合列兜成 0，但调用点仍 MUST 判空。
 */
public record PartTotals(Integer parts, Long records, Long amount) {
}
