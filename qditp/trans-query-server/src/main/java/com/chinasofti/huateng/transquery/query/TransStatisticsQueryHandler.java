package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.transquery.constant.TransQueryErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** IF8A-41 账单统计查询。 */
@Component
public class TransStatisticsQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(TransStatisticsQueryHandler.class);
    private static final String ZERO_AMOUNT = "0.00";

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Autowired
    private TransQueryParamNormalizer paramNormalizer;

    /** 查询账单统计 (IF8A-41)。 */
    public RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request) {
        RequestTransStatisticsResult response = new RequestTransStatisticsResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())) {
                return invalidParam(response, "thirdUserId不能为空");
            }

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
            result.setRetCode(TransQueryErrorCodeEnum.SUCCESS.getCode());
            result.setRetMsg(TransQueryErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-41 账单统计完成, thirdUserId={}, cardTypeList={}, cardIdList={}, tripData={}",
                    request.getThirdUserId(), request.getCardTypeList(),
                    request.getCardIdList(), result.getTripData());
            return result;
        } catch (Exception e) {
            log.error("IF8A-41 查询账单统计异常, request={}", request, e);
            response.setRetCode(TransQueryErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TransQueryErrorCodeEnum.SYSTEM_ERROR.getMsg());
            response.setTripData(zeroTripData());
        }
        return response;
    }

    private RequestTransStatisticsResult invalidParam(RequestTransStatisticsResult response, String msg) {
        response.setRetCode(TransQueryErrorCodeEnum.INVALID_PARAM.getCode());
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

    /** 逐字段兜底金额。 */
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
