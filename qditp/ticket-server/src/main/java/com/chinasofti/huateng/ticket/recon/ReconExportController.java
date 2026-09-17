package com.chinasofti.huateng.ticket.recon;

import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** recon-server 下发抽取指令的内部入口（IF-RECON-01 的 ticket 源实现）。 */
@RestController
@RequestMapping("/internal/recon")
public class ReconExportController {

    private final ReconExportService reconExportService;

    public ReconExportController(ReconExportService reconExportService) {
        this.reconExportService = reconExportService;
    }

    /**
     * 受理抽取指令，立即返回，不等抽取完成。
     *
     * @param request 抽取指令
     * @return 受理结果
     */
    @PostMapping("/export")
    public ReconExportRespDTO export(@RequestBody ReconExportReqDTO request) {
        boolean accepted = reconExportService.submit(request);
        return accepted
                ? ReconExportRespDTO.accepted("已受理")
                : ReconExportRespDTO.rejected("上一轮同批次抽取仍在执行");
    }
}
