package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.KeyVersionView;

import java.util.List;

/**
 * 综管台密钥版本查看（只读）。NEVER 返回任何密钥材料明文。
 */
public interface KeyVersionQueryService {

    /**
     * 汇总各密钥域的当前版本：AGM（按接入方）、CA 密钥仓库、HCE 静态密钥。
     *
     * @return 各域版本视图列表，永不返回 {@code null}
     */
    ResultVO<List<KeyVersionView>> versions();
}
