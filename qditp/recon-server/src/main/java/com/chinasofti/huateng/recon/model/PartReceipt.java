package com.chinasofti.huateng.recon.model;


public record PartReceipt(String batchId,
                          String source,
                          ReconFileType fileType,
                          Integer partNo,
                          Long byteCount,
                          Long recordCount,
                          Long amountTotal,
                          String sha256,
                          String path,
                          ReconPartStatus status) {
}
