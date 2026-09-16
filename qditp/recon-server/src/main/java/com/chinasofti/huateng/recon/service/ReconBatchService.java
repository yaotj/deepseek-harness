package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.recon.mapper.ReconBatchMapper;
import com.chinasofti.huateng.recon.model.BatchView;
import com.chinasofti.huateng.recon.model.CreateBatchRequest;
import com.chinasofti.huateng.recon.model.ReconBatchStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对账批次的创建与状态流转。
 */
@Service
public class ReconBatchService {
    private final ReconBatchMapper batchMapper;

    public ReconBatchService(ReconBatchMapper batchMapper) { this.batchMapper = batchMapper; }

    @Transactional
    public BatchView create(CreateBatchRequest request) {
        batchMapper.insertIfAbsent(request.getBatchId(), request.getBusinessDate(), request.getWindowStart(), request.getWindowEnd());
        BatchView batch = batchMapper.selectById(request.getBatchId());
        if (batch == null) throw new IllegalStateException("对账批次创建失败");
        return batch;
    }

    public BatchView get(String batchId) { return batchMapper.selectById(batchId); }

    /**
     * 幂等的状态推进：目标状态与当前状态相同则原样返回，不抛异常。
     *
     * <p>编排任务每轮都会重算目标状态并调用本方法，若沿用 {@link #transition} 会在每一轮
     * 都因「非法流转」报错刷日志。因此把「已经在目标状态」这一情况判定为成功。</p>
     *
     * @param batchId 批次标识
     * @param target  目标状态
     * @return 推进后的批次视图
     */
    @Transactional
    public BatchView transitionIfNeeded(String batchId, ReconBatchStatus target) {
        BatchView current = get(batchId);
        if (current == null) throw new IllegalArgumentException("对账批次不存在: " + batchId);
        if (current.status() == target) return current;
        return transition(batchId, target);
    }

    @Transactional
    public BatchView transition(String batchId, ReconBatchStatus status) {
        BatchView current = get(batchId);
        if (current == null) throw new IllegalArgumentException("对账批次不存在: " + batchId);
        if (!allowed(current.status(), status)) throw new IllegalStateException("非法的对账批次状态变更: " + current.status() + " -> " + status);
        if (batchMapper.updateStatus(batchId, status.name(), current.status().name()) != 1) {
            throw new IllegalStateException("对账批次状态已被其他请求更新");
        }
        return get(batchId);
    }

    /**
     * 状态流转白名单。SUCCESS 是终态，一律拒绝流出；FAILED 是四个补偿入口的起点。
     *
     * <p><b>FAILED -&gt; ALL_SOURCE_COMPLETED 必须在白名单里</b>：批次在「来源已全部收齐、但生成或投递
     * 失败」时会落到 FAILED，下一轮 {@code advanceBatch} 走的是
     * {@code transitionIfNeeded(ALL_SOURCE_COMPLETED)} 再重新生成。少了这一条，FAILED 批次只能靠
     * {@code dispatchDailyBatch} 的 FAILED -&gt; EXPORTING 复活，而生产 cron 一天只跑一次且 batchId
     * 按账期变化，昨天失败的批次今天不会再被下发，于是**永久卡在 FAILED、每轮只刷
     * 「非法的对账批次状态变更」错误日志**。2026-09-11 实测复现（batchId=RECON20260907）。</p>
     *
     * <p><b>PARTIAL -&gt; EXPORTING 也必须在白名单里</b>：`runDailyBatch()` 每天（或人工重跑）都会先
     * `dispatchDailyBatch()`，而它对已存在的批次做 `transitionIfNeeded(EXPORTING)`。批次只要停在
     * PARTIAL（部分来源收齐），少了这一条重入就直接抛「非法的对账批次状态变更: PARTIAL -&gt; EXPORTING」，
     * 接口返回 9999、**那个账期再也补不回来**。2026-09-11 实测（batchId=RECON20260909 停在 PARTIAL）。</p>
     */
    private boolean allowed(ReconBatchStatus from, ReconBatchStatus to) {
        return switch (from) {
            case CREATED -> to == ReconBatchStatus.EXPORTING || to == ReconBatchStatus.FAILED;
            case EXPORTING -> to == ReconBatchStatus.PARTIAL
                    || to == ReconBatchStatus.ALL_SOURCE_COMPLETED
                    || to == ReconBatchStatus.FAILED;
            case PARTIAL -> to == ReconBatchStatus.ALL_SOURCE_COMPLETED
                    || to == ReconBatchStatus.FAILED
                    || to == ReconBatchStatus.EXPORTING;
            case ALL_SOURCE_COMPLETED -> to == ReconBatchStatus.GENERATING || to == ReconBatchStatus.FAILED;
            case GENERATING -> to == ReconBatchStatus.UPLOADING || to == ReconBatchStatus.FAILED;
            case UPLOADING -> to == ReconBatchStatus.SUCCESS || to == ReconBatchStatus.FAILED;
            case FAILED -> to == ReconBatchStatus.EXPORTING
                    || to == ReconBatchStatus.ALL_SOURCE_COMPLETED
                    || to == ReconBatchStatus.GENERATING
                    || to == ReconBatchStatus.UPLOADING;
            case SUCCESS -> false;
        };
    }
}
