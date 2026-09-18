package com.chinasofti.huateng.accsimulator;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestTemplateBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class AccSimulatorService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestTemplate restTemplate;
    private final AccSimulatorProperties properties;
    private final AccEmployeeCardMapper cardMapper;
    private final Deque<SimulationExchange> history = new ArrayDeque<>();

    public AccSimulatorService(RestTemplateBuilder restTemplateBuilder, AccSimulatorProperties properties,
                               AccEmployeeCardMapper cardMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.properties = properties;
        this.cardMapper = cardMapper;
    }

    public SimulationExchange sendNotify(AccNotifySimulationRequest request) {
        String targetUrl = resolveTarget(request.getTargetUrl(), "/employee_card/notify");
        List<EmployeeCardInfoDTO> cards = request.getCardList() == null ? List.of() : request.getCardList();
        cards.forEach(this::saveCard);
        return send("状态通知", targetUrl, JSON.toJSONString(cards), commonForm(request.getProviderId(), request.getCharset(),
                request.getFormat(), request.getDeviceId(), request.getSignType(), request.getSign()), "cardList");
    }

    public SimulationExchange sendUpdateNotify(AccUpdateSimulationRequest request) {
        String targetUrl = resolveTarget(request.getTargetUrl(), "/employee_card/update_notify");
        saveUpdate(request.getEmployee());
        return send("资料变更通知", targetUrl, JSON.toJSONString(request.getEmployee()), commonForm(request.getProviderId(),
                request.getCharset(), request.getFormat(), request.getDeviceId(), request.getSignType(), request.getSign()), null);
    }

    public List<SimulationExchange> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }

    public List<AccEmployeeCard> cards() {
        return cardMapper.selectAll();
    }

    public void clearCards() {
        cardMapper.deleteAll();
    }

    public AccEmployeeCard findCard(String cardNo) {
        return cardMapper.selectByCardNo(cardNo);
    }

    private void saveCard(EmployeeCardInfoDTO source) {
        if (source == null || source.getCardNo() == null || source.getCardNo().isBlank()) {
            return;
        }
        AccEmployeeCard target = new AccEmployeeCard();
        target.setCardNo(source.getCardNo());
        target.setPhone(source.getPhone());
        target.setCardStatus(source.getCardStatus() == null ? 3 : source.getCardStatus());
        target.setEmployeeName(source.getEmployeeName());
        target.setIdCardNo(source.getIdCardNo());
        target.setCompany(source.getCompany());
        target.setCenter(source.getCenter());
        target.setDepartment(source.getDepartment());
        target.setPosition(source.getPosition());
        target.setPhoto(source.getPhotoUrl());
        cardMapper.merge(target);
    }

    private void saveUpdate(EmployeeInfoUpdateNotifyReqDTO source) {
        if (source == null || source.getCardNo() == null || source.getCardNo().isBlank()) {
            return;
        }
        AccEmployeeCard target = cardMapper.selectByCardNo(source.getCardNo());
        if (target == null) {
            target = new AccEmployeeCard();
            target.setCardNo(source.getCardNo());
            target.setCardStatus(3);
        }
        target.setCompany(source.getCompany());
        target.setCenter(source.getCenter());
        target.setDepartment(source.getDepartment());
        target.setPosition(source.getPosition());
        target.setPhoto(source.getPhoto());
        if (target.getPhone() == null) {
            target.setPhone("");
        }
        cardMapper.merge(target);
    }

    private SimulationExchange send(String operation, String targetUrl, String bizData, MultiValueMap<String, Object> common,
                                    String ignored) {
        long started = System.nanoTime();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.addAll(common);
        form.add("bizData", bizData);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, new HttpEntity<>(form, headers), String.class);
            SimulationExchange exchange = exchange(operation, targetUrl, response.getStatusCode().is2xxSuccessful(),
                    response.getStatusCode().value(), bizData, response.getBody(), null, started);
            remember(exchange);
            return exchange;
        } catch (RestClientResponseException ex) {
            SimulationExchange exchange = exchange(operation, targetUrl, false, ex.getStatusCode().value(), bizData,
                    ex.getResponseBodyAsString(), ex.getMessage(), started);
            remember(exchange);
            return exchange;
        } catch (RestClientException ex) {
            SimulationExchange exchange = exchange(operation, targetUrl, false, null, bizData, null, ex.getMessage(), started);
            remember(exchange);
            return exchange;
        }
    }

    private MultiValueMap<String, Object> commonForm(String providerId, String charset, String format, String deviceId,
                                                      String signType, String sign) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("providerId", valueOrDefault(providerId, properties.getProviderId()));
        form.add("charset", valueOrDefault(charset, properties.getCharset()));
        form.add("format", valueOrDefault(format, properties.getFormat()));
        form.add("timestamp", TIMESTAMP.format(LocalDateTime.now()));
        form.add("deviceId", valueOrDefault(deviceId, properties.getDeviceId()));
        form.add("signType", valueOrDefault(signType, properties.getSignType()));
        form.add("sign", sign == null ? "" : sign);
        return form;
    }

    private String resolveTarget(String targetUrl, String path) {
        if (targetUrl != null && !targetUrl.isBlank()) {
            return targetUrl;
        }
        return properties.getFepAccBaseUrl().replaceAll("/$", "") + path;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private SimulationExchange exchange(String operation, String targetUrl, boolean success, Integer status,
                                        String requestBody, String responseBody, String error, long started) {
        return new SimulationExchange(operation, targetUrl, success, status, requestBody, responseBody, error,
                LocalDateTime.now().toString(), (System.nanoTime() - started) / 1_000_000);
    }

    private void remember(SimulationExchange exchange) {
        synchronized (history) {
            history.addFirst(exchange);
            while (history.size() > Math.max(1, properties.getHistoryLimit())) {
                history.removeLast();
            }
        }
    }
}
