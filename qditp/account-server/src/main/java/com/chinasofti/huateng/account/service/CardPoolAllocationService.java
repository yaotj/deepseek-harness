package com.chinasofti.huateng.account.service;

/**
 * 开户发号的出网协作者：卡池预占 / 确认 / 释放，以及 HCE 票种向安全服务取卡。
 */
public interface CardPoolAllocationService {
    /**
     * 向 card-pool-server 预占一个逻辑卡号。
     *
     * @param cardType     票种码（044X）
     * @param businessType 业务类型，与 {@code LOGIC_CARD_POOL_CARD.BUSINESS_TYPE} 对应
     * @param businessId   业务流水号，决定幂等边界
     * @param ownerId      卡号归属方，取 thirdUserId
     * @return 预占成功返回卡号与预占标识；否则返回 {@code null}
     */
    CardAllocation reserveFromPool(String cardType, String businessType, String businessId, String ownerId);

    /**
     * 确认预占。
     *
     * @param allocation 预占信息，为 {@code null} 或非卡池发号（HCE）时直接跳过
     * @param scene      日志场景名
     * @return {@code true} 表示确认成功（或本次无预占可确认，属正常跳过）；{@code false} 表示卡池拒绝或抛异常
     */
    boolean confirmReservation(CardAllocation allocation, String scene);

    /**
     * 释放预占。
     *
     * @param allocation 预占信息，为 {@code null} 或非卡池发号（HCE）时直接跳过
     * @param scene      日志场景名
     */
    void releaseReservation(CardAllocation allocation, String scene);

    /**
     * 判断是否为需要通过安全服务发售的 HCE 卡类型。
     *
     * @param cardType APP 卡类型
     * @return {@code true} 表示 HCE卡（03）或新版HCE卡（04）
     */
    boolean isHceCard(String cardType);

    /**
     * 请求安全服务发售 HCE 卡数据并取得逻辑卡号。
     *
     * @param thirdUserId 十进制第三方用户标识
     * @param ticketCard  NFC 票卡类型
     * @return 安全服务生成的逻辑卡号与 HCE 数据；调用失败时返回 {@code null}
     */
    HceCardAllocation requestHceCardData(String thirdUserId, String ticketCard);

    /**
     * 开户时确定的逻辑卡号及 HCE 卡数据。
     */
    record HceCardAllocation(String cardId, String hceData) {
    }

    /**
     * 一次开户的发号结果。
     *
     * @param cardId        逻辑卡号
     * @param hceData       HCE 卡数据，仅 HCE 票种非空
     * @param reservationId 卡池预占标识，仅走卡池发号时非空；为空表示无预占可确认 / 释放
     * @param businessId    预占时使用的业务流水号，确认与释放都要带上供服务端校验归属
     */
    record CardAllocation(String cardId, String hceData, String reservationId, String businessId) {
    }
}
