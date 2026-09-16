package com.chinasofti.huateng.recon.model;

/**
 * 单个 {@code (批次, 来源, 文件类型)} 的抽取进度状态。
 */
public enum ReconSourceStatus {

    /** 已登记期望，尚未下发指令。 */
    PENDING,

    /** 指令已下发并被源服务受理，等待分片与完成声明。 */
    EXPORTING,

    /** 源已声明完成且三项校验通过。 */
    COMPLETED,

    /** 源声明失败，或下发指令被回绝，等待重试。 */
    FAILED,

    /**
     * 源声明完成但校验不通过（分片数 / 记录数 / 金额与落库不符）。
     *
     * <p>这是**不可自动放行**的状态：数据已经不一致，重试同一窗口也只会重复不一致，MUST 人工介入。</p>
     */
    MISMATCH
}
