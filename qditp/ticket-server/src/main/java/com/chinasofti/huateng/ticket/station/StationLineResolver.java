package com.chinasofti.huateng.ticket.station;

import com.chinasofti.huateng.model.app.RequestStationLineInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 车站线路信息解析器 —— 把站点编码翻译成线路代码 / 线路名。
 *
 * <p><b>为什么要有这个类。</b>2026-09-14 实测，{@code paraClient.requestStationLineInfo} 的调用
 * 在三个包里各写了一遍（{@code gate/AlipayIndustryDetailAssembler}、
 * {@code supplement/CardDataAnalyseHandler}、{@code notify/AlipayTripNotifier}），
 * 这是 {@code ParaClient} 被 5 个包持有的主因。三份代码的 RPC 组装与异常吞咽完全相同，
 * 只有「查不到怎么办」不同。本类只收口前者。
 *
 * <p><b>本类刻意不做兜底决策，NEVER 加。</b>三个调用方的兜底语义**互不相同且都是有意的**：
 * <ul>
 *   <li>{@code gate} 行业数据：查不到时由调用方用 {@code stationCode} 顶替 lineCode / lineName；</li>
 *   <li>{@code supplement} IF5A-01：返回空串（{@code lastLineCode} 只是回显字段）；</li>
 *   <li>{@code notify} 支付宝行程：返回 {@code stationCode} 本身，**契约已联调通过、NEVER 改**。</li>
 * </ul>
 * 把这三种揉成一个统一返回值，等于把三个不同的对外契约合并 —— 那正是本项目栽过的坑。
 * 本类的职责边界是「查得到就给结果，查不到就给 {@code null}」，到此为止。
 *
 * <p><b>{@link #isUsable} 是可选的，不是强制的。</b>{@code gate} 那条链路
 * **历史上就没有判 retCode**（拿到什么就用什么，只在字段为 {@code null} 时才回落站码），
 * 而另两条判。本次收口**刻意保留这个差异**、没有顺手统一 —— 让 {@code gate} 突然开始判
 * retCode 会改变行业数据推送的出向字段，而该链路无单测覆盖。差异现在集中在本文件的注释里可见，
 * 不再分散在三个包。要统一 MUST 单独一笔改动 + 端到端复测。
 *
 * <p>依赖方向与 {@link StationNameResolver} 相同：{@code station/} 只准依赖
 * {@code rpc} / {@code model}，**NEVER 依赖任何业务包**。
 */
@Component
public class StationLineResolver {

    private static final Logger log = LoggerFactory.getLogger(StationLineResolver.class);

    /** para-server 成功码。 */
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private ParaClient paraClient;

    /**
     * 查询车站线路信息。
     *
     * <p><b>本方法不判 {@code retCode}</b>，原样返回 para-server 的应答，由调用方按自己的
     * 契约决定判不判（判的话用 {@link #isUsable}）。异常在此吞掉并记 WARN —— 线路信息
     * 在三条链路上都只是**回显 / 辅助字段**，NEVER 让它的失败打断主流程。
     *
     * @param stationCode 站点编码，空则直接返回 {@code null}（不发 RPC）
     * @return para-server 应答；站码为空或调用抛异常时返回 {@code null}
     */
    public RequestStationLineInfoResult resolveLineInfo(String stationCode) {
        if (!StringUtils.hasText(stationCode)) {
            return null;
        }
        try {
            RequestStationLineInfoReqDTO request = new RequestStationLineInfoReqDTO();
            request.setStationCode(stationCode);
            return paraClient.requestStationLineInfo(request);
        } catch (Exception e) {
            log.warn("查询车站线路信息失败, stationCode={}", stationCode, e);
            return null;
        }
    }

    /**
     * 应答是否可用：非空 + 成功码 + {@code lineCode} 非空。
     *
     * <p>给「要判 retCode」的调用方用（{@code supplement} / {@code notify}）。
     * {@code gate} 不调它，见类注释。
     */
    public boolean isUsable(RequestStationLineInfoResult result) {
        return result != null
                && RET_SUCCESS.equals(result.getRetCode())
                && StringUtils.hasText(result.getLineCode());
    }
}
