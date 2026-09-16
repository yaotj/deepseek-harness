package com.chinasofti.huateng.model.cardpool;

/** Confirms or releases a previously created logical-card reservation. */
public class CardPoolReservationActionReqDTO {
    private String businessId;

    /**
     * 获取本次预占对应的业务流水号。
     *
     * @return 业务流水号，卡池按该值定位待确认或待释放的预占记录
     */
    public String getBusinessId() { return businessId; }

    /**
     * 设置本次预占对应的业务流水号。
     *
     * @param businessId 业务流水号，MUST 与创建预占时使用的值一致，否则无法命中预占记录
     */
    public void setBusinessId(String businessId) { this.businessId = businessId; }
}
