package com.chinasofti.huateng.facepay.controller.internal;

import com.chinasofti.huateng.facepay.service.ReconExportService;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日终对账抽取入口，由 recon-server 下发，<b>不对外暴露</b>。
 *
 * <p>本服务是对账的第 4 个抽取源，URL 与前三个源逐字一致（{@code POST /internal/recon/export}）：
 * recon-server 侧的 {@code ReconExportClient} 对所有源用同一条路径，<b>NEVER 改这个路径或方法名</b>。
 * 源标识在 {@link ReconExportService} 里是 {@code face-pay}，MUST 与 recon-server
 * {@code application.properties} 的 {@code recon.orchestration.sources[n].name} 完全一致，
 * 否则分片上送时对不上批次、该源永远停在 {@code EXPORTING}。</p>
 *
 * <p><b>受理即返回，抽取是异步的。</b>返回 {@code accepted=true} 只表示指令已入队，
 * 不代表抽取成功；成败由源侧经 {@code ReconPartSink} 的 commit / 失败声明回调给 recon-server。
 * 因此<b>排查「某个源没出账」NEVER 只看本端点的响应</b>，MUST 看本服务日志里的
 * 「PAY / BUS 抽取完成」或「对账抽取失败」，以及 recon-server 的 {@code RECON_BATCH_FILE}。</p>
 *
 * <p><b>本端点当前无鉴权</b>：{@code X-Recon-Token} 校验已按用户 2026-09-11 的明确要求
 * （「删除令牌要求，不用令牌了，当前处于开发测试阶段」）在四个源与 recon-server 侧整段删除，
 * 本源照同一形态实现。这与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」冲突，
 * 属<b>有意为之的临时降级，上线前 MUST 恢复</b>：恢复时用 {@code recon.internal-token} 配置键 +
 * {@code MessageDigest.isEqual} 定长比较（NEVER 用 {@code String.equals}，那会泄漏时序信息），
 * 且四个源与 {@code ReconExportClient} MUST 同批改。</p>
 */
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
