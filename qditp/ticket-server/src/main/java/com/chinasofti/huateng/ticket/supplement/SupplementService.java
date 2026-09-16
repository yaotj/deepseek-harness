package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;

/**
 * 补站域门面：{@code supplement/} 包对外的**唯一**入口。
 *
 * <p>本包此前没有门面，两个 Handler 各自被不同的调用方直接注入
 * （{@code TicketSupplementController} 拿 {@link ExcessFareHandler}、
 * {@code gate/AgmRideStatusServiceImpl} 拿当时那个 714 行的 {@code CardDataHandler}），
 * 于是「补站」这件事的边界在代码里根本不存在。现在收口为本接口：
 * <ul>
 *   <li>包外 **NEVER 再直接注入 {@link ExcessFareHandler} / {@link CardDataAnalyseHandler} /
 *       {@link CardDataUpdateHandler}**，它们保持包内可见的协作者语义；</li>
 *   <li>入参非空校验放在本层，Controller 只做路由与日志（AGENTS.md §3.3）。</li>
 * </ul>
 *
 * <p>两条补站链路互不相同，改动时 NEVER 混用字段名与白名单：
 * <ul>
 *   <li>APP 自助补站 IF8A-04 → {@link ExcessFareHandler}，参数 {@code upgradeAreaType}</li>
 *   <li>BOM 单边处理 IF5A-01 → {@link CardDataAnalyseHandler}、IF5A-03 → {@link CardDataUpdateHandler}，
 *       参数 {@code updateType} + {@code adviceOpt}</li>
 * </ul>
 *
 * <p>{@code gate/AgmRideStatusService} 的两个同名方法**保留为委派壳**：
 * {@code TicketAgmController} 的 {@code /ci/agm/requestCardData*} 仍在调它们，
 * **NEVER 因为本门面存在就把那两个方法删掉**。
 */
public interface SupplementService {

    /**
     * IF8A-04 请求自助补站。
     *
     * <p>入参 {@code cardId} / {@code upgradeAreaType} / {@code upgradeStationCode} /
     * {@code upgradeDateTime} 缺一即返 {@code INVALID_PARAM}，不进 Handler。
     */
    RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request);

    /**
     * IF5A-01 请求票卡分析。
     */
    RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request);

    /**
     * IF5A-03 请求票卡更新。
     *
     * <p><b>NEVER 加 @Transactional</b>：{@link CardDataUpdateHandler} 的 IF5A-03 主流程
     * 只写补站台账、不动乘车码状态机，却要调 account / para / fep-dev 三个远端，
     * 事务只会把 Druid 连接持有到全部 RPC 返回为止（AGENTS.md §5.2）。
     */
    RequestCardDataUpdateRespDTO requestCardDataUpdate(RequestCardDataUpdateReqDTO request);
}
