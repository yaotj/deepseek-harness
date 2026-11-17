package com.chinasofti.huateng.paysign.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.paysign.page.PayRefundOrderView;
import com.chinasofti.huateng.paysign.service.PayRefundPageQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/page/pay-refund")
public class PayRefundPageController {

    private final PayRefundPageQueryService payRefundPageQueryService;

    public PayRefundPageController(PayRefundPageQueryService payRefundPageQueryService) {
        this.payRefundPageQueryService = payRefundPageQueryService;
    }


    @GetMapping("/by-orders")
    public ResultVO<List<PayRefundOrderView>> byOrders(@RequestParam(required = false) List<String> orderNos) {
        try {
            return ResultMapper.ok(payRefundPageQueryService.byOrderNos(orderNos));
        } catch (IllegalArgumentException e) {
            return ResultMapper.illegalParams(e.getMessage());
        }
    }
}
