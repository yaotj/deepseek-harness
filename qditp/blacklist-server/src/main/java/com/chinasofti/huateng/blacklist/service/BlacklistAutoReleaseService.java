package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.constant.BlacklistErrorCodeEnum;
import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 黑名单自动解除：按渠道查对应欠费源，欠费已结清即发起解除。
 *
 * <p>与 {@link BlacklistReleaseInspectService} 的关系是「盘点 vs 执行」：那个只读、把三态报给人看，
 * 本类会真的改数据。因此本类的每一条放行判据都收得更紧：
 * <ul>
 *   <li><b>只看 {@code BLACK_CAUSE='01'}（欠费）</b>，由 {@code selectForAutoRelease} 在 SQL 里限定。
 *       自动解除的前提是「加黑原因已失效」，而只有欠费类的失效条件能被机器证明（欠费结清）。
 *       02 挂失补卡 NEVER 按欠费结清放行，09 其他无判据，两者只能人工解除。</li>
 *   <li><b>按 {@code CHANNEL_CODE} 单向路由</b>：01 地铁APP 只查闸机出站扣费欠费，02 支付宝只查支付宝出行欠费，
 *       <b>99 未知渠道一律跳过</b>（不知道该查哪个源，查错了等于拿无关结论放行）。</li>
 *   <li><b>{@code null}（欠费查询未成功执行）一律跳过</b>，NEVER 当成「已结清」。</li>
 * </ul>
 *
 * <p>解除动作<b>MUST 复用 {@link BlacklistService#deleteBlackList}</b>，NEVER 在本类里自己
 * {@code markReleasing} + 删行：两阶段解除（CAS 成 RELEASING → 通知推达渠道 → 搬历史 + 删主表行）
 * 与操作日志、afterCommit 出网都在那个方法里，绕过它就会漏掉渠道通知，出现「本地已解除、支付宝侧仍拉黑」。
 *
 * <p><b>NEVER 给本类加 {@code @Transactional}</b>：方法内有出网 HTTP（欠费查询），
 * 且 {@code deleteBlackList} 自己是短事务 + 提交后出网，套在外层事务里会把那条 afterCommit 推迟到批次结束。
 */
@Service
public class BlacklistAutoReleaseService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistAutoReleaseService.class);

    /** 地铁 APP 渠道，欠费源是闸机出站扣费。 */
    private static final String CHANNEL_CODE_METRO_APP = "01";
    /** 支付宝渠道，欠费源是支付宝出行。 */
    private static final String CHANNEL_CODE_ALIPAY = "02";

    /** 自动解除时写进 BLACKLIST.RELEASE_REASON，运营页与历史表都按它区分人工解除。 */
    private static final String RELEASE_REASON = "欠费已结清自动解除";
    /** 自动解除时写进 RELEASE_BY / 操作日志，NEVER 写成某个管理员账号。 */
    private static final String RELEASE_BY = "auto-release-job";

    private final BlacklistMapper blacklistMapper;
    private final BlacklistService blacklistService;
    private final CardUnsettledQuery cardUnsettledQuery;

    /** 单轮处理上限，与盘点分开配置：本任务每行都可能出网两次，跑太大会拖长单次调用。 */
    @Value("${blacklist.auto-release.batch-size:200}")
    private int batchSize;

    public BlacklistAutoReleaseService(BlacklistMapper blacklistMapper,
                                       BlacklistService blacklistService,
                                       CardUnsettledQuery cardUnsettledQuery) {
        this.blacklistMapper = blacklistMapper;
        this.blacklistService = blacklistService;
        this.cardUnsettledQuery = cardUnsettledQuery;
    }

    /**
     * 扫一轮候选并对欠费已结清的卡发起解除。
     *
     * @param limit 单轮上限，null 或非正数时取配置缺省值
     * @return 逐类计数，六个计数相加等于 scanned
     */
    public BlacklistAutoReleaseRespDTO autoRelease(Integer limit) {
        BlacklistAutoReleaseRespDTO response = new BlacklistAutoReleaseRespDTO();
        response.setResultCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg("成功");

        int effective = limit == null || limit <= 0 ? (batchSize > 0 ? batchSize : 200) : limit;
        List<Blacklist> records = blacklistMapper.selectForAutoRelease(effective);
        if (records == null || records.isEmpty()) {
            log.info("黑名单自动解除完成, 无欠费类候选记录, limit={}", effective);
            return response;
        }

        int released = 0;
        int unsettled = 0;
        int unknown = 0;
        int skipped = 0;
        int failed = 0;
        for (Blacklist record : records) {
            switch (handleOne(record)) {
                case RELEASED -> released++;
                case UNSETTLED -> unsettled++;
                case UNKNOWN -> unknown++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }

        response.setScanned(records.size());
        response.setReleased(released);
        response.setUnsettled(unsettled);
        response.setUnknown(unknown);
        response.setSkipped(skipped);
        response.setFailed(failed);
        log.info("黑名单自动解除完成, scanned={}, released={}, unsettled={}, unknown={}, skipped={}, failed={}",
                records.size(), released, unsettled, unknown, skipped, failed);
        return response;
    }

    /**
     * 处置单行。任何异常都收敛成 FAILED，不让一条坏数据中断整批。
     */
    private Outcome handleOne(Blacklist record) {
        String cardId = record.getCardId();
        String channelCode = record.getChannelCode();
        Boolean stillOwing;
        if (CHANNEL_CODE_METRO_APP.equals(channelCode)) {
            stillOwing = cardUnsettledQuery.gateUnsettled(cardId);
        } else if (CHANNEL_CODE_ALIPAY.equals(channelCode)) {
            stillOwing = cardUnsettledQuery.alipayUnsettled(cardId);
        } else {
            // 99 未知渠道：不知道该查哪个欠费源，只能留给人工。这类行会每轮都被扫到并计入 skipped，
            // skipped 长期不为 0 即说明上游加黑时没送 channelCode，MUST 从加黑入口治，NEVER 在这里猜一个渠道。
            log.warn("黑名单自动解除跳过: 渠道未知、无法定位欠费源, MUST 人工处理, cardId={}, channelCode={}",
                    cardId, channelCode);
            return Outcome.SKIPPED;
        }

        if (stillOwing == null) {
            log.error("黑名单自动解除跳过: 欠费查询未成功、事实不明, MUST 人工核对, cardId={}, channelCode={}",
                    cardId, channelCode);
            return Outcome.UNKNOWN;
        }
        if (stillOwing) {
            log.info("黑名单自动解除跳过: 仍有未结清欠费, cardId={}, channelCode={}", cardId, channelCode);
            return Outcome.UNSETTLED;
        }

        return release(cardId, channelCode);
    }

    /**
     * 发起解除（阶段一）。解除通知的投递与阶段二由 deleteBlackList 内部的 afterCommit 与扫表补偿负责。
     */
    private Outcome release(String cardId, String channelCode) {
        try {
            DeleteBlackListReqDTO request = new DeleteBlackListReqDTO();
            request.setCardId(cardId);
            request.setReleaseReason(RELEASE_REASON);
            request.setReleaseBy(RELEASE_BY);
            BlackListOperateResult result = blacklistService.deleteBlackList(request);
            if (result == null || !BlacklistErrorCodeEnum.SUCCESS.getCode().equals(result.getRetCode())) {
                log.error("黑名单自动解除失败, MUST 人工核对, cardId={}, channelCode={}, response={}",
                        cardId, channelCode, result);
                return Outcome.FAILED;
            }
            log.info("黑名单自动解除已发起, cardId={}, channelCode={}, releaseReason={}",
                    cardId, channelCode, RELEASE_REASON);
            return Outcome.RELEASED;
        } catch (Exception e) {
            log.error("黑名单自动解除异常, MUST 人工核对, cardId={}, channelCode={}", cardId, channelCode, e);
            return Outcome.FAILED;
        }
    }

    /** 单行处置结果，与响应里的五个计数一一对应。 */
    private enum Outcome {
        RELEASED,
        UNSETTLED,
        UNKNOWN,
        SKIPPED,
        FAILED
    }
}
