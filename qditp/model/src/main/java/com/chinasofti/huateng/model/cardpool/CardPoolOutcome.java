package com.chinasofti.huateng.model.cardpool;

/**
 * 逻辑卡号池调用结果分类。
 *
 * <p>此前 {@code CardPoolClient} 用 {@code null} / {@code false} 表达全部失败，调用方无法区分
 * 「池里真的没号了」「票种压根不走卡池（属程序缺陷）」「card-pool-server 不可达（可重试）」三件事，
 * 只能一律翻译成「无可分配逻辑卡号」，把配置错误和网络故障都报成卡池耗尽。本枚举把这三类拆开。</p>
 */
public enum CardPoolOutcome {

    /**
     * 调用成功，结果数据可用。
     */
    SUCCESS,

    /**
     * 该票种卡池已空。属正常业务结果，调用方可提示稍后重试或走降级发卡。
     */
    POOL_EMPTY,

    /**
     * 服务端拒绝：票种不走卡池、票种非法、业务归属缺失或归属冲突。
     * 属调用方参数或配置问题，重试无用，MUST 告警。
     */
    REJECTED,

    /**
     * 调用未能完成：连接失败、超时、响应无法解析或服务端内部错误。可重试。
     */
    CALL_FAILED
}
