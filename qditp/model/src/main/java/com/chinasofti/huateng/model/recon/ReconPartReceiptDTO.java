package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 分片接收回执（IF-RECON-02 的响应）。
 *
 * <p>幂等语义：同一 {@code (batchId, source, fileType, partNo)} 重复上送，哈希相同即原样返回
 * 首次回执（{@code duplicated=true}），哈希不同则服务端拒收。因此源服务重试 MUST 复用相同分片号，
 * NEVER 换号重传——换号会让同一批数据在最终文件里出现两次。</p>
 */
@Data
public class ReconPartReceiptDTO {

    private String batchId;

    private String source;

    private String fileType;

    private int partNo;

    private long byteCount;

    private long recordCount;

    /** 金额合计，单位分。 */
    private long amountTotal;

    private String sha256;

    private String status;

    /** 是否命中幂等（服务端此前已收过同哈希分片）。 */
    private boolean duplicated;
}
