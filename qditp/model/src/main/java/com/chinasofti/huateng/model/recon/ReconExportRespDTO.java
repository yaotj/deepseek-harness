package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 源服务对抽取指令的**受理**响应。
 *
 * <p>{@code accepted=true} 只表示已排入本源的抽取队列，NEVER 据此判断分片已上送完毕——
 * 收齐判定 MUST 以 recon-server 的 {@code RECON_BATCH_SOURCE.STATUS=COMPLETED} 为准。</p>
 */
@Data
public class ReconExportRespDTO {

    /** 是否受理。false 表示上一轮同批次抽取仍在跑，本次被丢弃（限流，不是失败）。 */
    private boolean accepted;

    /** 受理或回绝的原因说明。 */
    private String message;

    /**
     * 构造受理成功的响应。
     *
     * @param message 说明
     * @return 响应
     */
    public static ReconExportRespDTO accepted(String message) {
        ReconExportRespDTO response = new ReconExportRespDTO();
        response.setAccepted(true);
        response.setMessage(message);
        return response;
    }

    /**
     * 构造被回绝的响应。
     *
     * @param message 原因
     * @return 响应
     */
    public static ReconExportRespDTO rejected(String message) {
        ReconExportRespDTO response = new ReconExportRespDTO();
        response.setAccepted(false);
        response.setMessage(message);
        return response;
    }
}
