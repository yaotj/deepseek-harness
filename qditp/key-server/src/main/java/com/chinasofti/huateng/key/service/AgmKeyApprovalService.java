package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.common.response.ResultVO;

/**
 * AGM 密钥版本审批状态机（综管台操作型入口，安全红线，MUST 经人工安全合规复核后上线）。
 *
 * <p>只放行 {@code 20010 初始化} 单向迁到 {@code 20020 审批通过} / {@code 20030 拒绝}，终态不可再迁；
 * 用数据库 CAS（WHERE 带当前状态）兜并发。本服务只改审批状态元信息，<b>不生成、不返回、不触碰任何密钥材料</b>。</p>
 */
public interface AgmKeyApprovalService {

    /**
     * 审批某接入方某批次版本的 AGM 密钥版本。
     *
     * @param providerId  接入方编码
     * @param batchNumber 密钥批次号（KEY_BATH_NUMBER）
     * @param targetStatus 目标状态码：20020 审批通过 / 20030 拒绝
     * @param operator    操作人（写 MANAGER_ID 审计）
     * @return 审批结果；并发冲突 / 非法迁移 / 记录不存在均返回明确错误，MUST 不静默吞掉
     */
    ResultVO<Void> approve(String providerId, Long batchNumber, String targetStatus, String operator);
}
