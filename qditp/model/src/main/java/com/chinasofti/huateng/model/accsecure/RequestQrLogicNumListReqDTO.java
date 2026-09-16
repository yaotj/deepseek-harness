package com.chinasofti.huateng.model.accsecure;

/**
 * IF7B-01 请求逻辑卡号（ACC 安全服务）请求报文。
 *
 * <p>与 {@code acc-secure-server} 的 {@code /ci/acc/secure/requestQrLogicNumList} 入参一一对应，
 * 放在 model 模块以便 rpc 客户端与调用方共用同一份契约，避免各调用方手工拼 JSON 键名。</p>
 */
public class RequestQrLogicNumListReqDTO {
    /**
     * 请求数量，规格默认 10 万。
     */
    private String requestNum;

    /**
     * 请求流水号，与批次号一一对应，用于 ACC 侧幂等。
     */
    private String requestSeq;

    /**
     * ACC 票种（4 位票种码的后两位）。
     */
    private String ticketType;

    /**
     * 获取本次向 ACC 申请的逻辑卡号数量。
     *
     * @return 申请数量，规格默认 10 万
     */
    public String getRequestNum() {
        return requestNum;
    }

    /**
     * 设置本次向 ACC 申请的逻辑卡号数量。
     *
     * @param requestNum 申请数量，规格默认 10 万
     */
    public void setRequestNum(String requestNum) {
        this.requestNum = requestNum;
    }

    /**
     * 获取申请流水号。
     *
     * @return 申请流水号，与批次号一一对应，ACC 侧按整数解析，MUST 为纯数字
     */
    public String getRequestSeq() {
        return requestSeq;
    }

    /**
     * 设置申请流水号。
     *
     * @param requestSeq 申请流水号，与批次号一一对应，ACC 侧按整数解析，MUST 为纯数字
     */
    public void setRequestSeq(String requestSeq) {
        this.requestSeq = requestSeq;
    }

    /**
     * 获取 ACC 票种码。
     *
     * @return 2 位 ACC 票种码，即 044X 全票种码的后两位
     */
    public String getTicketType() {
        return ticketType;
    }

    /**
     * 设置 ACC 票种码。
     *
     * @param ticketType 2 位 ACC 票种码，即 044X 全票种码的后两位，可由
     *                   {@link com.chinasofti.huateng.model.cardpool.CardPoolTicketType#toAccTicketType(String)} 转换得到
     */
    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }
}
