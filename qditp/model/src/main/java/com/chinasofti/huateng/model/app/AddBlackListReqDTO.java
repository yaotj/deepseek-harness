package com.chinasofti.huateng.model.app;

/**
 * 新增黑名单请求参数。
 *
 * <p>本 DTO 是 blacklist-server 的对内 RPC 契约（非 parseBizData 解析的对外契约）。
 * 加字段后 MUST 重建链路上每一个经手它的模块镜像：blacklist-server、fep-alipay-server、
 * pay-sign-server、alipay-pay-sign-server —— model 版本号恒为 2.0.0，旧镜像里是旧 class，
 * Fastjson2 会静默丢弃它不认识的字段。</p>
 */
public class AddBlackListReqDTO {
    /**
     * 卡ID，唯一业务键，必填。
     */
    private String cardId;

    /**
     * 三方用户ID，审计冗余，不参与命中判定。
     */
    private String thirdUserId;

    /**
     * 卡类型编码（票种，如0441），出向通知契约需要。
     */
    private String cardType;

    /**
     * 业务渠道：01地铁APP，02支付宝，99未知。
     *
     * <p>拿不到时传 99，NEVER 猜一个值 —— 错的枚举值比空值更难排查。</p>
     */
    private String channelCode;

    /**
     * 发起方：01系统自动，02渠道通知，09运维手工。
     */
    private String blackSource;

    /**
     * 拉黑原因：01未付费欠费，02挂失补卡，09其他。
     *
     * <p>只有 01 参与欠费结清盘点；02 NEVER 按欠费结清自动解除。</p>
     */
    private String blackCause;

    /**
     * 关联业务单号，语义由 channelCode 与 blackSource 共同决定，可空。
     */
    private String bizNo;

    /**
     * 拉黑备注，自由文本，非判定依据。
     */
    private String reason;

    /**
     * 操作者：运维手工记管理员账号，系统触发记服务名。
     */
    private String createBy;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getBlackSource() {
        return blackSource;
    }

    public void setBlackSource(String blackSource) {
        this.blackSource = blackSource;
    }

    public String getBlackCause() {
        return blackCause;
    }

    public void setBlackCause(String blackCause) {
        this.blackCause = blackCause;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    @Override
    public String toString() {
        return "AddBlackListReqDTO{cardId='" + cardId + "', thirdUserId='" + thirdUserId
                + "', cardType='" + cardType + "', channelCode='" + channelCode
                + "', blackSource='" + blackSource + "', blackCause='" + blackCause
                + "', bizNo='" + bizNo + "', reason='" + reason + "', createBy='" + createBy + "'}";
    }
}
