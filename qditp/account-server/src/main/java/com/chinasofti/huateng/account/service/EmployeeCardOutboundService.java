package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;

import java.util.List;
import java.util.Map;

/**
 * 员工码链路的出网协作者：对 APP 的静默开户注册、对 ACC 的资料查询与激活/禁用请求。
 * <p>
 * 本接口<b>只做报文组装、HTTP 调用与响应解析，不含任何业务策略</b> —— 「哪种状态允许激活、
 * ACC 的错误码该透传还是归到 9999、失败要不要开工单、要不要写事件日志」全部留在
 * {@code EmployeeCardServiceImpl}，<b>NEVER 把这些判断挪进来</b>。
 * 判据与 {@link CardPoolAllocationService} 一致，见 {@code docs/domain/decisions.md} ADR-D16 约束 3。
 * <p>
 * 三个出网地址（{@code employee-card.app-register-url} / {@code acc-query-url} / {@code acc-activate-url}）
 * 与报文公共字段（providerId / charset / format / deviceId / signType）都收在实现类里，
 * <b>NEVER 让调用方再持有一份</b>。
 */
public interface EmployeeCardOutboundService {

    /**
     * APP 注册结果：只承载「哪些卡号失败、失败原因是什么」。
     * <p>
     * 空 map 表示整批成功。<b>NEVER 用「返回 null」表示成功</b> —— 调用方是按卡号逐张判定的，
     * null 会让整批被当成成功放过去。
     */
    record AppRegisterResult(Map<String, String> failReasons) {
        /** @return 该卡号的失败原因；{@code null} 表示这一张成功 */
        public String failureReasonOf(String cardNo) {
            return failReasons.get(cardNo);
        }
    }

    /**
     * 向 APP 批量注册员工码（ACC 的 {@code cardNo} 是员工号，不是逻辑卡号；静默开户由 APP 侧完成）。
     * <p>
     * <b>本方法从不抛异常</b>：地址未配置、无响应、调用异常一律折算成「整批失败 + 原因」返回，
     * 因此调用方 <b>MUST</b> 逐张检查 {@link AppRegisterResult#failureReasonOf}，
     * <b>NEVER 假定「没抛异常就是成功」</b>（AGENTS.md §5.2 已记两起同型事故）。
     */
    AppRegisterResult registerToApp(List<EmployeeCardInfoDTO> batch);

    /**
     * 向 ACC 查询员工码资料（用于本地 {@code EMPLOYEE_NAME} 为空时补全）。
     *
     * @return 查到的资料；<b>地址未配置 / ACC 返回失败 / 调用异常一律返回 {@code null} 并记日志</b>，
     *         调用方 MUST 自行决定「查不到」时是降级还是报错
     */
    EmployeeCardInfoDTO queryFromAcc(String cardNo);

    /** ACC 激活接口地址是否已配置。未配置时调用方 MUST 直接返回失败，NEVER 空跑 {@link #requestActivation}。 */
    boolean isActivationUrlConfigured();

    /**
     * 向 ACC 发起激活 / 禁用请求，返回响应体原文。
     * <p>
     * <b>异常一律原样抛出、本方法不做任何归类</b>：ACC 用「HTTP 4xx + 业务错误体」表达参数被拒，
     * 而「4xx 要透传 ACC 业务码、5xx 与超时才归 9999」是业务策略，MUST 由调用方判定
     * （见 {@code EmployeeCardServiceImpl.activateEmployeeCard} 的 catch 块）。
     * <b>NEVER 在这里把 {@code HttpClientErrorException} 吞掉或折算成返回值</b>，
     * 那会让「参数被拒」和「远端不可用」在调用方眼里变成同一件事。
     */
    String requestActivation(EmployeeCardActivateReqDTO request);
}
