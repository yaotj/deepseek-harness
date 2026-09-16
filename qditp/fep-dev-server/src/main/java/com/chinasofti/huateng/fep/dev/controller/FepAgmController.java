package com.chinasofti.huateng.fep.dev.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.model.DeviceHeartbeatRespDTO;
import com.chinasofti.huateng.fep.dev.model.NotifyVerifyResultAckDTO;
import com.chinasofti.huateng.fep.dev.model.NotifyVerifyResultDeviceReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListRespDTO;
import com.chinasofti.huateng.fep.dev.gate.GateTransactionHandler;
import com.chinasofti.huateng.fep.dev.keysync.KeySyncHandler;
import com.chinasofti.huateng.fep.dev.qrcode.QrCodeStatusHandler;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * AGM 设备接口前置入口。
 *
 * <p>2026-09-14 重构：原先中间隔着一层零行为的 {@code DevService} / {@code DevServiceImpl}
 * （三个方法各一行 delegate，且把密钥同步 / 票卡状态 / 闸机交易三件无关的事绑在同一个接口上），
 * 已删除，本类直接依赖三个各自单一职责的 Handler。<b>NEVER 再加回这层壳</b>——
 * 它不承载任何逻辑，只让「改一件事要动两个文件」。</p>
 */
@RestController
@RequestMapping("/ci/agm")
public class FepAgmController {
    private static final Logger log = LoggerFactory.getLogger(FepAgmController.class);

    private final GateTransactionHandler gateTransactionHandler;
    private final KeySyncHandler keySyncHandler;
    private final QrCodeStatusHandler qrCodeStatusHandler;

    public FepAgmController(GateTransactionHandler gateTransactionHandler,
                            KeySyncHandler keySyncHandler,
                            QrCodeStatusHandler qrCodeStatusHandler) {
        this.gateTransactionHandler = gateTransactionHandler;
        this.keySyncHandler = keySyncHandler;
        this.qrCodeStatusHandler = qrCodeStatusHandler;
    }
    /**
     * IF1A-01 闸机检票通知。
     *
     * <p>返回类型 MUST 是 {@link NotifyVerifyResultAckDTO}（只有 retCode + retMsg），
     * NEVER 直接把 {@link NotifyVerifyResultRespDTO} 返回给闸机——原因见该 Ack 类的注释：
     * 闸机侧老项目对未知字段反序列化报错，多一个字段就会触发 3 次重发（2026-08-27 生产实测）。</p>
     *
     * <p>2026-09-14：原先本方法里那段 {@code handleResultCode != "000"} 就短路返成功的判定
     * <b>已下沉到 {@code GateTransactionHandler.notifyVerifyResult}</b> —— 那是「读写器失败就不通知票务」
     * 的业务决策，不属于本层的参数校验（§3.3）。本方法现在只做：非空 → 解析 → 回填 deviceId → 转发 → 收敛成 Ack。
     * <b>NEVER 把任何按报文字段取值分支的逻辑加回本类。</b></p>
     */
    @PostMapping("/notiVerifyResult")
    public NotifyVerifyResultAckDTO notifyVerifyResult(@ModelAttribute ItpCommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-01 闸机检票通知, deviceId={}", deviceId);
        if (!hasBizData(request)) {
            return invalidParam(NotifyVerifyResultAckDTO::new, "bizData不能为空");
        }

        NotifyVerifyResultDeviceReqDTO bizData =
                parseBizData(request, NotifyVerifyResultDeviceReqDTO.class, "IF1A-01 闸机检票通知");

        if (bizData == null) {
            return invalidParam(NotifyVerifyResultAckDTO::new, "bizData格式错误");
        }

        bizData.setDeviceId(deviceId);
        log.info("IF1A-01 闸机检票通知, deviceId={}, bizData={}", deviceId, request.getBizData());

        NotifyVerifyResultRespDTO response = gateTransactionHandler.notifyVerifyResult(bizData);
        log.info("IF1A-01 闸机检票通知, deviceId={}, 响应 retCode={}", deviceId, response.getRetCode());
        return notifyAck(response.getRetCode(), response.getRetMsg());
    }

    /**
     * IF1A-02 密钥同步。
     */
    @PostMapping("/requestSynKeyList")
    public RequestSynKeyListRespDTO requestSynKeyList(@ModelAttribute ItpCommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-02 密钥同步, deviceId={}", deviceId);
        if (!hasBizData(request)) {
            return invalidParam(RequestSynKeyListRespDTO::new, "bizData不能为空");
        }

        RequestSynKeyListReqDTO bizData = parseBizData(request, RequestSynKeyListReqDTO.class, "IF1A-02 密钥同步");
        if (bizData == null) {
            return invalidParam(RequestSynKeyListRespDTO::new, "bizData格式错误");
        }

        log.info("IF1A-02 密钥同步, deviceId={}, keyCount={}", deviceId,
                bizData.getKeyCurVerList() == null ? 0 : bizData.getKeyCurVerList().size());
        RequestSynKeyListRespDTO response = keySyncHandler.requestSynKeyList(bizData, deviceId, request.getBizData());
        log.info("IF1A-02 密钥同步完成, deviceId={}, retCode={}, keyVersionCount={}",
                deviceId, response.getRetCode(),
                response.getKeyCurVerList() == null ? 0 : response.getKeyCurVerList().size());
        return response;
    }
    /**
     * IF1A-04 查询票卡状态。
     */
    @PostMapping("/requestQrCodeStatus")
    public RequestQrCodeStatusRespDTO requestQrCodeStatus(@ModelAttribute ItpCommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-04 查询票卡状态, deviceId={}", deviceId);
        if (!hasBizData(request)) {
            return invalidParam(RequestQrCodeStatusRespDTO::new, "bizData不能为空");
        }

        RequestQrCodeStatusReqDTO bizData = parseBizData(request, RequestQrCodeStatusReqDTO.class, "IF1A-04 查询票卡状态");
        if (bizData == null) {
            return invalidParam(RequestQrCodeStatusRespDTO::new, "bizData格式错误");
        }

        log.info("IF1A-04 查询票卡状态, deviceId={}, bizData={}", deviceId, request.getBizData());
        RequestQrCodeStatusRespDTO response = qrCodeStatusHandler.requestQrCodeStatus(bizData);
        log.info("IF1A-04 查询票卡状态, 响应 retCode={}", response.getRetCode());
        return response;
    }

    /**
     * IF1A-03 设备心跳。
     *
     * <p>心跳接口只确认设备链路可达，不解析 bizData，不调用后端业务服务。</p>
     */
    @PostMapping({"/deviceHeartbeat", "/notiDeviceHeard"})
    public DeviceHeartbeatRespDTO deviceHeartbeat(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF1A-03 设备心跳, deviceId={}", request == null ? null : request.getDeviceId());
        DeviceHeartbeatRespDTO response = new DeviceHeartbeatRespDTO();
        response.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(FepDevErrorCodeEnum.SUCCESS.getMessage());
        return response;
    }

    private boolean hasBizData(ItpCommonFormRequest request) {
        return request != null && StringUtils.hasText(request.getBizData());
    }

    /**
     * 解析设备上送的 bizData。IF1A-01 / IF1A-02 / IF1A-04 三个端点原先各写一遍同形的
     * try / catch，收口到这里；失败返回 null（日志与原实现同格式），
     * 由各端点按自己的响应类型构造 INVALID_PARAM。
     *
     * <p>第四个端点（deviceHeartbeat / notiDeviceHeard）不解析 bizData，不走本方法。</p>
     *
     * <p>放在本类作为私有方法而不是新建工具类：AGENTS §5.1 明确 NEVER 主动新建工具类，
     * 且这段解析只服务本 Controller。</p>
     */
    private <T> T parseBizData(ItpCommonFormRequest request, Class<T> clazz, String apiTag) {
        try {
            return JSON.parseObject(request.getBizData(), clazz);
        } catch (Exception e) {
            log.error("{}, bizData解析失败, deviceId={}, bizData={}",
                    apiTag, request.getDeviceId(), request.getBizData(), e);
            return null;
        }
    }

    /**
     * 构造 INVALID_PARAM 响应。三个解析 bizData 的端点原先各有一个同形的私有方法
     * （{@code invalidQrCodeStatusParam} / {@code invalidKeyListParam} / 借用 {@code notifyAck}），
     * 差别只在 new 哪个响应类，收口到这里。
     *
     * <p>前提是四个响应类都 {@code extends CommonResult}——为此
     * {@link RequestSynKeyListRespDTO} 于 2026-09-14 由「自带同名 retCode / retMsg」改为继承，
     * 序列化后的 JSON 字段集合未变（见该类注释与 {@code RequestSynKeyListRespDtoJsonShapeTest}）。
     * <b>NEVER 让新的响应类脱离 {@code CommonResult}</b>，否则本方法又要退回一类一份。</p>
     *
     * <p>成功分支不走本方法：{@code retCode} 由下游返回、不是固定值，仍用 {@link #notifyAck}。</p>
     */
    private <T extends CommonResult> T invalidParam(Supplier<T> factory, String retMsg) {
        T response = factory.get();
        response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(retMsg);
        return response;
    }

    private NotifyVerifyResultAckDTO notifyAck(String retCode, String retMsg) {
        NotifyVerifyResultAckDTO response = new NotifyVerifyResultAckDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
