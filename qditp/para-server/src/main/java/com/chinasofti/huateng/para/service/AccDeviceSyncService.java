package com.chinasofti.huateng.para.service;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.para.config.ParaDeviceSyncProperties;
import com.chinasofti.huateng.para.entity.device.DeviceInfo;
import com.chinasofti.huateng.para.mapper.device.DeviceInfoMapper;
import com.chinasofti.huateng.para.model.device.AccDeviceDTO;
import com.chinasofti.huateng.para.model.device.AccDevicePageResult;
import com.chinasofti.huateng.para.model.device.DeviceSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * ACC 设备台账同步服务：分页拉取 ACC 设备配置接口，MERGE INTO 幂等写入 TBL_DEVICE_INFO。
 * <p>同步只更新 ACC 来源列，业主在页面维护的 MANAGE_MODE / REMARK 刻意不在 MERGE 更新列内。</p>
 * <p>逐条提交、无整体事务：同步幂等可重跑，中途失败重跑即可补齐，避免长事务锁表。</p>
 */
@Service
public class AccDeviceSyncService {

    private static final Logger log = LoggerFactory.getLogger(AccDeviceSyncService.class);

    /** ACC updateTime 字段格式，实测样例 2025-07-10 00:00:00。 */
    private static final DateTimeFormatter ACC_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FLAG_NO = "0";
    private static final String FLAG_YES = "1";

    private final ParaDeviceSyncProperties properties;
    private final DeviceInfoMapper deviceInfoMapper;
    private final HttpClient httpClient;

    public AccDeviceSyncService(ParaDeviceSyncProperties properties, DeviceInfoMapper deviceInfoMapper) {
        this.properties = properties;
        this.deviceInfoMapper = deviceInfoMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    /** 全量同步所有配置的 manageLevel，任一级失败即抛异常终止，调用方据此记失败。 */
    public DeviceSyncResult syncAll() {
        long start = System.currentTimeMillis();
        DeviceSyncResult result = new DeviceSyncResult();
        int fetched = 0;
        int merged = 0;
        int pages = 0;
        for (String manageLevel : properties.getManageLevels()) {
            int[] levelStat = syncOneLevel(manageLevel.trim());
            pages += levelStat[0];
            fetched += levelStat[1];
            merged += levelStat[2];
        }
        result.setManageLevels(String.join(",", properties.getManageLevels()));
        result.setPages(pages);
        result.setFetched(fetched);
        result.setMerged(merged);
        result.setElapsedMillis(System.currentTimeMillis() - start);
        log.info("ACC设备台账同步完成: levels={}, pages={}, fetched={}, merged={}, elapsed={}ms",
                result.getManageLevels(), pages, fetched, merged, result.getElapsedMillis());
        return result;
    }

    /** 同步单个 manageLevel，返回 [页数, 拉取数, 落库数]。 */
    private int[] syncOneLevel(String manageLevel) {
        int pages = 0;
        int fetched = 0;
        int merged = 0;
        int currentPage = 1;
        while (true) {
            AccDevicePageResult response = fetchPage(manageLevel, currentPage);
            if (response.getBody() == null || response.getBody().getData() == null
                    || response.getBody().getData().isEmpty()) {
                break;
            }
            List<AccDeviceDTO> data = response.getBody().getData();
            for (AccDeviceDTO dto : data) {
                deviceInfoMapper.merge(toEntity(dto, manageLevel));
                merged++;
            }
            fetched += data.size();
            pages++;
            Integer totalCount = response.getBody().getTotalCount();
            Integer pageSize = response.getBody().getPageSize();
            boolean hasMore = data.size() >= properties.getPageSize()
                    && (totalCount == null || fetched < totalCount)
                    && (pageSize == null || data.size() >= pageSize);
            if (!hasMore) {
                break;
            }
            currentPage++;
        }
        return new int[]{pages, fetched, merged};
    }

    /** 请求 ACC 单页设备数据，success=false 或非 200 一律抛异常。 */
    private AccDevicePageResult fetchPage(String manageLevel, int currentPage) {
        String withGlobal = properties.isWithGlobal() ? "true" : "false";
        String url = String.format("%s/api/v1/Config/device?currentPage=%d&pageSize=%d&manageLevel=%s&withGlobal=%s",
                properties.getBaseUrl(), currentPage, properties.getPageSize(), manageLevel, withGlobal);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new IllegalStateException("请求ACC设备配置接口失败: url=" + url, exception);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("ACC设备配置接口返回非200: url=" + url
                    + ", status=" + response.statusCode());
        }
        AccDevicePageResult result = JSON.parseObject(response.body(), AccDevicePageResult.class);
        if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
            throw new IllegalStateException("ACC设备配置接口返回失败: url=" + url
                    + ", error=" + (result == null ? null : result.getError()));
        }
        return result;
    }

    /** ACC 记录转实体；manageLevel 来自本次请求参数（接口返回体不带该字段）。 */
    private DeviceInfo toEntity(AccDeviceDTO dto, String manageLevel) {
        DeviceInfo entity = new DeviceInfo();
        entity.setDeviceId(dto.getDeviceId());
        entity.setLineId(dto.getLineId());
        entity.setLineName(dto.getLineName());
        entity.setStationId(dto.getStationId());
        entity.setStationName(dto.getStationName());
        entity.setDeviceType(dto.getDeviceTypeCode());
        entity.setDeviceTypeName(dto.getDeviceTypeName());
        entity.setDeviceName(dto.getDeviceName());
        entity.setIpAddr(dto.getIp());
        entity.setPort(dto.getPort());
        entity.setIsVirtual(Boolean.TRUE.equals(dto.getVirture()) ? FLAG_YES : FLAG_NO);
        entity.setAccIsDeleted(Boolean.TRUE.equals(dto.getDeleted()) ? FLAG_YES : FLAG_NO);
        entity.setAccUpdateTime(parseAccTime(dto.getUpdateTime()));
        entity.setAccManageLevel(manageLevel);
        return entity;
    }

    /** ACC 时间字符串转 LocalDateTime，解析失败置 null 并告警，不让单条脏时间中断整次同步。 */
    private LocalDateTime parseAccTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), ACC_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            log.warn("ACC设备更新时间解析失败, 原文={}", value);
            return null;
        }
    }
}
