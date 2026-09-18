package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;

/**
 * 开户收口：把**已组装好**的注册信息落地。
 */
public interface RegistrationCommitService {
    /**
     * 向 ticket-server 注册乘车状态（<b>RPC，MUST 在事务外调用</b>）。
     *
     * <p>NEVER 在失败分支 releaseReservation：预占按 businessId 幂等、是并发请求共享的，超时回收交 {@code sys_job} 107（ADR-D52）。</p>
     *
     * @param regInfo 已组装完成的注册信息
     * @return ticket-server 的响应；<b>{@code null} 表示不可用</b>，调用方 MUST 视为失败
     */
    RegisterRideStatusRespDTO registerRideStatus(UserItpRegInfo regInfo);

    /**
     * 在**独立短事务**里插入 {@code USER_ITP_REG_INFO} 与 {@code USER_ITP_REG_LOG} 两行。
     *
     * @param regInfo 已组装完成的注册信息
     */
    void persistRegistration(UserItpRegInfo regInfo);

    /**
     * 留存 APP / 渠道上送的所属方原值，仅去首尾空格，不做机构字典校验。
     *
     * @param cardIssueCode 上送的发卡机构码
     * @return trim 后的原值
     */
    String normalizeIssueOrgCode(String cardIssueCode);
}
