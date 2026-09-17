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

/** 站点名称解析器 —— 批量把站点编码翻译成中文名。 */
@Component
public class StationNameResolver {

    private static final Logger log = LoggerFactory.getLogger(StationNameResolver.class);

    /** para-server 成功码。 */
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private ParaClient paraClient;

    /**
     * 批量查询站点编码对应的中文名。
     *
     * @param stationCodes 站点编码集合
     * @return 站点编码 -> 中文站名的映射；
     */
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

    /**
     * 站名降级解析：查得到用中文名，查不到回落站点编码。
     *
     * @param stationCode 站点编码，可为空
     * @param stationNames {@link #resolveStationNames} 的返回值
     * @return 中文站名，查不到时返回原编码；
     */
    public String resolveNameOrCode(String stationCode, Map<String, String> stationNames) {
        if (!StringUtils.hasText(stationCode)) {
            return null;
        }
        if (stationNames == null) {
            return stationCode;
        }
        return stationNames.getOrDefault(stationCode, stationCode);
    }

    /**
     * 合并进站与出站两组编码，去重后一次查询。
     *
     * @param entries 进站站点编码集合
     * @param exits 出站站点编码集合
     * @return 去重后的站点编码集合
     */
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
