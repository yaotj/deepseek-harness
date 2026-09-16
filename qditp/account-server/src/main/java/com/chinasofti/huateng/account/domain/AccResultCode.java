package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.common.response.ResultVO;

/**
 * ACC 出向响应「算不算成功」的<b>唯一判据</b>。
 *
 * <p>为什么需要它：ACC 侧同一批接口回过两种成功码 —— ITP 报文体系的 {@code 0000} 与
 * {@code ResultVO} 体系的 {@code 200}，因此 4 个调用点各自写了
 * {@code !"0000".equals(x) && !"200".equals(x)} 这样的双重否定
 * （{@code EmployeeCardOutboundServiceImpl} 两处、{@code EmployeeCardServiceImpl} 两处）。
 * 双重否定漏一个分支就变成「把成功当失败」或反之，而两者都不会报错。</p>
 *
 * <p><b>NEVER 把本类用在安全服务（acc-security-server）的响应上</b>：那条链路走
 * {@code SecurityClient.buildBaseResponse}，只会回 {@code ResultVO} 的码
 * （成功 {@code 200}、失败 {@code 500} / {@code 400}），从不回 {@code 0000}。
 * 在那里额外放行 {@code 0000} 等于凭空扩大成功集合，
 * 见 {@code CardPoolAllocationServiceImpl.requestHceCardData} 直接用
 * {@link ResultVO#SUCCESS_CODE} 单码判定。</p>
 *
 * <p>两个取值都<b>复用已有常量</b>（{@link AccountErrorCodeEnum#SUCCESS} 与
 * {@link ResultVO#SUCCESS_CODE}），<b>NEVER 在本类里重新写字面量</b>。</p>
 */
public final class AccResultCode {

    private AccResultCode() {
    }

    /**
     * ACC 响应码是否表示成功。{@code null} 与空串一律<b>不</b>算成功。
     *
     * <p>注意有一处调用点的语义是「<b>有码且不是成功码</b>才拒绝」
     * （{@code EmployeeCardOutboundServiceImpl} 的 {@code resultCode} 缺失时按成功放过），
     * 那里 MUST 保留 {@code StringUtils.hasText} 的前置判断，
     * <b>NEVER 直接换成 {@code !isSuccess(...)}</b> —— 会把「没回码」从放行变成拒绝。</p>
     */
    public static boolean isSuccess(String retCode) {
        return AccountErrorCodeEnum.SUCCESS.getCode().equals(retCode)
                || ResultVO.SUCCESS_CODE.equals(retCode);
    }
}
