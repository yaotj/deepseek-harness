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

/**
 * 站点名称解析器 —— 批量把站点编码翻译成中文名。
 *
 * <p><b>本类所在的 {@code station/} 包是「被多个业务包共享的下沉能力」，不是业务包。</b>
 * 2026-09-14 从 {@code query/TransStationNameResolver} 迁出（原名带 {@code Trans} 前缀，
 * 但它与交易查询无关，只是最早的调用方在那里）。迁出的动因是**依赖方向**：
 * {@code query} / {@code alipay} / {@code ridestatus} 三个平级业务包都要用它，
 * 谁放在谁家里都会制造两条横向依赖 —— 现在三方统一向下依赖 {@code station/}。</p>
 *
 * <p><b>依赖方向是单向的，NEVER 反过来</b>：{@code station/} 只准依赖 {@code rpc} / {@code model} /
 * {@code mapper}，**NEVER 依赖 {@code gate} / {@code query} / {@code ridestatus} / {@code alipay} /
 * {@code supplement} / {@code notify} 任何一个**。一旦反向依赖，它就不再是可共享的下沉能力，
 * 而是又一处循环耦合。</p>
 */
@Component
public class StationNameResolver {

    private static final Logger log = LoggerFactory.getLogger(StationNameResolver.class);

    /**
     * para-server 成功码。**NEVER 删这个判定**：只判 {@code result != null} 时，
     * para 返失败码却带残缺列表的情况会把不可信站名直接返给 APP
     * （原 {@code TicketRideStatusServiceImpl.fetchStationNameMap} 有这层校验，
     * 2026-09-14 第一次搬迁到 {@code query} 包时曾一度丢失）。
     */
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private ParaClient paraClient;

    /**
     * 批量查询站点编码对应的中文名。
     *
     * @param stationCodes 站点编码集合
     * @return 站点编码 -> 中文站名的映射；查询失败时返回**空 Map 而不是 null**
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
     * <p><b>降级规则收口在此一处</b>，NEVER 再在各 Assembler / Handler 里复制
     * {@code map.getOrDefault(code, code)} —— 仓内仍有 **6 处**同形副本尚未替换，
     * 分布在 ticket-server 的 {@code alipay/AlipayTripHandler} 里（{@code query/TransQueryHandler}
     * 那部分已随 r770 的拆分一并收口）。
     * 2026-09-14 的分包重构**刻意没有一起替换**：本方法与那 6 处**有一处行为差异**——
     * 副本对空编码返回空编码本身，本方法返回 {@code null}。替换 MUST 逐处确认调用方
     * 能否接受 {@code null}，NEVER 混在分包这一批里改。</p>
     *
     * @param stationCode  站点编码，可为空
     * @param stationNames {@link #resolveStationNames} 的返回值
     * @return 中文站名，查不到时返回原编码；编码为空时返回 {@code null}
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
     * @param exits   出站站点编码集合
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
