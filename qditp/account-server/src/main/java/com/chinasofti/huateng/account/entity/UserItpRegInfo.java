package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;

/**
 * User_ITP_Reg_Info 用户注册表实体。
 */
public class UserItpRegInfo {
    /**
     * 主键。
     */
    private Integer id;

    /**
     * 逻辑卡号。
     */
    private String cardId;

    /**
     * 转换后的卡类型。
     */
    private String cardType;

    /**
     * APP 入参卡类型。
     */
    private String itpCardType;


    /**
     * 第三方用户编码。
     */
    private String thirdUserId;

    /**
     * 手机号。
     */
    private String msisdn;

    /**
     * 注册时间。
     */
    private LocalDateTime regTms;

    /**
     * 删除标志。<b>极性反直觉：{@code 1}=有效、{@code 0}=已注销</b>（见
     * {@code docs/domain/state-machines.md} 在跑的状态机 #3）。
     *
     * <p>判活 <b>MUST</b> 走 {@link #isActive()} / {@link #isCanceled()}，
     * <b>NEVER</b> 在业务代码里裸写 {@code getDelYn() != 1}。</p>
     */
    private Integer delYn;

    /**
     * 删除操作对应的第三方用户编码。
     */
    private String delThirdUserId;

    /**
     * 注销时间。
     */
    private LocalDateTime unRegTms;

    /**
     * 用户姓名。
     */
    private String userName;

    /**
     * 证件号。
     */
    private String userId;

    /**
     * 发行渠道编码，仅 {@code 0001}(正常渠道) / {@code 0007}(支付宝出行)。
     *
     * <p>由 {@code CardIssueOrgEnum.toIssueChannelCode4} 从 APP 上送的机构码归一而来；
     * 码体的「发行渠道位」是 industry-data-server 对本值取右 2 位（{@code 07} / {@code 01}）。
     * <b>NEVER 把 APP 原值直接写进这里</b>——那会让码体落到非法渠道值，见 B14。</p>
     */
    private String cardIssueCode;

    /**
     * 发卡机构编码，APP 开户上送的 4 位原值（{@code 5412} 青岛地铁 / {@code 0007} 支付宝出行 等）。
     *
     * <p>只作留痕与后续统计用，<b>不参与码体拼装</b>。取值字典见 {@code CardIssueOrgEnum}。</p>
     */
    private String issueOrgCode;

    /**
     * 第三方支付渠道用户标识。
     */
    private String thirdPayId;

    /**
     * 渠道编码。
     */
    private String channel;

    /**
     * 签约请求号。
     */
    private String reqContractNo;

    /**
     * HCE 卡数据。开户时由安全服务生成，闸机交易后由 IF1A-01 reserve1 更新。
     */
    private String hceData;

    /**
     * 同行票或第三方票标识。
     */
    private String companionFlag;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getItpCardType() {
        return itpCardType;
    }

    public void setItpCardType(String itpCardType) {
        this.itpCardType = itpCardType;
    }


    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public LocalDateTime getRegTms() {
        return regTms;
    }

    public void setRegTms(LocalDateTime regTms) {
        this.regTms = regTms;
    }

    public Integer getDelYn() {
        return delYn;
    }

    public void setDelYn(Integer delYn) {
        this.delYn = delYn;
    }

    /**
     * 该开户记录是否有效（{@code DEL_YN = 1}）。null 一律按<b>无效</b>处理。
     *
     * <p>ADR-D36 从 7 处逐字节相同的 {@code regInfo.getDelYn() == null || getDelYn() != 1}
     * 收敛而来。收敛的理由不是「少写几行」，而是<b>这一列的极性反直觉</b>
     * （{@code 1}=有效 / {@code 0}=已注销），散在 5 个类里逐处手写迟早写反，
     * 而写反的后果是「已注销用户被当成有效用户放行」——静默、且单测不覆盖时发现不了。</p>
     */
    public boolean isActive() {
        return delYn != null && delYn == 1;
    }

    /**
     * 该开户记录是否已注销（{@code DEL_YN = 0}）。
     *
     * <p><b>与 {@code !isActive()} 不等价</b>：{@code delYn} 为 null 时两者都返回 false /
     * true 各一次。销户归档的三条件判定要求「该用户所有记录都<b>确实</b>是注销态」，
     * 因此 MUST 用本方法而不是取 {@code isActive()} 的反。</p>
     */
    public boolean isCanceled() {
        return delYn != null && delYn == 0;
    }

    public String getDelThirdUserId() {
        return delThirdUserId;
    }

    public void setDelThirdUserId(String delThirdUserId) {
        this.delThirdUserId = delThirdUserId;
    }

    public LocalDateTime getUnRegTms() {
        return unRegTms;
    }

    public void setUnRegTms(LocalDateTime unRegTms) {
        this.unRegTms = unRegTms;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }

    public String getIssueOrgCode() {
        return issueOrgCode;
    }

    public void setIssueOrgCode(String issueOrgCode) {
        this.issueOrgCode = issueOrgCode;
    }

    public String getThirdPayId() {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId) {
        this.thirdPayId = thirdPayId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }
}
