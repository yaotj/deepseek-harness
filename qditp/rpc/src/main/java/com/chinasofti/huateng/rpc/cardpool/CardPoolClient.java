package com.chinasofti.huateng.rpc.cardpool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.model.cardpool.CardPoolBatchExportDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolOutcome;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationActionReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReserveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class CardPoolClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(CardPoolClient.class);

    private static final String RESERVATIONS_PATH = "/internal/card-pools/reservations";

    private static final String MAINTENANCE_PATH = "/internal/card-pools/maintenance";


    public CardPoolClient(@Value("${service.cardPool.url:card-pool-service}") String baseUrl,
                          @Value("${service.cardPool.openLogger:true}") boolean openLogger,
                          WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(15);
    }


    public CardPoolReserveResult reserve(CardPoolReservationReqDTO request) {
        String response;
        try {
            response = postJsonAndGetResponse(RESERVATIONS_PATH, request);
        } catch (RuntimeException ex) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "调用卡池预占异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "卡池预占无响应");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(response);
        } catch (RuntimeException ex) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "卡池预占响应无法解析: " + response);
        }
        String code = json.getStr("code");
        String message = json.getStr("msg");
        if ("400".equals(code)) {
            return CardPoolReserveResult.failure(CardPoolOutcome.REJECTED, message);
        }
        if (!"200".equals(code)) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "卡池预占返回 code=" + code + ", msg=" + message);
        }
        Object data = json.get("data");
        if (data == null) {
            return CardPoolReserveResult.failure(CardPoolOutcome.POOL_EMPTY, "该票种卡池已空");
        }
        return CardPoolReserveResult.success(JSONUtil.toBean(data.toString(), CardPoolReservationRespDTO.class));
    }


    public CardPoolActionResult confirm(String reservationId, String businessId) {
        return action(RESERVATIONS_PATH + "/" + reservationId + "/confirm", businessId, "确认");
    }


    public CardPoolActionResult release(String reservationId, String businessId) {
        return action(RESERVATIONS_PATH + "/" + reservationId + "/release", businessId, "释放");
    }


    public CardPoolActionResult runMaintenance(Map<String, String> headers) {
        String response;
        try {
            response = postJsonAndGetResponse(MAINTENANCE_PATH, Collections.emptyMap(),
                    headers == null ? Collections.emptyMap() : headers);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "调用卡池维护异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "卡池维护无响应");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(response);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "卡池维护响应无法解析: " + response);
        }
        String code = json.getStr("code");
        if (!"200".equals(code)) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED,
                    "卡池维护返回 code=" + code + ", msg=" + json.getStr("msg"));
        }
        JSONObject data = json.getJSONObject("data");
        Boolean accepted = data == null ? null : data.getBool("accepted");
        String message = data == null ? null : data.getStr("message");
        log.info("调用card-pool-server卡池维护接口返回 path={}, accepted={}, message={}",
                MAINTENANCE_PATH, accepted, message);
        return CardPoolActionResult.success("accepted=" + accepted + ", message=" + message);
    }


    private CardPoolActionResult action(String path, String businessId, String action) {
        CardPoolReservationActionReqDTO request = new CardPoolReservationActionReqDTO();
        request.setBusinessId(businessId);
        String response;
        try {
            response = postJsonAndGetResponse(path, request);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED,
                    "调用卡池" + action + "异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "卡池" + action + "无响应");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(response);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED,
                    "卡池" + action + "响应无法解析: " + response);
        }
        if ("200".equals(json.getStr("code"))) {
            return CardPoolActionResult.success();
        }
        return CardPoolActionResult.failure(CardPoolOutcome.REJECTED, json.getStr("msg"));
    }


    public List<CardPoolBatchExportDTO> listCardPoolBatches(Long batchNo, String cardType,
            String accTicketType, String source, String status,
            LocalDateTime createTimeBegin, LocalDateTime createTimeEnd) {
        Map<String, String> params = new HashMap<>();
        if (batchNo != null) {
            params.put("batchNo", String.valueOf(batchNo));
        }
        if (cardType != null && !cardType.isEmpty()) {
            params.put("cardType", cardType);
        }
        if (accTicketType != null && !accTicketType.isEmpty()) {
            params.put("accTicketType", accTicketType);
        }
        if (source != null && !source.isEmpty()) {
            params.put("source", source);
        }
        if (status != null && !status.isEmpty()) {
            params.put("status", status);
        }
        if (createTimeBegin != null) {
            params.put("createTimeBegin", createTimeBegin.toString().replace('T', ' '));
        }
        if (createTimeEnd != null) {
            params.put("createTimeEnd", createTimeEnd.toString().replace('T', ' '));
        }
        int pageSize = 1000;
        int pageNum = 1;
        List<CardPoolBatchExportDTO> all = new ArrayList<>();
        while (true) {
            params.put("pageNum", String.valueOf(pageNum));
            params.put("pageSize", String.valueOf(pageSize));
            String result = getAndGetResponse("/page/card-pools/batches", params, new HashMap<>());
            JSONObject json = JSONUtil.parseObj(result);
            List<CardPoolBatchExportDTO> page = json.getJSONObject("data").getBeanList("list", CardPoolBatchExportDTO.class);
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
}
