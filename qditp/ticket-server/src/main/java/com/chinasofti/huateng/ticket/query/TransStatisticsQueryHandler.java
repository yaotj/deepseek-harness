package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * IF8A-41 账单统计查询。
 *
 * <p>2026-09-14 从 {@code TransQueryHandler}（626 行）拆出，与 IF8A-05 / IF8A-34 互不调用。</p>
 *
 * <p><b>统计源表是 {@code GATE_TXN_PAY}（经 RPC），NEVER 退回本地 {@code QRCODE_TXN_DETAIL} 自算</b>：
 * 那张表没有 {@code ORIGINAL_FARE}，只能拿 {@code OVERTIME_AMOUNT} 冒充「优惠」，三个金额标签全部错位
 * （2026-09-10 实测用户 00522943 的 12 元超时费被显示成「已优惠 12 元」，实付少报 12 元）。
 * {@code GATE_TXN_PAY} 同时有原价 / 票价 / 超时费 / 实付四个量，两表的 {@code TRX_AMOUNT} 与
 * {@code OVERTIME_AMOUNT} 逐行相等（LEFT JOIN 8 行核对），因此换表不改变已正确的那部分口径。</p>
 *
 * <p><b>本接口的日期 NEVER 走 {@code normalizeDate}</b>：APP 上送的就是 {@code yyyyMMdd}
 * （与 {@code TXN_DATE} 同格式、直接可比），而那个方法按 {@code yyyy-MM-dd} 解析（IF8A-05 的格式），
 * 喂 {@code yyyyMMdd} 必抛异常。</p>
 */
@Component
public class TransStatisticsQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(TransStatisticsQueryHandler.class);
    private static final String ZERO_AMOUNT = "0.00";

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Autowired
    private TransQueryParamNormalizer paramNormalizer;

    /**
     * 查询账单统计 (IF8A-41)。
     */
    public RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request) {
        RequestTransStatisticsResult response = new RequestTransStatisticsResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())) {
                return invalidParam(response, "thirdUserId不能为空");
            }

            // 卡类型映射：与 IF8A-05 同一套白名单与聚合桶展开规则（05 → 0445~0448）。
            // 未开通某票种时 APP 不带 cardId（2026-09-10 实测 NFC 页签只有 thirdUserId + cardType），
            // 此时 CARD_TYPE 是唯一的票种过滤依据，缺了它 SQL 退化成「按用户查全部票种」——
            // 实测 4 票种用户会把全部 47 条合计进来。
            if (StringUtils.hasText(request.getCardType())) {
                List<String> cardTypes = paramNormalizer.expandCardTypes(request.getCardType());
                if (cardTypes == null) {
                    return invalidParam(response, "卡类型非法: " + request.getCardType());
                }
                request.setCardTypeList(cardTypes);
                request.setCardType(null);
            }
            request.setCardIdList(paramNormalizer.parseCardIds(request.getCardId()));

            RequestTransStatisticsResult result = gateTxnPayClient.requestTransStatistics(request);
            if (result == null) {
                result = new RequestTransStatisticsResult();
            }
            if (result.getTripData() == null) {
                result.setTripData(zeroTripData());
            } else {
                fillZeroAmounts(result.getTripData());
            }
            result.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            result.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-41 账单统计完成, thirdUserId={}, cardTypeList={}, cardIdList={}, tripData={}",
                    request.getThirdUserId(), request.getCardTypeList(),
                    request.getCardIdList(), result.getTripData());
            return result;
        } catch (Exception e) {
            log.error("IF8A-41 查询账单统计异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            response.setTripData(zeroTripData());
        }
        return response;
    }

    private RequestTransStatisticsResult invalidParam(RequestTransStatisticsResult response, String msg) {
        response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(msg);
        response.setTripData(zeroTripData());
        return response;
    }

    /** 零值统计对象，参数非法 / 异常 / 无数据时统一用它，避免给 APP 返回 null 金额。 */
    private TripDataDTO zeroTripData() {
        TripDataDTO tripData = new TripDataDTO();
        tripData.setTotalPrice(ZERO_AMOUNT);
        tripData.setTotalDebit(ZERO_AMOUNT);
        tripData.setTotalDiscount(ZERO_AMOUNT);
        tripData.setTotalOvertime(ZERO_AMOUNT);
        tripData.setCount(0);
        return tripData;
    }

    /**
     * 逐字段兜底金额。
     *
     * <p><b>MUST 逐字段判空，NEVER 只判 {@code tripData == null}</b>：{@code COUNT(1)} 对空结果集返回 0，
     * 而 {@code SUM()} 返回 NULL，因此「无数据」时 {@code tripData} 非 null（count=0 有值）、
     * 三个金额却是 null，只判对象非空会把 null 直接透给 APP（2026-09-10 实测日票页签
     * 返回 {@code count:0} 但三个金额均为 null）。</p>
     */
    private void fillZeroAmounts(TripDataDTO tripData) {
        if (tripData == null) {
            return;
        }
        if (!StringUtils.hasText(tripData.getTotalPrice())) {
            tripData.setTotalPrice(ZERO_AMOUNT);
        }
        if (!StringUtils.hasText(tripData.getTotalDebit())) {
            tripData.setTotalDebit(ZERO_AMOUNT);
        }
        if (!StringUtils.hasText(tripData.getTotalDiscount())) {
            tripData.setTotalDiscount(ZERO_AMOUNT);
        }
        if (!StringUtils.hasText(tripData.getTotalOvertime())) {
            tripData.setTotalOvertime(ZERO_AMOUNT);
        }
        if (tripData.getCount() == null) {
            tripData.setCount(0);
        }
    }
}
