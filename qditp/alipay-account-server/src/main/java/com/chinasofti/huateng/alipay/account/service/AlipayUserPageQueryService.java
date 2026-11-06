package com.chinasofti.huateng.alipay.account.service;

import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchQuery;
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
     * 按并列筛选条件（手机号 / 逻辑卡号 / 第三方用户 ID / 渠道签约流水号，条件之间是 AND）
     * 分页检索未逻辑删除（{@code DELETE_FLAG='0'}）的支付宝注册用户，返回**脱敏后**的视图。
     *
     * @param query 筛选条件；空白字段不参与谓词，调用方 MUST 保证至少一个非空
     * @return 分页结果；查不到即 {@code total=0} 的空页
     */
    PageInfo<AlipayUserSearchView> search(AlipayUserSearchQuery query);
}
