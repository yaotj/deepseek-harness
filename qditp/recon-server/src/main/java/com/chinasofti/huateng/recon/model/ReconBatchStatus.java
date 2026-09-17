package com.chinasofti.huateng.recon.model;

/**
 * 对账批次状态机（流转白名单见 {@code ReconBatchService.allowed}）。
 *
 * <pre>
 * CREATED -&gt; EXPORTING -&gt; PARTIAL -&gt; ALL_SOURCE_COMPLETED -&gt; GENERATING -&gt; UPLOADING -&gt; SUCCESS
 * FAILED -&gt; EXPORTING | GENERATING | UPLOADING
 * </pre>
 */
public enum ReconBatchStatus {

    /** 批次已建，尚未下发抽取指令。 */
    CREATED,

    /** 已向来源下发抽取指令，等待分片上送。 */
    EXPORTING,

    /** 部分来源已声明完成，仍有来源未收口。 */
    PARTIAL,

    /** 全部来源均已声明完成且分片数、记录数、金额三项校验通过。 */
    ALL_SOURCE_COMPLETED,

    /** 正在合并生成最终文件。 */
    GENERATING,

    /** 正在向 FTP 投递最终文件。 */
    UPLOADING,

    /** 全部最终文件已投递成功，终态。 */
    SUCCESS,

    /** 任一环节失败，等待补偿重试。 */
    FAILED
}
