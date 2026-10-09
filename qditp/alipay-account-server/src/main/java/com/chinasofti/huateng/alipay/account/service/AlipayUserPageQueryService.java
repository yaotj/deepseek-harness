package com.chinasofti.huateng.alipay.account.service;

import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchView;
import com.github.pagehelper.PageInfo;

/**
 * 运营后台的支付宝注册用户查询（`/page/user/alipay/search` 的业务层）。
 *
 * <p><b>与 {@code service.impl.AlipayUserQueryService} 不是一回事、NEVER 合并</b>：那个类服务的是
 * 跨模块 RPC 契约 {@code AlipayUserInfoDTO}（只映射 7 个字段、不脱敏），本接口服务的是综管台展示对象
 * {@link AlipayUserSearchView}（手机号与支付标识**必须脱敏**）。两者字段集与脱敏口径都不同。
 */
public interface AlipayUserPageQueryService {
    /**
     * 按查询类型分页检索未逻辑删除（{@code DELETE_FLAG='0'}）的支付宝注册用户，返回**脱敏后**的视图。
     *
     * @param queryType 支持 {@code THIRD_USER_ID} / {@code CARD_ID} / {@code MSISDN}
     * @param keyword   已由调用方保证非空；本方法内部会 trim
     * @param pageNum   页码，非法值按 1 兜底
     * @param pageSize  每页条数，非法值按 10 兜底、上限 100
     * @return 分页结果；<b>{@code null} 表示查询类型不受支持</b>（调用方据此返回参数错误）。
     * <b>NEVER 把 {@code null} 与「查不到」混为一谈</b> —— 查不到是 {@code total=0} 的空页。
     */
    PageInfo<AlipayUserSearchView> search(String queryType, String keyword, Integer pageNum, Integer pageSize);
}
