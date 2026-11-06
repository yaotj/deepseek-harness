package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.page.ItpPayChannelView;
import com.chinasofti.huateng.account.page.ItpUserCanceledSearchView;
import com.chinasofti.huateng.account.page.ItpUserSearchQuery;
import com.chinasofti.huateng.account.page.ItpUserSearchView;
import com.chinasofti.huateng.account.page.RegStatView;

import java.util.List;

/**
 * 运营后台的非支付宝用户查询（`/page/user/itp` 两个端点的业务层）。
 */
public interface ItpUserQueryService {
    /**
     * 按并列筛选条件检索开户记录（不含已归档物理删除的行），返回**脱敏后**的视图。
     *
     * <p>手机号 / 逻辑卡号 / 第三方用户 ID / 渠道签约流水号之间是 AND 组合，空白字段不参与谓词。
     *
     * @param query 筛选条件；{@code status} 已由调用方归一为 {@code ACTIVE} / {@code CANCELED}，
     *              {@code ACTIVE}=只查有效（{@code DEL_YN=1}），{@code CANCELED}=只查已注销（{@code DEL_YN=0}）
     * @return 命中的视图列表，永不返回 {@code null}；查不到即空列表
     */
    List<ItpUserSearchView> search(ItpUserSearchQuery query);

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

    /**
     * 已注销用户查询：数据来源于 {@code USER_ITP_REG_LOG}（{@code OPER_TYPE=2} 销户记录）。
     *
     * <p>由于 IF8A-42 销户后通道已清空，注销成功即触发归档物理删除，
     * {@code USER_ITP_REG_INFO} 中 {@code DEL_YN=0} 的记录窗口极短，
     * 因此「已注销用户」的持久真源是销户日志而非开户表。
     *
     * <p>该表<b>没有签约流水号列</b>，{@code reqContractNo} 在本方法中被忽略，由调用方负责拒绝该条件。
     *
     * @param query 筛选条件（手机号 / 逻辑卡号 / 第三方用户 ID，AND 组合）
     * @return 命中的视图列表，永不返回 {@code null}
     */
    List<ItpUserCanceledSearchView> searchCanceledLog(ItpUserSearchQuery query);
}
