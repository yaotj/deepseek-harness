package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.app.RequestStationNameBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestStationNameBatchResult;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站点名称解析器 - 批量查询站点编码对应的中文名。
 */
@Component
public class TransStationNameResolver {

    private static final Logger log = LoggerFactory.getLogger(TransStationNameResolver.class);

    @Autowired
    private ParaClient paraClient;

    /**
     * 批量查询站点编码对应的中文名。
     *
     * @param stationCodes 站点编码集合
     * @return 站点编码 -> 中文站名的映射
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
            if (result != null && result.getStationNameList() != null) {
                for (RequestStationNameResult item : result.getStationNameList()) {
                    if (item.getStationName() != null) {
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
     * 从记录列表中提取站点编码集合。
     *
     * @param entries 进站站点编码列表
     * @param exits   出站站点编码列表
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
