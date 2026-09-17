package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 源服务向 recon-server 声明「某个 (批次, 来源, 文件类型) 的分片已全部上送完毕」（IF-RECON-03）。
 */
@Data
public class ReconSourceCompleteReqDTO {

    /** 分片总数。 */
    private int totalParts;

    /** 记录总数，即本源本文件类型所有分片的行数之和。 */
    private long totalRecords;

    /** 金额合计，单位分。明细文件为金额列之和，汇总文件为汇总金额之和。 */
    private long totalAmount;

    /** 抽取失败时的。 */
    private String failReason;
}
