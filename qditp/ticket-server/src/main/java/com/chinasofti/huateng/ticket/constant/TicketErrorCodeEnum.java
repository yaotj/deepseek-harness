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
     * IF8A-29 查询用户行程时「上次行程为空」（2026-09-22 业主裁决新增；2026-11-16 口径收窄）。
     *
     * <p><b>唯一触发条件：该卡没有任何过闸明细</b>（进出都没有，`selectLatestByCardId` 返回 null）。
     * 有交易一律返 {@code 0000}——刚进站返进站这笔，刚出站返出站这笔；首次进站时上次站点虽是建行哨兵
     * `FFFF`，但由装配器归一为空串，<b>不再</b>据「上次站点是哨兵」判 8911。
     *
     * <p><b>NEVER 把它当异常码处理</b> —— 这是正常业务态，`memberItinerary` 仍照常返回。
     *
     * <p>历史：D166 曾用「lastStation 是哨兵」判 8911 以规避 APP 渲染 `FFFF` 报「查询失败」，代价是
     * 新卡首次进站明明有进站交易也被误判；2026-11-16 改为哨兵归一空串 + 8911 仅零交易，见 ADR-D174。
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
