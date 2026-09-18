package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import org.springframework.stereotype.Service;

/**
 * 回调领域服务：只做分派（2026-09-17 拆分，ADR-D120）。
 *
 * <p>拆分前本类有 <b>8 个协作者</b>，两条回调链路（签约 IPD02 / 解约 IPD03）挤在一起：
 * 签约簇用 4 个、解约簇用 7 个、交集 3 个。按 ADR-D95 这两个簇并非严格不相交，
 * 拆分的代价（3 个协作者重复注入 + 1 个共享 helper 外提）由人裁决后接受。
 *
 * <p><b>NEVER 在本类写任何业务逻辑</b>：它存在的唯一理由是保住
 * {@code CallbackDomainService} 这个对外接口不变（调用方是 {@code PaySignServiceImpl}
 * 与 {@code TerminationProcessor}）。要改回调行为 MUST 改对应的 handler。
 */
@Service
public class CallbackDomainServiceImpl implements CallbackDomainService {

    private final SignResultCallbackHandler signResultCallbackHandler;
    private final TerminationResultCallbackHandler terminationResultCallbackHandler;

    public CallbackDomainServiceImpl(SignResultCallbackHandler signResultCallbackHandler,
                                     TerminationResultCallbackHandler terminationResultCallbackHandler) {
        this.signResultCallbackHandler = signResultCallbackHandler;
        this.terminationResultCallbackHandler = terminationResultCallbackHandler;
    }

    @Override
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        return signResultCallbackHandler.receiveSignResult(request, signChannel);
    }

    @Override
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        return terminationResultCallbackHandler.receiveTerminationResult(request, signChannel);
    }
}
