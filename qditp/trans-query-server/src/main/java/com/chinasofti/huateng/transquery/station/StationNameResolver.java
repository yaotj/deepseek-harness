package com.chinasofti.huateng.transquery.station;

import com.chinasofti.huateng.model.app.RequestStationNameBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestStationNameBatchResult;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StationNameResolver {

    private static final Logger log = LoggerFactory.getLogger(StationNameResolver.class);

    private static final String RET_SUCCESS = "0000";

    @Autowired
    private ParaClient paraClient;


    public Map<String, String> resolveStationNames(Set<String> stationCodes) {
        Map<String, String> stationNameMap = new HashMap<>();
        if (stationCodes == null || stationCodes.isEmpty()) {
            return stationNameMap;
        }
        try {
            List<String> codeList = new ArrayList<>(stationCodes);
            RequestStationNameBatchReqDTO req = new RequestStationNameBatchReqDTO();
            req.setStationCodes(codeList);
            RequestStationNameBatchResult result = paraClient.requestStationNameBatch(req);
            if (result == null || !RET_SUCCESS.equals(result.getRetCode())) {
                log.warn("批量查询站点中文名未成功, retCode={}, stationCodes={}",
                        result == null ? null : result.getRetCode(), stationCodes);
                return stationNameMap;
            }
            if (result.getStationNameList() != null) {
                for (RequestStationNameResult item : result.getStationNameList()) {
                    if (item != null && item.getStationName() != null) {
                        stationNameMap.put(item.getStationCode(), item.getStationName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("批量查询站点中文名失败, stationCodes={}", stationCodes, e);
        }
        return stationNameMap;
    }


    public String resolveNameOrCode(String stationCode, Map<String, String> stationNames) {
        if (!StringUtils.hasText(stationCode)) {
            return null;
        }
        if (stationNames == null) {
            return stationCode;
        }
        return stationNames.getOrDefault(stationCode, stationCode);
    }


    public Set<String> collectStationCodes(Set<String> entries, Set<String> exits) {
        Set<String> codes = new LinkedHashSet<>();
        if (entries != null) {
            codes.addAll(entries);
        }
        if (exits != null) {
            codes.addAll(exits);
        }
        return codes;
    }
}
