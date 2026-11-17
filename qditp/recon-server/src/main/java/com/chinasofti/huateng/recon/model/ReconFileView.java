package com.chinasofti.huateng.recon.model;


public record ReconFileView(String batchId,
                            ReconFileType fileType,
                            String fileName,
                            String path,
                            Long byteCount,
                            Long recordCount,
                            Long amountTotal,
                            String sha256,
                            String status,
                            String remotePath) { }
