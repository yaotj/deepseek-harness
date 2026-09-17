package com.chinasofti.huateng.ticket.station;

import com.chinasofti.huateng.model.app.RequestStationLineInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 车站线路信息解析器 —— 把站点编码翻译成线路代码 / 线路名。 */
@Component
public class StationLineResolver {

    private static final Logger log = LoggerFactory.getLogger(StationLineResolver.class);

    /** para-server 成功码。 */
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private ParaClient paraClient;

    /**
     * 查询车站线路信息。
     *
     * @param stationCode 站点编码，空则直接返回 {@code null}（不发 RPC）
     * @return para-server 应答；
     */
    public RequestStationLineInfoResult resolveLineInfo(String stationCode) {
        if (!StringUtils.hasText(stationCode)) {
            return null;
        }
        try {
            RequestStationLineInfoReqDTO request = new RequestStationLineInfoReqDTO();
            request.setStationCode(stationCode);
            return paraClient.requestStationLineInfo(request);
        } catch (Exception e) {
            log.warn("查询车站线路信息失败, stationCode={}", stationCode, e);
            return null;
        }
    }

    /** 应答是否可用：非空 + 成功码 + {@code lineCode} 非空。 */
    public boolean isUsable(RequestStationLineInfoResult result) {
        return result != null
                && RET_SUCCESS.equals(result.getRetCode())
                && StringUtils.hasText(result.getLineCode());
    }
}
