package com.chinasofti.huateng.collectpay.controller.page;

import com.chinasofti.huateng.collectpay.mapper.BomNoCashOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmTopupOrderMapper;
import com.chinasofti.huateng.collectpay.model.page.DailyTopupStatView;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营端充值日报表查询（对标旧 ITP 报表管理组「TVM充值日报表」/「非现金充值数据查询」，只读）。
 *
 * <p>纯查询口径：按下单日期直接聚合订单表，不建聚合表；日期窗必填（yyyy-MM-dd）。
 */
@RestController
@RequestMapping("/page/report")
public class TopupReportPageController {

    private final TvmTopupOrderMapper tvmTopupOrderMapper;
    private final BomNoCashOrderMapper bomNoCashOrderMapper;

    public TopupReportPageController(TvmTopupOrderMapper tvmTopupOrderMapper,
                                     BomNoCashOrderMapper bomNoCashOrderMapper) {
        this.tvmTopupOrderMapper = tvmTopupOrderMapper;
        this.bomNoCashOrderMapper = bomNoCashOrderMapper;
    }

    /** TVM 充值日报表：TBL_TVM_ORDER_TOPUP 按日聚合。 */
    @GetMapping("/tvm-topup-daily")
    public ResultVO<Map<String, Object>> tvmTopupDaily(@RequestParam(required = false) String dateStart,
                                                        @RequestParam(required = false) String dateEnd) {
        String windowStart = trimToNull(dateStart);
        String windowEnd = trimToNull(dateEnd);
        if (windowStart == null || windowEnd == null || windowStart.compareTo(windowEnd) > 0) {
            return ResultMapper.illegalParams("请同时填写完整的下单日期范围");
        }
        return ResultMapper.ok(Map.of("list",
                tvmTopupOrderMapper.selectTvmTopupDaily(windowStart + " 00:00:00", windowEnd + " 23:59:59")));
    }

    /** 非现金充值数据查询：TBL_BOM_ORDER_PAY（TRANS_TYPE=22 充值）按日聚合。 */
    @GetMapping("/nocash-topup")
    public ResultVO<Map<String, Object>> nocashTopup(@RequestParam(required = false) String dateStart,
                                                     @RequestParam(required = false) String dateEnd) {
        String windowStart = trimToNull(dateStart);
        String windowEnd = trimToNull(dateEnd);
        if (windowStart == null || windowEnd == null || windowStart.compareTo(windowEnd) > 0) {
            return ResultMapper.illegalParams("请同时填写完整的下单日期范围");
        }
        return ResultMapper.ok(Map.of("list",
                bomNoCashOrderMapper.selectNocashTopupDaily(windowStart + " 00:00:00", windowEnd + " 23:59:59")));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
