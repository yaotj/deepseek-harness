package com.chinasofti.huateng.accsimulator.controller;

import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import com.chinasofti.huateng.accsimulator.entity.AccSimulationHistory;
import com.chinasofti.huateng.accsimulator.model.AccEmployeeCardSaveRequest;
import com.chinasofti.huateng.accsimulator.model.AccNotifySimulationRequest;
import com.chinasofti.huateng.accsimulator.model.AccUpdateSimulationRequest;
import com.chinasofti.huateng.accsimulator.model.SimulationExchange;
import com.chinasofti.huateng.accsimulator.service.AccSimulatorService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/page/acc-simulator")
public class AccSimulatorController {

    private final AccSimulatorService service;


    public AccSimulatorController(AccSimulatorService service) {
        this.service = service;
    }


    @PostMapping("/notify")
    public ResultVO<SimulationExchange> notify(@RequestBody AccNotifySimulationRequest request) {
        return ResultMapper.ok(service.sendNotify(request));
    }


    @PostMapping("/update-notify")
    public ResultVO<SimulationExchange> updateNotify(@RequestBody AccUpdateSimulationRequest request) {
        return ResultMapper.ok(service.sendUpdateNotify(request));
    }


    @GetMapping("/history")
    public ResultVO<PageInfo<AccSimulationHistory>> history(@RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "10") int pageSize,
                                                            @RequestParam(required = false) String operation) {
        return ResultMapper.ok(service.history(pageNum, pageSize, operation));
    }


    @GetMapping("/cards")
    public ResultVO<PageInfo<AccEmployeeCard>> cards(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) String cardNo,
                                                     @RequestParam(required = false) String employeeName,
                                                     @RequestParam(required = false) Integer cardStatus) {
        return ResultMapper.ok(service.cards(pageNum, pageSize, cardNo, employeeName, cardStatus));
    }


    @PostMapping("/cards")
    public ResultVO<Void> saveCard(@RequestBody AccEmployeeCardSaveRequest request) {
        try {
            service.saveCard(request);
            return ResultMapper.ok();
        } catch (IllegalArgumentException ex) {
            return ResultMapper.illegalParams(ex.getMessage());
        }
    }


    @DeleteMapping("/cards")
    public ResultVO<Void> clearCards() {
        service.clearCards();
        return ResultMapper.ok();
    }


    @DeleteMapping("/history")
    public ResultVO<Void> clearHistory() {
        service.clearHistory();
        return ResultMapper.ok();
    }
}
