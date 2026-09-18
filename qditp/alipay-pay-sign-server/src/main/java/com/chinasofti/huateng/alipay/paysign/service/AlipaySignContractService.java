package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;

/**
 * 支付宝渠道**签约聚合**的唯一入口（2026-09-18 从 4 方法门面 {@code AlipayContractService} 拆出）。
 *
 * <p>只承载「把一条生效签约建立起来 / 读出来」这件事：落库三支（短路 / 就地复活 / INSERT）、
 * 签约流水、支付通道同步 outbox。**解约不在本接口内**，它在 {@link AlipayTerminationService} ——
 * 两者依赖簇不相交（签约侧 {@code ALIPAY_SIGN_INFO} + 账户域查询 + 通道同步；
 * 解约侧 {@code ALIPAY_TERMINATION_REQUEST} + 销卡通知），共处一个接口只会让读代码的人
 * 每次都要先跳过另一半。
 *
 * <p><b>本接口是后续签约能力的落点</b>：新的签约相关方法一律加在这里、实现落
 * {@code service/impl/sign/} 包；**NEVER 再往已删除的 `AlipayContractService` 形态回退成大门面**。
 */
public interface AlipaySignContractService {

    /**
     * 签约登记（{@code POST /channel/addContract}，调用方 fep-alipay-server）。
     *
     * <p>实现刻意不带 {@code @Transactional}（ADR-D129）：链路里有两次出网 —— 查账户域拿卡号、
     * 签约后同步支付通道 —— 任一次被事务包住都会让行锁持有时长等于对端响应时长。
     */
    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);

    /**
     * 按第三方用户号读生效签约（{@code GET /channel/selectSignInfo}）。
     *
     * <p>**当前零调用方**（`rpc/AlipayPaySignClient.selectSignInfo` 有包装方法但全仓零引用），
     * 按裁决**保留不删**，只是宿主随签约聚合迁到本接口。删它时 MUST 连 `rpc` 那个包装方法一起删。
     */
    AlipaySignInfoDTO selectSignInfo(String thirdUserId);
}
