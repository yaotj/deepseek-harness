package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.page.ItpPayChannelView;
import com.chinasofti.huateng.account.page.ItpUserSearchView;
import com.chinasofti.huateng.account.page.RegStatView;

import java.util.List;

/**
 * 运营后台的非支付宝用户查询（`/page/user/itp` 两个端点的业务层）。
 */
public interface ItpUserQueryService {
    /**
     * 按查询类型检索开户记录（含有效与已注销，不含已归档物理删除的行），返回**脱敏后**的视图。
     *
     * @param queryType 支持 {@code THIRD_USER_ID} / {@code MSISDN} / {@code CARD_ID}
     * @param keyword   已由调用方保证非空；本方法内部会 trim
     * @return 命中的视图列表；<b>{@code null} 表示查询类型不受支持</b>（调用方据此返回参数错误）。
     * <b>NEVER 把 {@code null} 与「查不到」混为一谈</b> —— 查不到是空列表。
     */
    List<ItpUserSearchView> search(String queryType, String keyword);

    /**
     * 查询指定用户 + 票种下已关联的全部支付渠道与解约参数（账号字段已脱敏）。
     *
     * @return 渠道视图列表；<b>{@code null} 表示没找到有效票种注册信息</b>（调用方据此返回参数错误）。
     */
    List<ItpPayChannelView> payChannels(String thirdUserId, String cardId, String cardType);

    /**
     * 注册量统计：按票种分组计数（含有效与已注销），可选注册日期窗。
     *
     * @param startDate 注册日期下限（含），可为空
     * @param endDate   注册日期上限（含），可为空
     * @return 各票种注册量列表（按数量降序），永不返回 {@code null}
     */
    List<RegStatView> regStats(String startDate, String endDate);

    /**
     * 综管台批量导入查询：按逻辑卡号列表批量检索开户记录，返回**脱敏后**的视图
     * （口径与 {@link #search} 的 CARD_ID 分支一致，含有效与已注销）。
     *
     * @return 命中的视图列表，永不返回 {@code null}
     */
    List<ItpUserSearchView> batchSearch(List<String> cardIds);
}
