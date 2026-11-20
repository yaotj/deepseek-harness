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

import java.util.List;

@RestController
@RequestMapping("/page/order-refund-cycle")
public class OrderRefundCycleController {
    private final OrderRefundCycleService orderRefundCycleService;

    public OrderRefundCycleController(OrderRefundCycleService orderRefundCycleService) {
        this.orderRefundCycleService = orderRefundCycleService;
    }

    @GetMapping
    public ResultVO<PageInfo<OrderRefundCycle>> page(@RequestParam(required = false) String ticketType,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return orderRefundCycleService.page(ticketType, pageNum, pageSize);
    }

    @PostMapping
    public ResultVO<Void> create(@RequestBody OrderRefundCycle request) {
        return orderRefundCycleService.create(request);
    }

    @PutMapping("/{ticketType}")
    public ResultVO<Void> update(@PathVariable String ticketType, @RequestBody OrderRefundCycle request) {
        return orderRefundCycleService.update(ticketType, request);
    }

    @DeleteMapping("/{ticketType}")
    public ResultVO<Void> delete(@PathVariable String ticketType) {
        return orderRefundCycleService.delete(ticketType);
    }

    @GetMapping("/ticket-types")
    public ResultVO<List<String>> ticketTypes() {
        return orderRefundCycleService.listTicketTypes();
    }
}
