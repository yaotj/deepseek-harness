package com.chinasofti.huateng.model.cardpool;

/**
 * 逻辑卡号池调用结果分类。
 */
public enum CardPoolOutcome {

    /**
     * 调用成功，结果数据可用。
     */
    SUCCESS,

    /**
     * 该票种卡池已空。
     */
    POOL_EMPTY,

    /**
     * 服务端拒绝：票种不走卡池、票种非法、业务归。
     */
    REJECTED,

    /**
     * 调用未能完成：连接失败、超时、响应无法解析或服务端内部错误。可重试。
     */
    CALL_FAILED
}
