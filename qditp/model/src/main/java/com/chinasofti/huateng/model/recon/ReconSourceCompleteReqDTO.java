package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 源服务向 recon-server 声明「某个 (批次, 来源, 文件类型) 的分片已全部上送完毕」（IF-RECON-03）。
 *
 * <p>这是收齐判定的唯一依据：recon-server 不猜分片数量，只在收到本声明且已落库的分片数、
 * 记录数、金额合计三项都与声明一致时，才把该源标记为 COMPLETED。三项任一不符即置 MISMATCH
 * 并告警，NEVER 放行——分片少一片，最终对账文件就少几十万条，且文件本身看不出缺失。</p>
 */
@Data
public class ReconSourceCompleteReqDTO {

    /** 分片总数。分片号从 0 开始连续编号，因此合法分片号区间是 {@code [0, totalParts)}。 */
    private int totalParts;

    /** 记录总数，即本源本文件类型所有分片的行数之和。 */
    private long totalRecords;

    /** 金额合计，单位分。明细文件为金额列之和，汇总文件为汇总金额之和。 */
    private long totalAmount;

    /** 抽取失败时的原因；非空表示本源本次抽取失败，recon-server 据此置 FAILED 并等待重试。 */
    private String failReason;
}
