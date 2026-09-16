package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 更换手机号响应DTO（if8a_76）。
 *
 * <p>规范表118 只定义 {@code retCode} 与 {@code retMsg} 两个字段，二者由
 * {@link CommonResult} 提供，本类**不得再增加业务字段**。原先多出的 {@code thirdUserId}
 * 从未赋值、恒为 null，属契约冗余，2026-09-09 已删除。</p>
 */
public class UpdatePhoneResult extends CommonResult {
}
