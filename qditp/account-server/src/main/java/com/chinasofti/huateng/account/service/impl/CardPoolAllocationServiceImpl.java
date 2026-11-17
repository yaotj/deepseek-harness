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


@Service
public class CardPoolAllocationServiceImpl implements CardPoolAllocationService {
    private static final Logger log = LoggerFactory.getLogger(CardPoolAllocationServiceImpl.class);


    private static final String APP_CARD_TYPE_HCE = "03";


    private static final String APP_CARD_TYPE_NEW_HCE = "04";

    private final CardPoolClient cardPoolClient;

    private final SecurityClient securityClient;


    public CardPoolAllocationServiceImpl(CardPoolClient cardPoolClient, SecurityClient securityClient) {
        this.cardPoolClient = cardPoolClient;
        this.securityClient = securityClient;
    }


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
        String normalized = cardType == null ? null : cardType.trim();
        return APP_CARD_TYPE_HCE.equals(normalized) || APP_CARD_TYPE_NEW_HCE.equals(normalized);
    }


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

        if (response == null || !ResultVO.SUCCESS_CODE.equals(response.getRetCode())
                || !StringUtils.hasText(response.getLogicNum()) || !StringUtils.hasText(response.getHceData())) {
            log.error("请求HCE卡数据失败, thirdUserId={}, response={}", thirdUserId, JSON.toJSONString(response));
            return null;
        }
        return new HceCardAllocation(response.getLogicNum(), response.getHceData());
    }


    private String toFourByteHex(String thirdUserId) {
        if (thirdUserId == null || !thirdUserId.matches("\\d{1,19}")) {
            log.error("thirdUserId不是十进制数字，无法编码为HCE的iptUserId, thirdUserId={}", thirdUserId);
            return null;
        }
        long userId = Long.parseLong(thirdUserId);
        return String.format("%08X", userId & 0xFFFFFFFFL);
    }
}
