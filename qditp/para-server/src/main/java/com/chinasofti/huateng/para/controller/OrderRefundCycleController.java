package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ticket.OrderRefundCycle;
import com.chinasofti.huateng.para.service.OrderRefundCycleService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 订单退款周期参数维护接口。 */
@RestController
@RequestMapping("/page/order-refund-cycle")
public class OrderRefundCycleController {
    private final OrderRefundCycleService orderRefundCycleService;

    public OrderRefundCycleController(OrderRefundCycleService orderRefundCycleService) {
        this.orderRefundCycleService = orderRefundCycleService;
    }

    /** 分页查询票卡类型的退款周期配置。 */
    @GetMapping
    public ResultVO<PageInfo<OrderRefundCycle>> page(@RequestParam(required = false) String ticketType,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return orderRefundCycleService.page(ticketType, pageNum, pageSize);
    }

    /** 新增票卡类型退款周期配置。 */
    @PostMapping
    public ResultVO<Void> create(@RequestBody OrderRefundCycle request) {
        return orderRefundCycleService.create(request);
    }

    /** 更新退款周期和备注，路径中的票卡类型不可修改。 */
    @PutMapping("/{ticketType}")
    public ResultVO<Void> update(@PathVariable String ticketType, @RequestBody OrderRefundCycle request) {
        return orderRefundCycleService.update(ticketType, request);
    }

    /** 删除指定票卡类型的退款周期配置。 */
    @DeleteMapping("/{ticketType}")
    public ResultVO<Void> delete(@PathVariable String ticketType) {
        return orderRefundCycleService.delete(ticketType);
    }
}
