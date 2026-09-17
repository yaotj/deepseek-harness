package com.chinasofti.huateng.facepay.controller.internal;

import com.chinasofti.huateng.facepay.service.ReconExportService;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 日终对账抽取入口，由 recon-server 下发，不对外暴露。 */
@RestController
@RequestMapping("/internal/recon")
public class ReconExportController {

    private final ReconExportService reconExportService;

    public ReconExportController(ReconExportService reconExportService) {
        this.reconExportService = reconExportService;
    }

    /**
     * 接收一次抽取指令。
     *
     * @param request 批次号 / 账期 / 时间窗口 / 文件类型清单
     * @return {@code accepted=true} 已受理；{@code accepted=false} 同批次上一轮仍在执行（限流，不是失败）
     */
    @PostMapping("/export")
    public ReconExportRespDTO export(@RequestBody ReconExportReqDTO request) {
        boolean accepted = reconExportService.submit(request);
        return accepted ? ReconExportRespDTO.accepted("已受理")
                : ReconExportRespDTO.rejected("上一轮同批次抽取仍在执行");
    }
}
