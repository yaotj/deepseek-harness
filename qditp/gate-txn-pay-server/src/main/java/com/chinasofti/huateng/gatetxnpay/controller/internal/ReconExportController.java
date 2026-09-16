package com.chinasofti.huateng.gatetxnpay.controller.internal;

import com.chinasofti.huateng.gatetxnpay.service.ReconExportService;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * recon-server 下发抽取指令的内部入口（IF-RECON-01 的本源实现）。
 *
 * <p><b>【开发测试阶段：本接口当前无鉴权，上线前 MUST 恢复】</b>用户 2026-09-11 明确要求
 * 「删除令牌要求，不用令牌了，当前处于开发测试阶段」，故原先的 {@code X-Recon-Token}
 * 共享令牌校验已整段删除。本项目多数业务模块没有 spring-security、没有全局拦截器兜底，
 * 因此现状等于允许任何网络可达方触发全表扫描级别的批处理，与 AGENTS.md §5.2
 * 「新增状态变更型接口 MUST 有鉴权」相冲突，属**有意为之的临时降级**。</p>
 *
 * <p>恢复方式：重新引入 {@code recon.internal-token} 配置与请求头比对，比较 MUST 用
 * {@link java.security.MessageDigest#isEqual} 做定长时间比较，NEVER 用 {@code String.equals}
 * —— 后者短路返回，可被逐字节计时探测出令牌内容；令牌值由 K8s Secret 注入，
 * NEVER 在仓库里写默认真值。</p>
 */
@RestController
@RequestMapping("/internal/recon")
public class ReconExportController {

    private final ReconExportService reconExportService;

    public ReconExportController(ReconExportService reconExportService) {
        this.reconExportService = reconExportService;
    }

    /**
     * 受理抽取指令，<b>立即返回，不等抽取完成</b>。
     *
     * <p>抽取是分钟级批处理，同步等待会让请求线程长时间阻塞在 ojdbc8 的 {@code synchronized}
     * 方法里、pin 住虚拟线程的载体线程（详见 {@link ReconExportService} 类注释）。
     * 收齐判定由 recon-server 侧的批次状态负责，本响应只表示「已排入队列」。</p>
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
