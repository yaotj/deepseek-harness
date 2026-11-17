package com.chinasofti.huateng.accsimulator.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.accsimulator.config.AccSimulatorProperties;
import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import com.chinasofti.huateng.accsimulator.entity.AccSimulationHistory;
import com.chinasofti.huateng.accsimulator.mapper.AccEmployeeCardMapper;
import com.chinasofti.huateng.accsimulator.mapper.AccSimulationHistoryMapper;
import com.chinasofti.huateng.accsimulator.model.AccEmployeeCardSaveRequest;
import com.chinasofti.huateng.accsimulator.model.AccNotifySimulationRequest;
import com.chinasofti.huateng.accsimulator.model.AccUpdateSimulationRequest;
import com.chinasofti.huateng.accsimulator.model.SimulationExchange;
import com.chinasofti.huateng.accsimulator.service.AccSimulatorService;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.boot.web.client.RestTemplateBuilder;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


@Service
public class AccSimulatorServiceImpl implements AccSimulatorService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestTemplate restTemplate;
    private final AccSimulatorProperties properties;
    private final AccEmployeeCardMapper cardMapper;
    private final AccSimulationHistoryMapper historyMapper;


    public AccSimulatorServiceImpl(RestTemplateBuilder restTemplateBuilder, AccSimulatorProperties properties,
                                   AccEmployeeCardMapper cardMapper,
                                   AccSimulationHistoryMapper historyMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.properties = properties;
        this.cardMapper = cardMapper;
        this.historyMapper = historyMapper;
    }

    @Override
    public SimulationExchange sendNotify(AccNotifySimulationRequest request) {
        String targetUrl = resolveTarget(request.getTargetUrl(), "/employee_card/notify");
        List<EmployeeCardInfoDTO> cards = request.getCardList() == null ? List.of() : request.getCardList();
        cards.forEach(this::saveCard);
        return send("状态通知", targetUrl, JSON.toJSONString(Map.of("cardList", cards)), commonForm(request.getProviderId(), request.getCharset(),
                request.getFormat(), request.getDeviceId(), request.getSignType(), request.getSign()), "cardList");
    }

    @Override
    public SimulationExchange sendUpdateNotify(AccUpdateSimulationRequest request) {
        String targetUrl = resolveTarget(request.getTargetUrl(), "/employee_card/update_notify");
        saveUpdate(request.getEmployee());
        return send("资料变更通知", targetUrl, JSON.toJSONString(request.getEmployee()), commonForm(request.getProviderId(),
                request.getCharset(), request.getFormat(), request.getDeviceId(), request.getSignType(), request.getSign()), null);
    }

    @Override
    public PageInfo<AccSimulationHistory> history(int pageNum, int pageSize, String operation) {
        PageHelper.startPage(pageNum, pageSize);
        List<AccSimulationHistory> list = historyMapper.selectPage(operation);
        return new PageInfo<>(list);
    }

    @Override
    public void clearHistory() {
        historyMapper.deleteAll();
    }

    @Override
    public PageInfo<AccEmployeeCard> cards(int pageNum, int pageSize, String cardNo, String employeeName, Integer cardStatus) {
        PageHelper.startPage(pageNum, pageSize);
        List<AccEmployeeCard> list = cardMapper.selectPage(cardNo, employeeName, cardStatus);
        return new PageInfo<>(list);
    }

    @Override
    public void saveCard(AccEmployeeCardSaveRequest request) {
        if (request == null || request.getCardNo() == null || request.getCardNo().isBlank()) {
            throw new IllegalArgumentException("员工号不能为空");
        }
        AccEmployeeCard card = new AccEmployeeCard();
        card.setCardNo(request.getCardNo().trim());
        card.setPhone(request.getPhone());
        card.setCardStatus(request.getCardStatus() == null ? 3 : request.getCardStatus());
        if (card.getCardStatus() < 1 || card.getCardStatus() > 4) {
            throw new IllegalArgumentException("状态只能是1至4");
        }
        card.setEmployeeName(request.getEmployeeName());
        card.setIdCardNo(request.getIdCardNo());
        card.setCompany(request.getCompany());
        card.setCenter(request.getCenter());
        card.setDepartment(request.getDepartment());
        card.setPosition(request.getPosition());
        card.setPhoto(request.getPhoto());
        cardMapper.merge(card);
    }

    @Override
    public void clearCards() {
        cardMapper.deleteAll();
    }

    @Override
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
        AccSimulationHistory history = new AccSimulationHistory();
        history.setOperation(exchange.operation());
        history.setTargetUrl(exchange.targetUrl());
        history.setTransportSuccess(exchange.transportSuccess());
        history.setHttpStatus(exchange.httpStatus());
        history.setRequestBody(exchange.requestBody());
        history.setResponseBody(exchange.responseBody());
        history.setErrorMessage(exchange.errorMessage());
        history.setCreatedAt(LocalDateTime.parse(exchange.createdAt()));
        history.setElapsedMs(exchange.elapsedMs());
        historyMapper.insert(history);
    }
}
