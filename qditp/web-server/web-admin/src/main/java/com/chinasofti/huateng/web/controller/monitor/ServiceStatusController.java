package com.chinasofti.huateng.web.controller.monitor;

import com.chinasofti.huateng.common.core.domain.AjaxResult;
import com.chinasofti.huateng.web.service.ServiceStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/monitor/service-status")
public class ServiceStatusController {

    private final ServiceStatusService serviceStatusService;

    public ServiceStatusController(ServiceStatusService serviceStatusService) {
        this.serviceStatusService = serviceStatusService;
    }

    @PreAuthorize("@ss.hasPermi('monitor:serviceStatus:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        return serviceStatusService.list();
    }
}
