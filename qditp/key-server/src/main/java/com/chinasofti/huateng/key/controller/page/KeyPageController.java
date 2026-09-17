package com.chinasofti.huateng.key.controller.page;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.KeyVersionView;
import com.chinasofti.huateng.key.service.KeyVersionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 综管台密钥版本查看入口（只读）。响应 NEVER 包含任何密钥材料明文。 */
@RestController
@RequestMapping("/page/key")
public class KeyPageController {

    private final KeyVersionQueryService keyVersionQueryService;

    public KeyPageController(KeyVersionQueryService keyVersionQueryService) {
        this.keyVersionQueryService = keyVersionQueryService;
    }

    /** 各密钥域（AGM / CA 密钥仓库 / HCE 静态密钥）当前版本汇总。 */
    @GetMapping("/versions")
    public ResultVO<List<KeyVersionView>> versions() {
        return keyVersionQueryService.versions();
    }
}
