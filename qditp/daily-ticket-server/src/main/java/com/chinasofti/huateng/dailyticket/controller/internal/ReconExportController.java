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

/**
 * recon-server 下发抽取指令的内部入口（IF-RECON-01 的 daily-ticket 侧）。
 *
 * <p><b>【开发测试阶段：本接口当前无鉴权，上线前 MUST 恢复】</b>用户 2026-09-11 明确要求
 * 「删除令牌要求，不用令牌了，当前处于开发测试阶段」，故原先的 {@code X-Recon-Token}
 * 共享令牌校验已整段删除。本模块没有 spring-security、也没有全局验签拦截器，因此现状等于
 * 允许任何网络可达方触发全量导出，与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」
 * 相冲突，属**有意为之的临时降级**。</p>
 *
 * <p>恢复方式：重新引入 {@code recon.internal-token} 配置与请求头 {@code X-Recon-Token}
 * 的逐字节比对，**MUST 用 {@link java.security.MessageDigest#isEqual} 而不是
 * {@code equals}**，避免按字符短路带来的时序侧信道。</p>
 *
 * <p>本接口只受理、不干活：抽取由 {@link ReconExportService} 丢到自己的平台线程池执行，
 * 这里**必须立即返回**。若在请求线程上同步跑抽取，阻塞的 ojdbc8 调用会 pin 住虚拟线程的载体线程，
 * 严重时全 JVM 虚拟线程停止调度。</p>
 */
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
