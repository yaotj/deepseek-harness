package com.chinasofti.huateng.recon.model;

import java.util.List;

public record ReconBatchProgress(BatchView batch, List<ReconFileView> files) { }
