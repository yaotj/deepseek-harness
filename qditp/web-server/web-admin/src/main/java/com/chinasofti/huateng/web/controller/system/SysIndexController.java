package com.chinasofti.huateng.web.controller.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.chinasofti.huateng.common.config.PointsConfig;
import com.chinasofti.huateng.common.utils.StringUtils;


@RestController
public class SysIndexController
{
    @Autowired
    private PointsConfig pointsConfig;


    @RequestMapping("/")
    public String index()
    {
        return StringUtils.format("欢迎使用{}后台管理框架，当前版本：v{}，请通过前端地址访问。", pointsConfig.getName(), pointsConfig.getVersion());
    }
}
