package com.chinasofti.huateng.gatetxnpay.controller.internal;

import com.chinasofti.huateng.gatetxnpay.service.ReconExportService;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/recon")
public class ReconExportController {

    private final ReconExportService reconExportService;

    public ReconExportController(ReconExportService reconExportService) {
        this.reconExportService = reconExportService;
    }


    @PostMapping("/export")
    public ReconExportRespDTO export(@RequestBody ReconExportReqDTO request) {
        boolean accepted = reconExportService.submit(request);
        return accepted
                ? ReconExportRespDTO.accepted("已受理")
                : ReconExportRespDTO.rejected("上一轮同批次抽取仍在执行");
    }
}
