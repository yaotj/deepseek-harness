package com.chinasofti.huateng.recon.controller;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.recon.ReconSourceCompleteReqDTO;
import com.chinasofti.huateng.recon.model.BatchView;
import com.chinasofti.huateng.recon.model.CreateBatchRequest;
import com.chinasofti.huateng.recon.model.PartReceipt;
import com.chinasofti.huateng.recon.model.ReconBatchStatus;
import com.chinasofti.huateng.recon.model.ReconFileType;
import com.chinasofti.huateng.recon.model.SourceProgress;
import com.chinasofti.huateng.recon.service.ReconBatchService;
import com.chinasofti.huateng.recon.service.ReconOrchestrationService;
import com.chinasofti.huateng.recon.service.ReconPartService;
import com.chinasofti.huateng.recon.service.ReconFileGenerationService;
import com.chinasofti.huateng.recon.service.ReconFileTransferService;
import com.chinasofti.huateng.recon.service.ReconSourceService;
import com.chinasofti.huateng.recon.model.ReconFileView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 对账内部接口：仅供源服务与运维调用。
 *
 * <p><b>【开发测试阶段：本组接口当前无鉴权，上线前 MUST 恢复】</b>用户 2026-09-11 明确要求
 * 「删除令牌要求，不用令牌了，当前处于开发测试阶段」，故原先所有端点上的 {@code X-Recon-Token}
 * 定长比较已整段删除。这里含分片接收、状态变更、生成与投递等状态变更型端点，无鉴权状态下
 * 任何网络可达方都能改批次状态或塞入分片，与 AGENTS.md §5.2 相冲突，属**有意为之的临时降级**。
 * 恢复时把 {@code recon.internal-token} 与请求头的 {@link java.security.MessageDigest#isEqual}
 * 比较加回每个端点即可。</p>
 */
@RestController
@RequestMapping("/internal/recon")
public class ReconInternalController {
    private static final Logger log = LoggerFactory.getLogger(ReconInternalController.class);

    private final ReconBatchService batchService;
    private final ReconPartService partService;
    private final ReconFileGenerationService fileGenerationService;
    private final ReconFileTransferService fileTransferService;
    private final ReconSourceService sourceService;
    private final ReconOrchestrationService orchestrationService;

    public ReconInternalController(ReconBatchService batchService,
                                   ReconPartService partService,
                                   ReconFileGenerationService fileGenerationService,
                                   ReconFileTransferService fileTransferService,
                                   ReconSourceService sourceService,
                                   ReconOrchestrationService orchestrationService) {
        this.batchService = batchService;
        this.partService = partService;
        this.fileGenerationService = fileGenerationService;
        this.fileTransferService = fileTransferService;
        this.sourceService = sourceService;
        this.orchestrationService = orchestrationService;
    }
    @PostMapping("/batches")
    public BatchView createBatch(@Valid @RequestBody CreateBatchRequest request) {
        return batchService.create(request);
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchView> getBatch(@PathVariable String batchId) {
        BatchView view = batchService.get(batchId);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /**
     * 直接改批次状态。
     *
     * <p><b>仅供人工干预</b>：正常链路的状态推进一律由编排器按收齐结果决定，
     * 手工改状态会绕过收齐校验，MUST 在确认数据一致后才使用。</p>
     */
    @PutMapping("/batches/{batchId}/status")
    public BatchView updateStatus(@PathVariable String batchId,
                                  @RequestParam ReconBatchStatus status) {
        return batchService.transition(batchId, status);
    }

    /**
     * 跑完一次完整的日终对账，供 web-admin 的 Quartz 每日调用。
     *
     * <p>这是**本模块唯一的日常触发入口**——recon-server 已删掉 {@code @EnableScheduling}，
     * 频率完全由 web-admin 的 {@code sys_job} 控制（见 {@code docs/architecture/web-server.md} §七）。
     * 同步跑完再返回，失败或超时返回非 {@code 0000}，让 {@code sys_job_log} 能反映真实成败。</p>
     *
     * <p>返回 {@link CommonResult} 而不是 {@code BatchView}：调用方是 Quartz 任务，
     * 只需要「成/败 + 原因」，并且要与 web-admin 侧其它任务的判据保持一致（`retCode` 是否 0000）。
     * 批次明细仍可用 {@code GET /internal/recon/batches/{batchId}} 查。</p>
     */
    @PostMapping("/daily/run")
    public CommonResult runDailyBatch() {
        CommonResult result = new CommonResult();
        try {
            BatchView batch = orchestrationService.runDailyBatch();
            result.setRetCode("0000");
            result.setRetMsg("日终对账已完成: batchId=" + batch.batchId() + ", status=" + batch.status());
            return result;
        } catch (RuntimeException ex) {
            log.error("日终对账运行失败 msg={}", ex.getMessage(), ex);
            result.setRetCode("9999");
            result.setRetMsg("日终对账运行失败: " + ex.getMessage());
            return result;
        }
    }

    @PostMapping(value = "/batches/{batchId}/sources/{source}/files/{fileType}/parts/{partNo}", consumes = "application/octet-stream")
    public PartReceipt receivePart(@PathVariable String batchId,
                                   @PathVariable String source,
                                   @PathVariable ReconFileType fileType,
                                   @PathVariable int partNo,
                                   @RequestParam long recordCount,
                                   @RequestParam(defaultValue = "0") long amountTotal,
                                   @RequestParam String sha256,
                                   HttpServletRequest request) throws IOException {
        return partService.receive(batchId, source, fileType, partNo, recordCount, amountTotal, sha256,
                request.getInputStream());
    }

    /**
     * 源服务声明某个 {@code (来源, 文件类型)} 的分片已全部上送，触发三项总账收齐校验。
     */
    @PostMapping("/batches/{batchId}/sources/{source}/files/{fileType}/complete")
    public SourceProgress completeSource(@PathVariable String batchId,
                                         @PathVariable String source,
                                         @PathVariable ReconFileType fileType,
                                         @RequestBody ReconSourceCompleteReqDTO request) {
        return sourceService.declare(batchId, source, fileType, request);
    }

    /** 查看某批次各来源的收齐进度。 */
    @GetMapping("/batches/{batchId}/sources")
    public List<SourceProgress> listSources(@PathVariable String batchId) {
        return sourceService.list(batchId);
    }

    /** 人工推进单个批次，等价于编排器的一轮推进。 */
    @PostMapping("/batches/{batchId}/advance")
    public BatchView advance(@PathVariable String batchId) {
        return orchestrationService.advanceBatch(batchId);
    }

    @PostMapping("/batches/{batchId}/files/{fileType}/generate")
    public ReconFileView generateFile(@PathVariable String batchId,
                                      @PathVariable ReconFileType fileType) throws IOException {
        return fileGenerationService.generate(batchId, fileType);
    }

    @PostMapping("/batches/{batchId}/files/{fileType}/upload")
    public ReconFileView uploadFile(@PathVariable String batchId,
                                    @PathVariable ReconFileType fileType) throws IOException {
        return fileTransferService.upload(batchId, fileType);
    }
}
