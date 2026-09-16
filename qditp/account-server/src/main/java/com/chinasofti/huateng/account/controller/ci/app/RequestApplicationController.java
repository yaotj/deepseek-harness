package com.chinasofti.huateng.account.controller.ci.app;

import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.service.AccountCancelService;
import com.chinasofti.huateng.account.service.AccountProfileService;
import com.chinasofti.huateng.account.service.PhoneChangeService;
import com.chinasofti.huateng.account.service.AccountRegistrationService;
import com.chinasofti.huateng.account.service.PayChannelService;
import com.chinasofti.huateng.account.service.AccountRequestVerifier;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;
import com.chinasofti.huateng.common.response.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestApplicationController {
    private static final Logger log = LoggerFactory.getLogger(RequestApplicationController.class);

    /** IF8A-42 销户。第六轮拆分后直连实现方，原 AccountApplicationService 已删除。 */
    private final AccountCancelService accountCancelService;

    /**
     * 账户资料查询。ADR-D35 已把纯内部调用的 {@code queryCardTypeByCardId} 与
     * {@code updateHceData} 切到 {@code controller/internal/CardDataInternalController}，
     * 本类只留 {@code queryUserInfo} —— 它<b>内外都有调用方</b>（fep-app 的 IndustryData
     * 与 ticket / gate-txn-pay / pay-sign 三个内部模块），暂按外部面处理，见 ADR-D35。
     */
    private final AccountProfileService accountProfileService;

    /** 换号（IF8A-76）。直连实现方，NEVER 再加一层转发。 */
    private final PhoneChangeService phoneChangeService;

    /**
     * 支付通道的 <b>APP 契约面</b>五个入口，2026-09-11 第四轮拆分从 accountApplicationService 搬出。
     *
     * <p>ADR-D34 已把「调用方是 pay-sign-server」的两个入口切到
     * {@code PayChannelInternalService} + {@code controller/internal/PayChannelInternalController}，
     * <b>NEVER 把它们迁回本类</b>。</p>
     */
    private final PayChannelService payChannelService;

    /** 开户发号，2026-09-11 第五轮拆分从 accountApplicationService 搬出。 */
    private final AccountRegistrationService accountRegistrationService;

    /**
     * 入向验签器。<b>⚠️ 当前是悬空依赖：本类没有任何方法调它</b>
     * （全模块 grep 只命中这一处声明与构造器赋值），因此账户域 24 个端点全部裸暴露。
     *
     * <p>ADR-D37 改构造器注入后<b>刻意保留它</b>：一个「只在构造器签名里出现、方法体零引用」
     * 的参数比原先的 {@code @Autowired} 字段更显眼，正好当成待补验签的提醒。
     * <b>NEVER 因为「没人用」就删掉</b>——删掉等于把这个缺口从代码里抹去。补验签见 ADR-D35。</p>
     */
    private final AccountRequestVerifier accountRequestVerifier;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public RequestApplicationController(AccountCancelService accountCancelService,
                                        AccountProfileService accountProfileService,
                                        PhoneChangeService phoneChangeService,
                                        PayChannelService payChannelService,
                                        AccountRegistrationService accountRegistrationService,
                                        AccountRequestVerifier accountRequestVerifier) {
        this.accountCancelService = accountCancelService;
        this.accountProfileService = accountProfileService;
        this.phoneChangeService = phoneChangeService;
        this.payChannelService = payChannelService;
        this.accountRegistrationService = accountRegistrationService;
        this.accountRequestVerifier = accountRequestVerifier;
    }

    @PostMapping("/requestApplication")
    public RequestApplicationResult requestApplication(@RequestBody RequestApplicationReqDTO request) {
        log.info("接收到请求开户接口报文: {}", request);
        return accountRegistrationService.requestApplication(request);
    }

    @PostMapping("/requestAddPayChannel")
    public RequestAddPayChannelResult requestAddPayChannel(@RequestBody RequestAddPayChannelReqDTO request) {
        log.info("接收到请求添加支付通道接口报文: {}", request);
        return payChannelService.requestAddPayChannel(request);
    }

    @PostMapping("/requestSetDefaultPayChannel")
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@RequestBody RequestSetDefaultPayChannelReqDTO request) {
        log.info("接收到请求设置默认支付通道接口报文: {}", request);
        return payChannelService.requestSetDefaultPayChannel(request);
    }

    @PostMapping("/requestRemovePayChannel")
    public RequestRemovePayChannelResult requestRemovePayChannel(@RequestBody RequestRemovePayChannelReqDTO request) {
        log.info("接收到删除支付通道接口报文: {}", request);
        return payChannelService.requestRemovePayChannel(request);
    }

    /**
     * 钱包协议约定的解绑入口。钱包没有支付平台签约协议，复用本地支付通道删除事务。
     */
    @PostMapping("/requestAgreeRelease")
    public RequestRemovePayChannelResult requestAgreeRelease(@RequestBody RequestRemovePayChannelReqDTO request) {
        log.info("接收到钱包解绑接口报文: {}", request);
        return payChannelService.requestAgreeRelease(request);
    }

    /**
     * IF8A-42 用户销户。只注销开户记录，支付渠道由随后的 IF8A-75 解绑。
     *
     * <p>内部路径保持 {@code /userCancel}（`AccountClient.userCancel` 按此调用），
     * 对外规范名 {@code /app/cancelAccount} 只在 fep-app-server 落地，属实现细节差异，
     * 与 if8a_76 的 {@code newMsisdn} 同一处理方式。</p>
     */
    @PostMapping("/userCancel")
    public UserCancelResult userCancel(@RequestBody UserCancelReqDTO request) {
        log.info("接收到IF8A-42用户销户接口报文: {}", request);
        return accountCancelService.userCancel(request);
    }

    @PostMapping("/queryUserInfo")
    public QueryUserInfoResult queryUserInfo(@RequestBody QueryUserInfoReqDTO request) {
        log.info("接收到查询用户信息接口报文: {}", request);
        return accountProfileService.queryUserInfo(request);
    }

    @PostMapping("/requestUpdateChannelDefaultContract")
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(@RequestBody RequestUpdateChannelDefaultContractReqDTO request) {
        log.info("接收到更换第三方渠道码默认支付方式接口报文: {}", request);
        return payChannelService.requestUpdateChannelDefaultContract(request);
    }

    @GetMapping("/updatePhone")
    public CommonResult updatePhone(@RequestParam String thirdUserId, @RequestParam String newMsisdn) {
        log.info("接收到更换手机号请求: thirdUserId={}, newMsisdn={}", thirdUserId, newMsisdn);
        boolean result = phoneChangeService.updatePhone(thirdUserId, newMsisdn);
        CommonResult response = new CommonResult();
        if (result) {
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("更换手机号成功");
        } else {
            // MUST 用 FAIL(9999)、NEVER 用 SYSTEM_ERROR(9001)：本接口对 APP 一直返 9999，
            // 换成 9001 就是改对外契约。AccountErrorCodeEnum 类注释已写明本枚举里两套失败语义并存、
            // NEVER 为了看起来整齐而对齐取值 —— 这条约束同样适用于调用点。
            response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("更换手机号失败");
        }
        return response;
    }


}
