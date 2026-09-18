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

/**
 * ACC 模拟器服务实现。
 *
 * <p>负责组装 multipart/form-data 表单向 FEP/ACC 发送模拟通知，
 * 同时维护本地模拟员工卡数据与调用历史记录。</p>
 */
@Service
public class AccSimulatorServiceImpl implements AccSimulatorService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestTemplate restTemplate;
    private final AccSimulatorProperties properties;
    private final AccEmployeeCardMapper cardMapper;
    private final AccSimulationHistoryMapper historyMapper;

    /**
     * 构造 ACC 模拟器服务。
     *
     * @param restTemplateBuilder REST 客户端构建器
     * @param properties          ACC 模拟器配置
     * @param cardMapper          员工卡数据访问
     * @param historyMapper       调用历史数据访问
     */
    public AccSimulatorServiceImpl(RestTemplateBuilder restTemplateBuilder, AccSimulatorProperties properties,
                                   AccEmployeeCardMapper cardMapper,
                                   AccSimulationHistoryMapper historyMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.properties = properties;
        this.cardMapper = cardMapper;
        this.historyMapper = historyMapper;
    }

    /** {@inheritDoc} */
    @Override
    public SimulationExchange sendNotify(AccNotifySimulationRequest request) {
        String targetUrl = resolveTarget(request.getTargetUrl(), "/employee_card/notify");
        List<EmployeeCardInfoDTO> cards = request.getCardList() == null ? List.of() : request.getCardList();
        cards.forEach(this::saveCard);
        return send("状态通知", targetUrl, JSON.toJSONString(Map.of("cardList", cards)), commonForm(request.getProviderId(), request.getCharset(),
                request.getFormat(), request.getDeviceId(), request.getSignType(), request.getSign()), "cardList");
    }

    /** {@inheritDoc} */
    @Override
    public SimulationExchange sendUpdateNotify(AccUpdateSimulationRequest request) {
        String targetUrl = resolveTarget(request.getTargetUrl(), "/employee_card/update_notify");
        saveUpdate(request.getEmployee());
        return send("资料变更通知", targetUrl, JSON.toJSONString(request.getEmployee()), commonForm(request.getProviderId(),
                request.getCharset(), request.getFormat(), request.getDeviceId(), request.getSignType(), request.getSign()), null);
    }

    /** {@inheritDoc} */
    @Override
    public PageInfo<AccSimulationHistory> history(int pageNum, int pageSize, String operation) {
        PageHelper.startPage(pageNum, pageSize);
        List<AccSimulationHistory> list = historyMapper.selectPage(operation);
        return new PageInfo<>(list);
    }

    /** {@inheritDoc} */
    @Override
    public void clearHistory() {
        historyMapper.deleteAll();
    }

    /** {@inheritDoc} */
    @Override
    public PageInfo<AccEmployeeCard> cards(int pageNum, int pageSize, String cardNo, String employeeName, Integer cardStatus) {
        PageHelper.startPage(pageNum, pageSize);
        List<AccEmployeeCard> list = cardMapper.selectPage(cardNo, employeeName, cardStatus);
        return new PageInfo<>(list);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void clearCards() {
        cardMapper.deleteAll();
    }

    /** {@inheritDoc} */
    @Override
    public AccEmployeeCard findCard(String cardNo) {
        return cardMapper.selectByCardNo(cardNo);
    }

    /**
     * 将状态通知中的员工卡信息同步到本地模拟库。
     *
     * @param source 员工卡信息 DTO
     */
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

    /**
     * 将资料变更通知中的员工信息同步到本地模拟库；卡号不存在时自动创建。
     *
     * @param source 员工资料变更通知 DTO
     */
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

    /**
     * 以 multipart/form-data 形式向目标地址发送模拟通知，并记录交互结果。
     *
     * @param operation 操作类型名称
     * @param targetUrl 目标地址
     * @param bizData   业务数据 JSON 串
     * @param common    公共表单参数
     * @param ignored   保留参数，未使用
     * @return 模拟调用的完整交互记录
     */
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

    /**
     * 组装公共表单参数，空值使用配置中的默认值。
     *
     * @param providerId 服务提供方标识
     * @param charset    请求字符集
     * @param format     数据格式
     * @param deviceId   设备标识
     * @param signType   签名类型
     * @param sign       签名值
     * @return 公共表单参数
     */
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

    /**
     * 解析目标地址，未指定时使用配置中的基础地址拼接默认路径。
     *
     * @param targetUrl 用户指定的目标地址
     * @param path      默认接口路径
     * @return 最终目标地址
     */
    private String resolveTarget(String targetUrl, String path) {
        if (targetUrl != null && !targetUrl.isBlank()) {
            return targetUrl;
        }
        return properties.getFepAccBaseUrl().replaceAll("/$", "") + path;
    }

    /**
     * 空值兜底，返回默认值。
     *
     * @param value    原始值
     * @param fallback 默认值
     * @return 原始值或默认值
     */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 构建一次模拟调用的交互记录。
     *
     * @param operation    操作类型
     * @param targetUrl    目标地址
     * @param success      传输是否成功
     * @param status       HTTP 状态码
     * @param requestBody  请求体
     * @param responseBody 响应体
     * @param error        错误信息
     * @param started      起始纳秒时间戳
     * @return 交互记录
     */
    private SimulationExchange exchange(String operation, String targetUrl, boolean success, Integer status,
                                        String requestBody, String responseBody, String error, long started) {
        return new SimulationExchange(operation, targetUrl, success, status, requestBody, responseBody, error,
                LocalDateTime.now().toString(), (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * 将交互记录持久化到历史记录表。
     *
     * @param exchange 交互记录
     */
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
