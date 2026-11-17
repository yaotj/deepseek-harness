package com.chinasofti.huateng.model.recon;

import lombok.Data;


@Data
public class ReconExportRespDTO {

    private boolean accepted;

    private String message;


    public static ReconExportRespDTO accepted(String message) {
        ReconExportRespDTO response = new ReconExportRespDTO();
        response.setAccepted(true);
        response.setMessage(message);
        return response;
    }


    public static ReconExportRespDTO rejected(String message) {
        ReconExportRespDTO response = new ReconExportRespDTO();
        response.setAccepted(false);
        response.setMessage(message);
        return response;
    }
}
