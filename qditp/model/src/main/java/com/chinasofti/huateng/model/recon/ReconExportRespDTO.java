package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 源服务对抽取指令的**受理**响应。
 */
@Data
public class ReconExportRespDTO {

    /** 是否受理。false 表示上一轮同批次抽取仍在跑，本次被丢弃（限流，不是失败）。 */
    private boolean accepted;

    /** 受理或回绝的。 */
    private String message;

    /**
     * 构造受理成功的响应。
     * @param message 说明。
     * @return 响应。
     */
    public static ReconExportRespDTO accepted(String message) {
        ReconExportRespDTO response = new ReconExportRespDTO();
        response.setAccepted(true);
        response.setMessage(message);
        return response;
    }

    /**
     * 构造被回绝的响应。
     * @param message 原因。
     * @return 响应。
     */
    public static ReconExportRespDTO rejected(String message) {
        ReconExportRespDTO response = new ReconExportRespDTO();
        response.setAccepted(false);
        response.setMessage(message);
        return response;
    }
}
