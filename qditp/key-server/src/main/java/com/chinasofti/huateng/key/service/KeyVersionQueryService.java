package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.KeyVersionView;

import java.util.List;

/**
 * 综管台密钥版本查看（只读）。
 *
 * <p><b>安全红线</b>：本接口只暴露版本号、状态、时间等元信息，NEVER 返回任何
 * 密钥材料明文（KEY_VALUE / KEY_PRIVATE / KEY_PUBLIC / KEY_WRAP_VALUE*）。
 * 任何一个域查询失败时该域记一条「查询失败」行而不让整体 500，避免单个域的
 * 库表问题把整个监控页打挂。</p>
 */
public interface KeyVersionQueryService {

    /**
     * 汇总各密钥域的当前版本：AGM（按接入方）、CA 密钥仓库、HCE 静态密钥。
     *
     * @return 各域版本视图列表，永不返回 {@code null}
     */
    ResultVO<List<KeyVersionView>> versions();
}
