package com.chinasofti.huateng.ticket.constant;

public enum TicketErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "业务处理过程中出现异常"),
    INVALID_PARAM("8001", "请求参数验证失败"),
    QR_CODE_NOT_FOUND("8004", "未注册用户"),
    PARTNER_AUTH_FAILED("8007", "合作伙伴验证失败"),
    USER_CANCEL_REVIEW("8008", "用户状态为解约审核中"),
    CARD_MISMATCH("8006", "用户卡号与请求参数不一致"),
    CODE_STATUS_NORMAL("8301", "码状态正常，无需更新"),
    EXIT_STATION_SUCCESS("8302", "补出站成功"),
    ENTRY_STATION_SUCCESS("8303", "补进站成功"),
    NO_ENTRY_RECORD("8304", "没有进站记录，无法补出站"),
    CARD_STATUS_CHANGED("8305", "票卡状态已变更，请重新做票卡分析后重试"),
    AGM_RETURN_STATUS_ABNORMAL1("8401", "AGM返回值状态异常"),
    AGM_RETURN_STATUS_ABNORMAL2("8402", "AGM返回值状态异常"),
    GATE_COMM_ERROR("8403", "闸机通讯异常，本次结果未知，请勿直接重试，先查询票卡状态"),
    ACC_COMM_ERROR("8501", "ACC通讯异常"),
    ACC_RETURN_STATUS_ABNORMAL("8502", "ACC返回值状态异常"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    NO_DATA("8002", "无数据"),
    /**
     * IF8A-29 查询用户行程时「上次行程为空」（2026-09-22 业主裁决新增）。
     *
     * <p>用于「本次行程有、上次行程没有」这一形态，典型是**新卡首次进站**：`QRCODE_STATUS.LAST_TXN_STATION`
     * 还是建行时的哨兵 `FFFF`（`ticket.default-last-txn-station`），于是 `lastStationName` 会把 `FFFF`
     * 当站名吐给 APP，APP 拿它渲染就报「查询失败」（2026-09-22 日票卡 `0426090951000084` 实测）。
     *
     * <p><b>NEVER 把它当异常码处理</b> —— 这是正常业务态，`memberItinerary` 里的本次行程字段仍然有效、仍会返回。
     */
    LAST_ITINERARY_EMPTY("8911", "上次行程为空"),
    ENTRY_TXN_NOT_FOUND("8004", "未找到同序列号进站交易");

    private final String code;
    private final String msg;

    TicketErrorCodeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    TicketErrorCodeEnum() {
        this.code = "9999";
        this.msg = "业务处理过程中出现异常";
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
