package com.chinasofti.huateng.accsimulator;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/page/acc-simulator")
public class AccSimulatorController {

    private final AccSimulatorService service;

    public AccSimulatorController(AccSimulatorService service) {
        this.service = service;
    }

    @PostMapping("/notify")
    public SimulationExchange notify(@RequestBody AccNotifySimulationRequest request) {
        return service.sendNotify(request);
    }

    @PostMapping("/update-notify")
    public SimulationExchange updateNotify(@RequestBody AccUpdateSimulationRequest request) {
        return service.sendUpdateNotify(request);
    }

    @GetMapping("/history")
    public List<SimulationExchange> history() {
        return service.history();
    }

    @GetMapping("/cards")
    public List<AccEmployeeCard> cards() {
        return service.cards();
    }

    @DeleteMapping("/cards")
    public Map<String, Object> clearCards() {
        service.clearCards();
        return Map.of("code", 200, "msg", "操作成功");
    }

    @DeleteMapping("/history")
    public Map<String, Object> clearHistory() {
        service.clearHistory();
        return Map.of("code", 200, "msg", "操作成功");
    }
}
