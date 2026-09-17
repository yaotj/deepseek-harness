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
     * 状态流转白名单。SUCCESS 是终态，一律拒绝流出；FAILED 是补偿重试的入口。
     *
     * <p>护栏：{@code PARTIAL -&gt; EXPORTING} MUST 在白名单里，缺它那个账期再也补不回来。</p>
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
