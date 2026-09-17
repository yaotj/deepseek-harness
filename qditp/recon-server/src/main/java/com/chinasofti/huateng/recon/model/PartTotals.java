package com.chinasofti.huateng.recon.model;

/**
 * 已落库分片的汇总，用于与源声明的三项总账比对。
 *
 * @param parts   已落库且状态为 RECEIVED 的分片数
 * @param records 分片记录数之和
 * @param amount  分片金额合计之和，单位分
 */
public record PartTotals(Integer parts, Long records, Long amount) {
}
