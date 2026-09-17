package com.chinasofti.huateng.cardpool.entity;

import java.time.LocalDateTime;

/** 逻辑卡号池明细，对应表 LOGIC_CARD_POOL_CARD。 */
public class LogicCardPoolCard {

    /** 主键，自增。 */
    private Long id;

    /** 逻辑卡号，全局唯一。 */
    private String cardNo;

    /** 来源批次号。 */
    private Long batchNo;

    /** 票种，4 位。 */
    private String cardType;

    /** 状态，AVAILABLE / RESERVED / ASSIGNED。 */
    private String status;

    /** 预占标识，全局唯一。 */
    private String reservationId;

    /** 业务类型，与业务流水号组成唯一归属键。 */
    private String businessType;

    /** 业务流水号。 */
    private String businessId;

    /** 归属方标识。 */
    private String ownerId;

    /** 预占时间。 */
    private LocalDateTime reservedTime;

    /** 预占过期时间，确认后置空。 */
    private LocalDateTime expireTime;

    /** 确认时间。 */
    private LocalDateTime confirmTime;

    /**
     * 读取主键。
     *
     * @return 明细主键，自增
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键。
     *
     * @param id 明细主键，自增
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 读取逻辑卡号。
     *
     * @return 逻辑卡号，全局唯一
     */
    public String getCardNo() {
        return cardNo;
    }

    /**
     * 设置逻辑卡号。
     *
     * @param cardNo 逻辑卡号，全局唯一，重复写入会命中唯一索引
     */
    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    /**
     * 读取来源批次号。
     *
     * @return 该卡号所属的 LOGIC_CARD_POOL_BATCH 批次号
     */
    public Long getBatchNo() {
        return batchNo;
    }

    /**
     * 设置来源批次号。
     *
     * @param batchNo 该卡号所属的 LOGIC_CARD_POOL_BATCH 批次号
     */
    public void setBatchNo(Long batchNo) {
        this.batchNo = batchNo;
    }

    /**
     * 读取票种码。
     *
     * @return 4 位票种码，形如 044X，与所属批次一致
     */
    public String getCardType() {
        return cardType;
    }

    /**
     * 设置票种码。
     *
     * @param cardType 4 位票种码，形如 044X
     */
    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    /**
     * 读取卡号状态。
     *
     * @return AVAILABLE（可用）/ RESERVED（已预占）/ ASSIGNED（已确认分配）
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置卡号状态。
     *
     * @param status AVAILABLE / RESERVED / ASSIGNED，预占超时回收时退回 AVAILABLE
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 读取预占标识。
     *
     * @return 预占标识，全局唯一，确认与释放时按该值定位卡号
     */
    public String getReservationId() {
        return reservationId;
    }

    /**
     * 设置预占标识。
     *
     * @param reservationId 预占标识，全局唯一，由预占动作生成
     */
    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    /**
     * 读取业务类型。
     *
     * @return 业务类型，与业务流水号共同构成卡号归属的唯一键
     */
    public String getBusinessType() {
        return businessType;
    }

    /**
     * 设置业务类型。
     *
     * @param businessType 业务类型，与业务流水号共同构成卡号归属的唯一键
     */
    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    /**
     * 读取业务流水号。
     *
     * @return 发起预占的业务流水号，用于幂等复用同一张卡号
     */
    public String getBusinessId() {
        return businessId;
    }

    /**
     * 设置业务流水号。
     *
     * @param businessId 发起预占的业务流水号
     */
    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    /**
     * 读取归属方标识。
     *
     * @return 卡号最终归属方标识，如用户标识或渠道标识
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * 设置归属方标识。
     *
     * @param ownerId 卡号最终归属方标识，如用户标识或渠道标识
     */
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    /**
     * 读取预占时间。
     *
     * @return 卡号进入 RESERVED 的时间
     */
    public LocalDateTime getReservedTime() {
        return reservedTime;
    }

    /**
     * 设置预占时间。
     *
     * @param reservedTime 卡号进入 RESERVED 的时间
     */
    public void setReservedTime(LocalDateTime reservedTime) {
        this.reservedTime = reservedTime;
    }

    /**
     * 读取预占过期时间。
     *
     * @return 预占过期时间，超时可被回收动作退回 AVAILABLE；确认后置空
     */
    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    /**
     * 设置预占过期时间。
     *
     * @param expireTime 预占过期时间，确认分配后置为 null
     */
    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * 读取确认时间。
     *
     * @return 卡号进入 ASSIGNED 的确认时间
     */
    public LocalDateTime getConfirmTime() {
        return confirmTime;
    }

    /**
     * 设置确认时间。
     *
     * @param confirmTime 卡号进入 ASSIGNED 的确认时间
     */
    public void setConfirmTime(LocalDateTime confirmTime) {
        this.confirmTime = confirmTime;
    }



}
