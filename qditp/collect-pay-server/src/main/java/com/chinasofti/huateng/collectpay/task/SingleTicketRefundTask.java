package com.chinasofti.huateng.collectpay.task;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.service.AppOrderService;
import com.chinasofti.huateng.collectpay.service.BomOrderService;
import com.chinasofti.huateng.collectpay.service.TvmTopupService;
import com.chinasofti.huateng.common.response.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 未使用的单程票退款
 */
@Component
@Slf4j
@RequestMapping("/pay/singleTicketRefundTask")
public class SingleTicketRefundTask {

    @Autowired
    private AppOrderService appOrderService;
    @Autowired
    private TvmTopupService tvmTopupService;
    @Autowired
    private BomOrderService bomOrderService;


    /**
     * 对于使用APP在线购票的订单（购票后未取票）做定时查询及退款
     */
//    @PostMapping("/refundAppNotTakeTickets")
    @Scheduled(cron = "0 0 20 * * ?")
    public CommonResult refundAppNotTakeTickets() {
        log.info("收到由 web-server Quartz 定时任务发起的调用 appTicketRefund 接口");

        try {
            JSONObject result = appOrderService.refundAppNotTakeTickets();
            log.info("refundAppNotTakeTickets 定时任务调用结束 result is {}", result);
            return returnSuccess();
        } catch (Exception e) {
            return returnFail();
        }

    }

    /**
     * tvm充值退款
     */
    @Scheduled(cron = "0 0 20 * * ?")
    public CommonResult refundTvmTopupNotTakeTickets() {
        log.info("收到由 web-server Quartz 定时任务发起的调用 refundTvmTopupNotTakeTickets 接口");

        try {
            JSONObject result = tvmTopupService.refundTvmTopupNotTakeTickets();
            log.info("refundTvmTopupNotTakeTickets 定时任务调用结束 result is {}", result);
            return returnSuccess();
        } catch (Exception e) {
            return returnFail();
        }

    }

    /**
     * bom售票的订单（购票后未取票）做定时查询及退款
     */
//    @PostMapping("/refundBomSaleNotTakeTickets")
    @Scheduled(cron = "0 1 9,15,21 * * ?")
    public CommonResult refundBomSaleNotTakeTickets() {
        log.info("收到由 web-server Quartz 定时任务发起的调用 refundBomSaleNotTakeTickets 接口");

        try {
            JSONObject result = bomOrderService.refundBomSaleNotTakeTickets();
            log.info("refundBomSaleNotTakeTickets 定时任务调用结束 result is {}", result);
            return returnSuccess();
        } catch (Exception e) {
            return returnFail();
        }

    }

    /**
     * bom充值的订单（购票后未取票）做定时查询及退款
     */
//    @PostMapping("/refundBomTopupNotTakeTickets")
    @Scheduled(cron = "0 0 9,15,21 * * ?")
    public CommonResult refundBomTopupNotTakeTickets() {
        log.info("收到由 web-server Quartz 定时任务发起的调用 refundBomTopupNotTakeTickets 接口");

        try {
            JSONObject result = bomOrderService.refundBomTopupNotTopup();
            log.info("refundBomSaleNotTakeTickets 定时任务调用结束 result is {}", result);
            return returnSuccess();
        } catch (Exception e) {
            return returnFail();
        }

    }


    private CommonResult returnSuccess() {
        CommonResult response = new CommonResult();
        response.setRetCode("0000");
        response.setRetMsg("调用成功");
        return response;
    }

    private CommonResult returnFail() {
        CommonResult response = new CommonResult();
        response.setRetCode("9999");
        response.setRetMsg("调用失败");
        return response;
    }

}





