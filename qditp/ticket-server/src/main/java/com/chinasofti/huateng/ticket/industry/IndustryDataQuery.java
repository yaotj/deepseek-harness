package com.chinasofti.huateng.ticket.industry;

import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import org.springframework.util.StringUtils;


public record IndustryDataQuery(String thirdUserId, String cardId, String cardType) {

    public static String validate(String thirdUserId, String cardId, String cardType) {
        if (!StringUtils.hasText(thirdUserId)) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(cardId)) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(cardType)) {
            return "cardType不能为空";
        }
        return null;
    }

    public static IndustryDataQuery from(RequestIndustryDataReqDTO request) {
        return new IndustryDataQuery(request.getThirdUserId().trim(),
                request.getCardId().trim(),
                request.getCardType().trim());
    }

    public static IndustryDataQuery from(RequestNoSignalDataReqDTO request) {
        return new IndustryDataQuery(request.getThirdUserId().trim(),
                request.getCardId().trim(),
                request.getCardType().trim());
    }
}
