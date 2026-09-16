package com.chinasofti.huateng.recon.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateBatchRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    private String batchId;
    @NotBlank
    @Pattern(regexp = "\\d{8}")
    private String businessDate;
    @NotBlank
    private String windowStart;
    @NotBlank
    private String windowEnd;

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(String businessDate) {
        this.businessDate = businessDate;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }
}
