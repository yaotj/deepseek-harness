package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.service.CardPoolAllocationService;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.model.cardpool.CardPoolOutcome;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReserveResult;
import com.chinasofti.huateng.model.cardpool.CardPoolTicketType;
import com.chinasofti.huateng.model.security.RequestHceCardDataReqDTO;
import com.chinasofti.huateng.model.security.RequestHceCardDataRespDTO;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import com.chinasofti.huateng.rpc.security.SecurityClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 开户发号协作者的实现，2026-09-11 从 {@code AccountApplicationServiceImpl} 原样搬出，行为未变。
 *
 * <p>本类<b>不带 {@code @Transactional} 也 NEVER 加</b>：每个方法都是出网调用。</p>
 */
@Service
public class CardPoolAllocationServiceImpl implements CardPoolAllocationService {

    private static final Logger log = LoggerFactory.getLogger(CardPoolAllocationServiceImpl.class);

    /**
     * APP 上送的 HCE 票种码（旧 NFC 卡），发行侧对应 {@code 0442}。
     *
     * <p><b>与 {@code AccountRegistrationServiceImpl.ALIPAY_PAYMENT_CHANNEL} 的 {@code "03"}
     * 是两套互不相干的编码</b>：那个 {@code 03} 是<b>支付渠道</b>（支付宝），这个是<b>票种</b>。
     * 此前两处都是裸字面量，读代码时无法分辨，全局 grep {@code "03"} 也会把两者混在一起。
     * <b>NEVER 把这两个常量合并或互相引用。</b></p>
     *
     * <p>APP 口径与发行口径的映射表在 {@code model} 的 {@code CardTypeMapping.ISSUE_CARD_TYPES}，
     * 那里的 {@code NFC_BUCKET_CARD_TYPES} 是 private、且只服务查询链路的聚合桶语义，
     * <b>NEVER 为了复用它去改 `model` 的可见性</b>（本处要的是「是不是 HCE」，不是「查哪些票种」）。</p>
     */
    private static final String APP_CARD_TYPE_HCE = "03";

    /** APP 上送的新版 HCE 票种码（新 NFC 卡），发行侧对应 {@code 0443}。 */
    private static final String APP_CARD_TYPE_NEW_HCE = "04";

    private final CardPoolClient cardPoolClient;

    private final SecurityClient securityClient;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public CardPoolAllocationServiceImpl(CardPoolClient cardPoolClient, SecurityClient securityClient) {
        this.cardPoolClient = cardPoolClient;
        this.securityClient = securityClient;
    }

    /**
     * 向 card-pool-server 预占一个逻辑卡号。
     *
     * <p>调用方 MUST 在事务外调用：这是 RPC。返回 {@code null} 只表示「本次拿不到卡号」，
     * 三种原因已按 {@link CardPoolOutcome} 分级打日志，NEVER 在上层再统一翻译成「卡池耗尽」。</p>
     */
    @Override
    public CardAllocation reserveFromPool(String cardType, String businessType, String businessId, String ownerId) {
        if (!CardPoolTicketType.POOL_ENABLED_TYPES.contains(cardType)) {
            log.error("该票种不走逻辑卡号池发号，无法开户, cardType={}, businessType={}, businessId={}",
                    cardType, businessType, businessId);
            return null;
        }
        CardPoolReservationReqDTO poolRequest = new CardPoolReservationReqDTO();
        poolRequest.setCardType(cardType);
        poolRequest.setBusinessType(businessType);
        poolRequest.setBusinessId(businessId);
        poolRequest.setOwnerId(ownerId);

        CardPoolReserveResult result = cardPoolClient.reserve(poolRequest);
        if (!result.isSuccess()) {
            switch (result.getOutcome()) {
                case POOL_EMPTY -> log.warn("逻辑卡号池已空，需尽快补货, cardType={}, businessId={}, msg={}",
                        cardType, businessId, result.getMessage());
                case REJECTED -> log.error("卡池拒绝本次预占（票种或归属不合法）, cardType={}, businessId={}, msg={}",
                        cardType, businessId, result.getMessage());
                default -> log.error("调用卡池预占未拿到结论，可重试, cardType={}, businessId={}, msg={}",
                        cardType, businessId, result.getMessage());
            }
            return null;
        }
        CardPoolReservationRespDTO data = result.getData();
        log.info("卡池预占成功, cardType={}, businessType={}, businessId={}, cardNo={}, reservationId={}",
                cardType, businessType, businessId, data.getCardNo(), data.getReservationId());
        return new CardAllocation(data.getCardNo(), null, data.getReservationId(), businessId);
    }

    /**
     * 确认预占。确认失败不回滚已提交的开户数据——卡号已发给用户，回滚才是错的；
     * 但**结果 MUST 回给调用方**：未确认的预占会被卡池的超时回收扫回 AVAILABLE，
     * 此时同一卡号可能被二次发放，所以除了打 ERROR，还要让上层开工单 + 不返成功（ADR-D52）。
     *
     * <p>返 {@code true} 也包含「本次没有预占可确认」（HCE 发号不进卡池）这一正常跳过分支，
     * <b>NEVER 把它当失败</b>。</p>
     */
    @Override
    public boolean confirmReservation(CardAllocation allocation, String scene) {
        if (allocation == null || !StringUtils.hasText(allocation.reservationId())) {
            return true;
        }
        CardPoolActionResult result;
        try {
            result = cardPoolClient.confirm(allocation.reservationId(), allocation.businessId());
        } catch (RuntimeException ex) {
            log.error("{}卡池确认抛异常，开户已落库，MUST 人工核对, reservationId={}, businessId={}, cardNo={}",
                    scene, allocation.reservationId(), allocation.businessId(), allocation.cardId(), ex);
            return false;
        }
        if (result != null && result.isSuccess()) {
            log.info("{}卡池确认成功, reservationId={}, businessId={}",
                    scene, allocation.reservationId(), allocation.businessId());
            return true;
        }
        log.error("{}卡池确认失败，开户已落库但卡号仍处预占态，MUST 人工核对, reservationId={}, businessId={}, cardNo={}, outcome={}, msg={}",
                scene, allocation.reservationId(), allocation.businessId(), allocation.cardId(),
                result == null ? null : result.getOutcome(), result == null ? null : result.getMessage());
        return false;
    }

    /**
     * 释放预占。释放失败只记日志，不向上抛：卡池的预占超时回收会兜底把卡号收回。
     */
    @Override
    public void releaseReservation(CardAllocation allocation, String scene) {
        if (allocation == null || !StringUtils.hasText(allocation.reservationId())) {
            return;
        }
        CardPoolActionResult result;
        try {
            result = cardPoolClient.release(allocation.reservationId(), allocation.businessId());
        } catch (RuntimeException ex) {
            log.warn("{}释放卡池预占抛异常，等预占超时回收兜底, reservationId={}, businessId={}",
                    scene, allocation.reservationId(), allocation.businessId(), ex);
            return;
        }
        if (result != null && result.isSuccess()) {
            log.info("{}已释放卡池预占, reservationId={}, businessId={}",
                    scene, allocation.reservationId(), allocation.businessId());
            return;
        }
        log.warn("{}释放卡池预占未成功，等预占超时回收兜底, reservationId={}, businessId={}, outcome={}, msg={}",
                scene, allocation.reservationId(), allocation.businessId(),
                result == null ? null : result.getOutcome(), result == null ? null : result.getMessage());
    }

    @Override
    public boolean isHceCard(String cardType) {
        // 本方法是接口方法、两条开户链路共用，NEVER 假定调用方已判空
        String normalized = cardType == null ? null : cardType.trim();
        return APP_CARD_TYPE_HCE.equals(normalized) || APP_CARD_TYPE_NEW_HCE.equals(normalized);
    }

    /**
     * 请求安全服务发售 HCE 卡数据并取得逻辑卡号。
     *
     * <p>{@code ticketCard} 取自开户请求；{@code iptUserId} 由
     * {@code 000000} 和 {@code thirdUserId} 的四字节十六进制编码组成。安全服务成功响应中的
     * {@code logicNum} 即开户使用的 {@code cardId}。</p>
     */
    @Override
    public HceCardAllocation requestHceCardData(String thirdUserId, String ticketCard) {
        String encodedUserId = toFourByteHex(thirdUserId);
        if (encodedUserId == null) {
            return null;
        }
        RequestHceCardDataReqDTO request = new RequestHceCardDataReqDTO();
        request.setTicketCard(ticketCard);
        request.setIptUserId("000000" + encodedUserId);
        RequestHceCardDataRespDTO response = securityClient.requestHceCardData(request);

        // 安全服务走 ResultVO 骨架（SecurityClient.buildBaseResponse 把 code 映射成 retCode），
        // 成功只有 200、失败是 500 / 400，**从不回 0000**。因此这里 MUST 单码判定，
        // NEVER 改成 AccResultCode.isSuccess —— 那是 ACC 出向的双码口径，在这里等于凭空放宽成功集合。
        if (response == null || !ResultVO.SUCCESS_CODE.equals(response.getRetCode())
                || !StringUtils.hasText(response.getLogicNum()) || !StringUtils.hasText(response.getHceData())) {
            log.error("请求HCE卡数据失败, thirdUserId={}, response={}", thirdUserId, JSON.toJSONString(response));
            return null;
        }
        return new HceCardAllocation(response.getLogicNum(), response.getHceData());
    }

    /**
     * 将十进制第三方用户标识编码为四字节大写十六进制字符串。
     *
     * @param thirdUserId 十进制第三方用户标识
     * @return 八位大写十六进制字符串；<b>非十进制数字时返回 {@code null}</b>，由调用方按
     *         「HCE 发号失败」处理。IF8A-01 入口只校验了 {@code hasText}，不保证是数字，
     *         直接 {@code parseLong} 会抛 NumberFormatException 被上层统一 catch 成 9999，
     *         日志里看不出真实原因。
     */
    private String toFourByteHex(String thirdUserId) {
        if (thirdUserId == null || !thirdUserId.matches("\\d{1,19}")) {
            log.error("thirdUserId不是十进制数字，无法编码为HCE的iptUserId, thirdUserId={}", thirdUserId);
            return null;
        }
        long userId = Long.parseLong(thirdUserId);
        return String.format("%08X", userId & 0xFFFFFFFFL);
    }
}
