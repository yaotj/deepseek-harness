package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import org.springframework.util.StringUtils;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;

/**
 * 支付签约域**应答装配的唯一实现点**（2026-09-15 拆分批次 2）。
 *
 * <p><b>由来</b>：这些方法原是 {@code PaySignWorkflow}（10 个）与
 * {@code TerminationInternalServiceImpl}（9 个）各自的私有 {@code fillError} / {@code fillSuccess}，
 * 其中 {@code BaseRespDTO} 那一对<b>两个类里逐字重复</b>。搬迁后调用点**一个字都没改** ——
 * 两个类改用 {@code import static}，方法名与参数列表原样保留，因此这次改动的等价性风险接近零。
 *
 * <p><b>19 个方法其实只有 3 种行为</b>，这是本类存在的真正理由（不是「少写几行」）：
 * <ul>
 *   <li><b>A 五字段</b>：{@code code} / {@code msg} / {@code success} + {@code retCode} / {@code retMsg}
 *       —— {@code BaseRespDTO}、{@code RequestPayResult}、{@code RequestRefundResult}；</li>
 *   <li><b>B 两字段 retCode</b>：只设 {@code retCode} / {@code retMsg}
 *       —— {@code RequestSignInfoResult}、{@code PaySignCallbackResult}、{@code UnbindAgreementResult}；</li>
 *   <li><b>C 两字段 resultCode</b>：只设 {@code resultCode} / {@code resultMsg}
 *       —— {@code CompensateNotifyRespDTO}、{@code CheckFailedOrdersRespDTO}、
 *       {@code ProcessTerminationRespDTO}。</li>
 * </ul>
 * 三种行为在本类内**仍是逐个重载各写一遍、没有抽私有 helper**（本段初稿写「各收口成一个私有
 * helper、改 3 处即可」，与代码不符，已改正）：搬迁这一版**刻意零行为改动**，抽 helper 会让
 * 「搬迁」与「重构」混在同一次提交里、等价性不再一眼可验。收益因此只是**从 2 个类收到 1 个类**
 * —— 要改「失败应答的字段口径」仍需在本类内改对应那几个重载，但**不必再跨文件找**。
 * 真要抽 helper MUST 单独一版、并先补上覆盖三种行为的断言。
 *
 * <p><b>为什么重载消不掉</b>：这 9 个 DTO 分属四个互不相干的家族，且多数在 {@code model} 模块 ——
 * 它们是能被 {@code parseBizData} 解析的**对外契约**，按 {@code docs/domain} 的判据
 * <b>NEVER 为了内部整洁给它们加共同父类或改继承</b>。因此重载数量是契约形状决定的，不是代码质量问题。
 *
 * <p><b>本类是对 AGENTS.md §5.1「NEVER 主动创建新的工具类」的有意破例</b>，与
 * {@code face-pay-server} 的 {@code F2fDuplicateKey}（ADR-D84）同一性质：19 个方法散在 2 个类、
 * 抄私有方法等于留两份逐字副本，而它们没有任何状态与依赖，做成 Bean 只是徒增装配。
 * <b>NEVER 往本类加业务判断</b>：它只把「已经定好的错误码与文案」写进应答对象，
 * 该返哪个码由调用方决定。
 *
 * <p><b>NEVER 改 A 类的 {@code code} 取值</b>（失败 {@code -1} / 成功 {@code 0}）：那是支付中心侧
 * 与旧客户端在读的字段，与 ITP 自己的 {@code retCode} 并存是历史契约，不是冗余。
 */
public final class PaySignResponses {

    private PaySignResponses() {
    }

    public static void fillError(BaseRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(BaseRespDTO response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(RequestPayResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(RequestPayResult response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(RequestRefundResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(RequestRefundResult response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    /** B 类：{@code requestSignInfo} 的应答模型只有 retCode / retMsg。 */
    public static void fillError(RequestSignInfoResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(RequestSignInfoResult response) {
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(PaySignCallbackResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(PaySignCallbackResult response) {
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    /** B 类：IF8A-75 直接解绑只有失败装配，成功分支由调用方按业务字段自行填。 */
    public static void fillError(UnbindAgreementResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    /** C 类：内部批处理应答用 resultCode / resultMsg，<b>与 retCode 不是同一个字段</b>。 */
    public static void fillError(CompensateNotifyRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setResultCode(errorCode.getCode());
        response.setResultMsg(msg);
    }

    public static void fillSuccess(CompensateNotifyRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(CheckFailedOrdersRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setResultCode(errorCode.getCode());
        response.setResultMsg(msg);
    }

    public static void fillSuccess(CheckFailedOrdersRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(ProcessTerminationRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setResultCode(errorCode.getCode());
        response.setResultMsg(msg);
    }

    public static void fillSuccess(ProcessTerminationRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }
    /**
     * IF8B 签约通知重发（{@code /internal/paySign/resendSignNotify}）。
     *
     * <p>与本类其余重载不同，这两个**返回入参本身**以便调用点写成 {@code return fillXxx(resp)}
     * —— 那是从 {@code AppNotifyServiceImpl} 搬来时的原样签名（2026-09-16，ADR-D98），
     * <b>NEVER 为了「风格统一」改成 void</b>，那会连带改 11 个调用点。</p>
     */
    public static ResendSignNotifyRespDTO fillSuccess(ResendSignNotifyRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
        return response;
    }

    /** 同上；{@code notified} 恒置 false —— 失败分支 NEVER 报「已通知」。 */
    public static ResendSignNotifyRespDTO fillError(ResendSignNotifyRespDTO response,
                                                    PaySignErrorCodeEnum error, String msg) {
        response.setResultCode(error.getCode());
        response.setResultMsg(StringUtils.hasText(msg) ? msg : error.getMsg());
        response.setNotified(false);
        return response;
    }
    /**
     * IF8A-22 签约结果查询的成功应答：一次把 {@code 0000} 与 4 个签约字段填齐。
     *
     * <p>由 {@code ContractDomainServiceImpl.requestContractResult} 里**逐字重复两遍**的 5 行
     * 收口而来（2026-09-16，ADR-D101）：一处是「本地已签约直接返回」、一处是「查完支付平台再返回」。
     * 两处曾完全相同，<b>NEVER 再让它们分叉</b> —— 分叉的表现是「同一个用户，走缓存分支和走网关分支
     * 拿到的字段不一样」，而两条分支都返 {@code 0000}，从应答码上完全看不出来。
     *
     * <p>{@code defaultStatus} <b>由调用方传入、NEVER 在本类写死</b>：{@code NOT_SIGNED} 是
     * 签约状态机的取值，归 {@code ContractDomainServiceImpl} 的常量管，
     * 在这里再抄一份就成了「改状态值要改两个文件」。
     */
    public static void fillContractResult(RequestContractResultRespDTO response, PaySignInfo signInfo,
                                          String defaultStatus) {
        fillSuccess(response);
        response.setStatus(PaySignValues.defaultString(signInfo.getContractStatus(), defaultStatus));
        response.setPayUserId(signInfo.getPayAccountId());
        response.setPayAccountId(signInfo.getPayAccountId());
        response.setPayAgreementNo(signInfo.getPayAgreementNo());
    }
}
