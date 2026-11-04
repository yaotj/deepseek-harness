package com.chinasofti.huateng.blacklist.entity;

import java.time.LocalDateTime;

/** 灰名单表实体（观察/预警名单，不拦截、不渠道同步），流水见 {@link GraylistOperateLog}。 */
public class Graylist {

    /** 主键ID。 */
    private Long id;

    /** 卡ID，唯一业务键。 */
    private String cardId;

    /** 三方用户ID，审计冗余。 */
    private String thirdUserId;

    /** 卡类型编码（票种，如 0441）。 */
    private String cardType;

    /** 发起方：01 系统自动，09 运维手工。 */
    private String graySource;

    /** 关联业务单号，可空。 */
    private String bizNo;

    /** 进灰备注，自由文本。 */
    private String reason;

    /** 操作者。 */
    private String createBy;

    /** 进灰时间。 */
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public String getGraySource() { return graySource; }
    public void setGraySource(String graySource) { this.graySource = graySource; }

    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
