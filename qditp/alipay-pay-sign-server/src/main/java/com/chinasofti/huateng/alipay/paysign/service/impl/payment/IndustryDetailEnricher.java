package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.RequestStationLineInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IndustryDetailEnricher {
    private final ParaClient paraClient;

    public IndustryDetailEnricher(ParaClient paraClient) {
        this.paraClient = paraClient;
    }

    private static final Logger log = LoggerFactory.getLogger(IndustryDetailEnricher.class);

    public String enrich(String industryDetail, String thirdUserId) {
        if (!StringUtils.hasText(industryDetail)) {
            return industryDetail;
        }
        try {
            JSONObject detailObj = JSON.parseObject(industryDetail);
            enrichNames(detailObj);
            return detailObj.toJSONString();
        } catch (Exception e) {
            log.warn("industryDetail enrichment failed", e);
            return industryDetail;
        }
    }

    private void enrichNames(JSONObject detailObj) {
        enrichByStationCode(detailObj, "exitStationCode", "exit");
        enrichByStationCode(detailObj, "entryStationCode", "entry");
    }

    private void enrichByStationCode(JSONObject detailObj, String stationCodeKey, String prefix) {
        String stationCode = detailObj.getString(stationCodeKey);
        if (!StringUtils.hasText(stationCode)) {
            return;
        }

        String lineCodeKey = prefix + "LineCode";
        String lineCode = detailObj.getString(lineCodeKey);
        if (StringUtils.hasText(lineCode)) {
            String shortLineCode = lineCode.length() > 2 ? lineCode.substring(0, 2) : lineCode;
            detailObj.put(lineCodeKey, shortLineCode);
        }

        try {
            RequestStationLineInfoReqDTO req = new RequestStationLineInfoReqDTO();
            req.setStationCode(stationCode);
            RequestStationLineInfoResult result = this.paraClient.requestStationLineInfo(req);
            if (result != null) {
                if (StringUtils.hasText(result.getStationName())) {
                    detailObj.put(prefix + "StationName", result.getStationName());
                }
                if (StringUtils.hasText(lineCode) && StringUtils.hasText(result.getLineName())) {
                    detailObj.put(prefix + "LineName", result.getLineName());
                }
            }
        } catch (Exception e) {
            log.warn("queryStationLineInfo failed, stationCode={}", stationCode, e);
        }
    }
}
