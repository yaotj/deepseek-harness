package com.chinasofti.huateng.account.service;

/**
 * 销户归档：把已注销且已无支付通道的开户记录迁到 {@code USER_ITP_REG_LOG}（{@code OPER_TYPE=3}）后物理删除。
 */
public interface AccountArchiveService {
    /**
     * 解绑掉最后一个签约渠道后的归档，<b>在调用方的事务内执行</b>。
     */
    void archiveIfLastChannelRemoved(String thirdUserId);

    /**
     * 销户（IF8A-42）后补一次归档尝试，<b>自开独立短事务、失败只记 warn</b>。
     */
    void tryArchiveAfterCancel(String thirdUserId);
}
