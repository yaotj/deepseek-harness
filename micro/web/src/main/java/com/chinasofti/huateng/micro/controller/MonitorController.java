package com.chinasofti.huateng.micro.controller;

import com.chinasofti.huateng.micro.monitor.jvm.JvmObjectTop;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "jvm")
@RestController
@RequestMapping(value = "/monitor")
public class MonitorController {

    @Operation(summary = "health")
    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        Map data = new HashMap();
        data.put("msg", "ok");
        return data;
    }

    @Operation(summary = "jvm对象top信息")
    @PostMapping("/getObjectCountTop")
    @ResponseBody
    public List<Map.Entry<String, Long>> getObjectCountTop(@RequestBody Map<String, String> map) {
        long c = 10L;
        if (map.get("maxSize") != null) {
            c = Long.parseLong(map.get("maxSize"));
        }
        return JvmObjectTop.getObjectCountTop(c);
    }

}
