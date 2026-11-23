package com.chinasofti.huateng.rpc.blacklist;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListRespDTO;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.model.app.BlacklistExportDTO;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.model.app.HceBlacklistFileGenRespDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class BlacklistClient extends ProxyWebClient {

    public BlacklistClient(@Value("${service.blacklist.url:blacklist-service}") String baseUrl,
                           @Value("${service.blacklist.openLogger:true}") boolean openLogger,
                           WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }


    public QueryBlackListResult queryBlackList(@RequestBody QueryBlackListReqDTO request) {
        String result = postJsonAndGetResponse("/queryBlackList", request);
        return JSONUtil.toBean(result, new TypeReference<QueryBlackListResult>() {
        }, true);
    }


    public AlipayTripReceiveBlackListRespDTO alipayTripReceiveBlackList(@RequestBody AlipayTripReceiveBlackListReqDTO request) {
        QueryBlackListReqDTO appRequest = new QueryBlackListReqDTO();
        appRequest.setCardId(request.getCardId());
        QueryBlackListResult appResult = queryBlackList(appRequest);
        AlipayTripReceiveBlackListRespDTO response = new AlipayTripReceiveBlackListRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }


    public BlackListOperateResult addBlackList(@RequestBody AddBlackListReqDTO request) {
        String result = postJsonAndGetResponse("/addBlackList", request);
        return JSONUtil.toBean(result, BlackListOperateResult.class, true);
    }


    public BlacklistReleaseInspectRespDTO inspectReleasable(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/blacklist/inspectReleasable", new HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<BlacklistReleaseInspectRespDTO>() {
        }, true);
    }


    public BlacklistAutoReleaseRespDTO autoRelease(Integer limit, Map<String, String> headers) {
        String url = limit == null ? "/internal/blacklist/auto-release"
                : "/internal/blacklist/auto-release?limit=" + limit;
        String result = postJsonAndGetResponse(url, new HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<BlacklistAutoReleaseRespDTO>() {
        }, true);
    }


    /**
     * 补款付清后的单卡实时解黑（ACTIVE + 欠费类，复用全表 autoRelease 的欠费判据）。
     * 失败由调用方仅记日志不阻断补款收敛；每天两次的全表 {@link #autoRelease} 仍是兜底。
     */
    public BlacklistAutoReleaseRespDTO releaseCardIfSettled(String cardId, Map<String, String> headers) {
        String url = "/internal/blacklist/release-card-if-settled?cardId=" + cardId;
        String result = postJsonAndGetResponse(url, new HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<BlacklistAutoReleaseRespDTO>() {
        }, true);
    }


    public OutboxScan.Result compensateChannelSyncAdd(Integer limit, Map<String, String> headers) {
        return postOutboxScan("/internal/blacklist/channel-sync/compensate-add", limit, headers);
    }


    public OutboxScan.Result compensateChannelSyncRelease(Integer limit, Map<String, String> headers) {
        return postOutboxScan("/internal/blacklist/channel-sync/compensate-release", limit, headers);
    }


    private OutboxScan.Result postOutboxScan(String path, Integer limit, Map<String, String> headers) {
        String url = limit == null ? path : path + "?limit=" + limit;
        String result = postJsonAndGetResponse(url, new HashMap<>(), headers);
        JSONObject json = JSONUtil.parseObj(result);
        return new OutboxScan.Result(json.getInt("scanned", 0), json.getInt("success", 0),
                json.getInt("failed", 0));
    }


    public HceBlacklistFileGenRespDTO generateHceBlacklistFile(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/blacklist/hce-file/generate", new HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<HceBlacklistFileGenRespDTO>() {
        }, true);
    }


    public List<BlacklistExportDTO> listBlacklists(String cardId, String thirdUserId, String status,
            String channelSyncStatus, String createTimeBegin, String createTimeEnd) {
        Map<String, String> params = new HashMap<>();
        putIfNotEmpty(params, "cardId", cardId);
        putIfNotEmpty(params, "thirdUserId", thirdUserId);
        putIfNotEmpty(params, "status", status);
        putIfNotEmpty(params, "channelSyncStatus", channelSyncStatus);
        putIfNotEmpty(params, "createTimeBegin", createTimeBegin);
        putIfNotEmpty(params, "createTimeEnd", createTimeEnd);
        int pageSize = 1000;
        int pageNum = 1;
        List<BlacklistExportDTO> all = new ArrayList<>();
        while (true) {
            params.put("pageNum", String.valueOf(pageNum));
            params.put("pageSize", String.valueOf(pageSize));
            String result = getAndGetResponse("/page/blacklist", params, new HashMap<>());
            JSONObject data = JSONUtil.parseObj(result).getJSONObject("data");
            List<BlacklistExportDTO> page = data.getBeanList("list", BlacklistExportDTO.class);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        return all;
    }

    private void putIfNotEmpty(Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) {
            params.put(key, value);
        }
    }
}
