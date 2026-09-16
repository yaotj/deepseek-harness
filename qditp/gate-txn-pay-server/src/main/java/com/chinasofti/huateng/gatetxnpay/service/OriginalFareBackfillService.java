package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.model.page.OriginalFareBackfillRequest;

import java.util.Map;

/**
 * 历史订单 {@code ORIGINAL_FARE}（地铁原价）**运营补数**。
 *
 * <p>独立于 {@link GateTxnPayService} 的理由不是「代码太长」，而是它与出站扣费主链路
 * **没有任何共享状态**：只按进出站重查票价、只写 {@code ORIGINAL_FARE} 为空的行，
 * 不碰 {@code DEBIT_STATUS}、不发起扣款、不调 pay-sign。实现类因此只需要
 * {@code GateTxnPayMapper} 与 {@code FareCalculator} 两个协作者。
 *
 * <p>三条设计约束，改动前 MUST 逐条确认：</p>
 * <ol>
 *   <li><b>不带事务</b>。方法内要逐笔调 para-server，事务包住 RPC 会把行锁持有时长拉到
 *       对端响应时长，是本项目已发生过的生产事故形态。逐笔单条 UPDATE 自动提交，
 *       中途失败不影响已回填的行。</li>
 *   <li><b>只写空值行</b>。SQL 带 {@code ORIGINAL_FARE IS NULL}，重复调用幂等，
 *       也不会覆盖出站时写好的原价快照。</li>
 *   <li><b>回填用的是当前参数版本的票价</b>，费率矩阵只保留少数历史版本，无法还原当时版本，
 *       因此结果是近似口径；若期间调过价，MUST 先与业务确认可接受。</li>
 * </ol>
 *
 * <p>{@code dryRun} 默认 true 只试算不落库；实付大于 0 且「原价 - 实付」超过
 * {@code suspectDiffCents}（默认 300 分）的行会被跳到 {@code suspectList} 而不回填，
 * 用于挡住「金额按元上送」这类脏数据造出虚高优惠——已发生：设备 206377 的 5 笔测试数据
 * {@code TRX_AMOUNT} 是元、原价是分，回填后优惠虚高 4~7 元。</p>
 */
public interface OriginalFareBackfillService {

    /**
     * 按 {@code TXN_DATE} 区间扫出 {@code ORIGINAL_FARE} 为空的订单并回填。
     *
     * <p>返回体含 {@code dryRun} / {@code scannedCount} / {@code updatedCount} /
     * {@code noFareCount} / {@code suspectCount} / {@code failedCount} 六个计数与三个明细列表。
     * {@code affected == 0} 是并发下的**幂等结果**，既不计 updated 也不计 failed。</p>
     */
    ResultVO<Map<String, Object>> backfillOriginalFare(OriginalFareBackfillRequest request);
}
