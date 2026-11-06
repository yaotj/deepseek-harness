package com.chinasofti.huateng.micro.controller;

import com.chinasofti.huateng.micro.monitor.configuration.MonitorConfig;
import com.chinasofti.huateng.micro.web.Webconfig;
import com.chinasofti.huateng.micro.web.global.ASimpleResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "config")
@RestController
@RequestMapping(value = "/config")
public class ConfigController {
    public static Logger log = LoggerFactory.getLogger(ConfigController.class);

    @Autowired
    MonitorConfig monitorConfig;

    @Autowired
    Webconfig webconfig;

    private boolean allowLocalIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (ip.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        return false;
    }

    private Map<String, Boolean> showData() {
        Map<String, Boolean> data = new HashMap<>();
        data.put("other.monitor.printToLogger", monitorConfig.isPrintToLogger());
        data.put("other.monitor.checkLogWriter", monitorConfig.isCheckLogWriter());
        data.put("other.web.enableLogRequestInFilter", webconfig.isEnableLogRequestInFilter());
        data.put("other.web.enableBodyCacheFilter", webconfig.isEnableBodyCacheFilter());
        data.put("other.web.enableLogTakeTimesInInterceptor", webconfig.isEnableLogTakeTimesInInterceptor());
        return data;
    }

    @Operation(summary = "/show")
    @RequestMapping(value = "/show", method = {RequestMethod.GET})
    public ASimpleResultVo<Map<String, Boolean>> show(HttpServletRequest request) {
        ASimpleResultVo<Map<String, Boolean>> simpleResultVo = new ASimpleResultVo<>();
        if (allowLocalIp(request)) {
            simpleResultVo.setData(showData());
        }
        return simpleResultVo;
    }


    @Operation(summary = "/change")
    @RequestMapping(value = "/change/{key}/{value}", method = {RequestMethod.GET})
    public ASimpleResultVo<Map<String, Boolean>> change(HttpServletRequest request, @PathVariable String key, @PathVariable boolean value) {
        ASimpleResultVo<Map<String, Boolean>> simpleResultVo = new ASimpleResultVo<>();
        if (allowLocalIp(request)) {
            switch (key) {
                case "other.monitor.printToLogger" -> monitorConfig.setPrintToLogger(value);
                case "other.monitor.checkLogWriter" -> monitorConfig.setCheckLogWriter(value);
                case "other.web.enableLogRequestInFilter" -> webconfig.setEnableLogRequestInFilter(value);
                case "other.web.enableBodyCacheFilter" -> webconfig.setEnableBodyCacheFilter(value);
                case "other.web.enableLogTakeTimesInInterceptor" -> webconfig.setEnableLogTakeTimesInInterceptor(value);
            }
        }
        simpleResultVo.setData(showData());
        return simpleResultVo;
    }


}
