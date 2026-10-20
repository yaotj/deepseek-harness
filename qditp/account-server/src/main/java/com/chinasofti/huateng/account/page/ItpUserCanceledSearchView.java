package com.chinasofti.huateng.account.page;

import java.time.LocalDateTime;

/**
 * 已注销用户查询视图，数据来源于 {@code USER_ITP_REG_LOG}。
 *
 * <p>由于 IF8A-42 销户后通道已清空，注销成功即触发归档物理删除，
 * {@code USER_ITP_REG_INFO} 中 {@code DEL_YN=0} 的记录窗口极短，
 * 因此「已注销用户」的唯一持久真源是销户日志（{@code OPER_TYPE=2}）。
 */
public class ItpUserCanceledSearchView {

    /** 日志主键。 */
    private Integer id;

    /** 第三方用户编码。 */
    private String thirdUserId;

    /** 逻辑卡号。 */
    private String cardId;

    /** 卡类型。 */
    private String cardType;

    /** 手机号。 */
    private String msisdn;

    /** 销户时间（{@code USER_ITP_REG_LOG.OPER_DATE_TIME}）。 */
    private LocalDateTime cancelTime;

    /** 操作类型：2=销户，3=归档。 */
    private Integer operType;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public void setCancelTime(LocalDateTime cancelTime) {
        this.cancelTime = cancelTime;
    }

    public Integer getOperType() {
        return operType;
    }

    public void setOperType(Integer operType) {
        this.operType = operType;
    }
}
