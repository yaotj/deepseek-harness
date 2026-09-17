package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.ReconExportService;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** recon-server 下发抽取指令的内部入口（IF-RECON-01 的 daily-ticket 侧）。 */
@RestController
@RequestMapping("/internal/recon")
public class ReconExportController {

    private static final Logger log = LoggerFactory.getLogger(ReconExportController.class);

    private final ReconExportService reconExportService;

    public ReconExportController(ReconExportService reconExportService) {
        this.reconExportService = reconExportService;
    }

    /**
     * 受理抽取指令。
     *
     * @param request 抽取指令
     * @return 受理或回绝
     */
    @PostMapping("/export")
    public ReconExportRespDTO export(@RequestBody ReconExportReqDTO request) {
        boolean accepted = reconExportService.submit(request);
        if (!accepted) {
            return ReconExportRespDTO.rejected("同批次抽取仍在途，本次指令已丢弃 batchId=" + request.getBatchId());
        }
        log.info("受理对账抽取指令 batchId={}, businessDate={}, window=[{}, {}), fileTypes={}",
                request.getBatchId(), request.getBusinessDate(),
                request.getWindowStart(), request.getWindowEnd(), request.getFileTypes());
        return ReconExportRespDTO.accepted("已排入抽取队列 batchId=" + request.getBatchId());
    }
}
