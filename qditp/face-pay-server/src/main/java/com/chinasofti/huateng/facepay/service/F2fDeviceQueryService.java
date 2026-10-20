package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusPageVO;
import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusSummaryVO;
import com.chinasofti.huateng.facepay.mapper.F2fDeviceStatusMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 运营端设备心跳状态（{@code F2F_DEVICE_STATUS}）的只读查询。 */
@Service
public class F2fDeviceQueryService {

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceQueryService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    /** 在线标志取值，与 {@code F2F_DEVICE_STATUS.ONLINE_FLAG} 的注释口径一致。 */
    private static final String ONLINE = "1";

    private static final String OFFLINE = "0";

    private final F2fDeviceStatusMapper deviceStatusMapper;

    public F2fDeviceQueryService(F2fDeviceStatusMapper deviceStatusMapper) {
        this.deviceStatusMapper = deviceStatusMapper;
    }

    /** 设备心跳状态分页查询入参。 */
    public record DevicePageQuery(String channel,
                                  String onlineFlag,
                                  String deviceId,
                                  Integer pageNum,
                                  Integer pageSize) {
    }

    /** 查询判定结果，调用方 MUST 穷尽分支后各自组响应壳。 */
    public sealed interface PageOutcome {

        /**
         * 查询成功。
         *
         * @param list    当前页设备
         * @param total   命中总数，受筛选条件影响
         * @param summary 全表汇总，不受筛选条件影响
         */
        record Ok(List<F2fDeviceStatusPageVO> list, long total, F2fDeviceStatusSummaryVO summary)
                implements PageOutcome {
        }

        /** 入参非法，没有查库。 */
        record Rejected(String reason) implements PageOutcome {
        }
    }

    /**
     * 分页查询设备最新心跳状态。
     *
     * <p>本表只有「最新状态」没有历史，且行数就是设备台数（当前 21 行），故不设「必须给检索范围」的限制，
     * 与当面付订单页不同——那边是业务流水表，无条件全表扫会伤库。
     */
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

    /**
     * 转义 LIKE 通配符，避免用户输入的 {@code %} / {@code _} 退化成通配符、悄悄放大命中集。
     *
     * <p>反斜杠 MUST 先处理，否则会把后面刚补上的转义符再转一次。
     *
     * @return 转义后的关键字，可直接拼进带 {@code ESCAPE '\'} 的 LIKE；入参为 null 时原样返回
     */
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
