package com.chinasofti.huateng.recon.model;

public record PartKey(String batchId, String source, ReconFileType fileType, int partNo) {
}
