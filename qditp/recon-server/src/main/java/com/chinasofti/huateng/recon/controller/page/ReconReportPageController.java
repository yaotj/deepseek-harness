package com.chinasofti.huateng.recon.controller.page;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.recon.service.ReconReportQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营端报表查询（对标旧 ITP「报表管理」组，全部只读）。
 *
 * <p>端点与页面映射：
 * <ul>
 *   <li>{@code /txn-diff}：itp平台与渠道对账明细 ×3（支付宝/微信/银联，payChannel 区分）</li>
 *   <li>{@code /third-bill}：ITP 与渠道对账报表 ×3（渠道账单行，paymentChannel 区分）</li>
 *   <li>{@code /daily-stats}：日统计 3 页（groupBy=VENDOR 两页 / HOUR 一天内分时一页）</li>
 *   <li>{@code /clearing}：业务清分详细报表</li>
 * </ul>
 */
@RestController
@RequestMapping("/page/recon")
public class ReconReportPageController {

    private final ReconReportQueryService reportQueryService;

    public ReconReportPageController(ReconReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    /** 对账差异明细分页（账单日窗必填，payChannel/diffSide/订单号可选）。 */
    @GetMapping("/txn-diff")
    public ResultVO<Map<String, Object>> txnDiff(@RequestParam(required = false) String billDateStart,
                                                 @RequestParam(required = false) String billDateEnd,
                                                 @RequestParam(required = false) String payChannel,
                                                 @RequestParam(required = false) String diffSide,
                                                 @RequestParam(required = false) String orderNo,
                                                 @RequestParam(required = false) Integer pageNum,
                                                 @RequestParam(required = false) Integer pageSize) {
        return reportQueryService.txnDiffPage(billDateStart, billDateEnd, payChannel, diffSide, orderNo,
                pageNum, pageSize);
    }

    /** 渠道账单行分页（账单日窗必填）。 */
    @GetMapping("/third-bill")
    public ResultVO<Map<String, Object>> thirdBill(@RequestParam(required = false) String billDateStart,
                                                   @RequestParam(required = false) String billDateEnd,
                                                   @RequestParam(required = false) String paymentChannel,
                                                   @RequestParam(required = false) String paymentVendor,
                                                   @RequestParam(required = false) String merchantOrderNo,
                                                   @RequestParam(required = false) Integer pageNum,
                                                   @RequestParam(required = false) Integer pageSize) {
        return reportQueryService.thirdBillPage(billDateStart, billDateEnd,
                paymentChannel, paymentVendor, merchantOrderNo, pageNum, pageSize);
    }

    /** 日统计（交易日期窗必填；groupBy=HOUR 时要求单日）。 */
    @GetMapping("/daily-stats")
    public ResultVO<Map<String, Object>> dailyStats(@RequestParam(required = false) String groupBy,
                                                    @RequestParam(required = false) String dateStart,
                                                    @RequestParam(required = false) String dateEnd) {
        return reportQueryService.dailyStats(groupBy, dateStart, dateEnd);
    }

    /** 业务清分明细（账单日窗必填，聚合行不分页）。 */
    @GetMapping("/clearing")
    public ResultVO<Map<String, Object>> clearing(@RequestParam(required = false) String billDateStart,
                                                  @RequestParam(required = false) String billDateEnd,
                                                  @RequestParam(required = false) String paymentChannel,
                                                  @RequestParam(required = false) String paymentVendor) {
        return reportQueryService.clearing(billDateStart, billDateEnd, paymentChannel, paymentVendor);
    }
}
