package com.chinasofti.huateng.recon.model;


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
