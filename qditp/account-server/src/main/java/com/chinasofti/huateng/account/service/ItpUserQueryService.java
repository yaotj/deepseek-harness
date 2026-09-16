package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.page.ItpPayChannelView;
import com.chinasofti.huateng.account.page.ItpUserSearchView;
import com.chinasofti.huateng.account.page.RegStatView;

import java.util.List;

/**
 * 运营后台的非支付宝用户查询（`/page/user/itp` 两个端点的业务层）。
 *
 * <p>2026-09-11 从 {@code ItpUserPageController} 下沉。原先那个 controller 不只是透传：
 * 它自己按查询类型分派、按渠道逐条调支付域 RPC、并组装脱敏视图与 {@code terminationReady}，
 * 属于 AGENTS.md §3.3 禁止的「controller 写业务逻辑」。<b>NEVER 把 Mapper 或 `PaySignClient`
 * 注回 controller</b>。</p>
 *
 * <p><b>2.0.63 起本接口的实现内没有任何跨域 RPC</b>（ADR-D30）：{@link #payChannels} 的
 * 「支付账号」原先按渠道<b>逐条</b>调 {@code paySignClient.querySignInfoBySeq}（N+1），
 * 现改读 {@code APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID} 本地列，该列由 IF8A-77 回写。
 * <b>NEVER 为了「查得更全」把 RPC 加回来</b> —— 运营列表页每行一次跨域 HTTP 只换一个展示字段，
 * 代价与收益不成比例；覆盖率问题的正解是补齐回写点，不是在读路径上兜底。</p>
 */
public interface ItpUserQueryService {

    /**
     * 按查询类型检索开户记录（含有效与已注销，不含已归档物理删除的行），返回**脱敏后**的视图。
     *
     * <p>视图除注册信息外还带该卡的支付渠道解约时间：申请解绑日期（最近一次
     * {@code APP_TERMINATION_REQUEST.REQUEST_TIME}）与解绑成功日期（最近一次 SUCCESS
     * 的 {@code COMPLETE_TIME}），均只读聚合自同库的 pay-sign 域表。</p>
     *
     * @param queryType 支持 {@code THIRD_USER_ID} / {@code MSISDN} / {@code CARD_ID}
     * @param keyword   已由调用方保证非空；本方法内部会 trim
     * @return 命中的视图列表；<b>{@code null} 表示查询类型不受支持</b>（调用方据此返回参数错误）。
     *         <b>NEVER 把 {@code null} 与「查不到」混为一谈</b> —— 查不到是空列表。
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
     * <p>只读聚合，不含个人信息。日期为闭区间（{@code yyyy-MM-dd}），{@code null}/空表示不限。</p>
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
     * <p>列表长度上限由 controller 强制（500），本方法假定入参已合规；内部只做
     * trim、去空白、去重。空列表直接返回空结果，不打数据库。</p>
     *
     * @return 命中的视图列表，永不返回 {@code null}
     */
    List<ItpUserSearchView> batchSearch(List<String> cardIds);
}
