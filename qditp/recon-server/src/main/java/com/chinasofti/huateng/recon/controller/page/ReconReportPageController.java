package com.chinasofti.huateng.recon.controller.page;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.recon.service.ReconReportQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/page/recon")
public class ReconReportPageController {

    private final ReconReportQueryService reportQueryService;

    public ReconReportPageController(ReconReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

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

    @GetMapping("/daily-stats")
    public ResultVO<Map<String, Object>> dailyStats(@RequestParam(required = false) String groupBy,
                                                    @RequestParam(required = false) String dateStart,
                                                    @RequestParam(required = false) String dateEnd) {
        return reportQueryService.dailyStats(groupBy, dateStart, dateEnd);
    }

    @GetMapping("/clearing")
    public ResultVO<Map<String, Object>> clearing(@RequestParam(required = false) String billDateStart,
                                                  @RequestParam(required = false) String billDateEnd,
                                                  @RequestParam(required = false) String paymentChannel,
                                                  @RequestParam(required = false) String paymentVendor) {
        return reportQueryService.clearing(billDateStart, billDateEnd, paymentChannel, paymentVendor);
    }
}
