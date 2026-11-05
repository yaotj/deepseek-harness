package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusPageVO;
import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusSummaryVO;
import com.chinasofti.huateng.facepay.mapper.F2fDeviceStatusMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class F2fDeviceQueryService {

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceQueryService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    private static final String ONLINE = "1";

    private static final String OFFLINE = "0";

    private final F2fDeviceStatusMapper deviceStatusMapper;

    public F2fDeviceQueryService(F2fDeviceStatusMapper deviceStatusMapper) {
        this.deviceStatusMapper = deviceStatusMapper;
    }

    public record DevicePageQuery(String channel,
                                  String onlineFlag,
                                  String deviceId,
                                  Integer pageNum,
                                  Integer pageSize) {
    }

    public sealed interface PageOutcome {

        record Ok(List<F2fDeviceStatusPageVO> list, long total, F2fDeviceStatusSummaryVO summary)
                implements PageOutcome {
        }

        record Rejected(String reason) implements PageOutcome {
        }
    }

    public PageOutcome page(DevicePageQuery query) {
        String channel = trimToNull(query.channel());
        if (channel != null && !F2fChannel.isSupported(channel)) {
            return new PageOutcome.Rejected("受理渠道只能是 01(APP)、02(TVM) 或 03(BOM)");
        }
        String onlineFlag = trimToNull(query.onlineFlag());
        if (onlineFlag != null && !ONLINE.equals(onlineFlag) && !OFFLINE.equals(onlineFlag)) {
            return new PageOutcome.Rejected("在线状态只能是 1(在线) 或 0(离线)");
        }

        int currentPage = query.pageNum() == null || query.pageNum() < 1 ? 1 : query.pageNum();
        int currentPageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        int offset = (currentPage - 1) * currentPageSize;
        String deviceIdKeyword = escapeLike(trimToNull(query.deviceId()));

        List<F2fDeviceStatusPageVO> list = deviceStatusMapper.selectDevicePage(
                channel, onlineFlag, deviceIdKeyword, offset, currentPageSize);
        long total = deviceStatusMapper.countDevicePage(channel, onlineFlag, deviceIdKeyword);
        F2fDeviceStatusSummaryVO summary = deviceStatusMapper.selectDeviceSummary();
        log.info("运营端设备心跳状态查询完成, channel={}, onlineFlag={}, pageNum={}, pageSize={}, size={}, total={}",
                channel, onlineFlag, currentPage, currentPageSize, list == null ? 0 : list.size(), total);
        return new PageOutcome.Ok(list, total, summary);
    }

    static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
