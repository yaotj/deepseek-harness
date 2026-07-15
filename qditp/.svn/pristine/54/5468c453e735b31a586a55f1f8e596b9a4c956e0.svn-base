package com.chinasofti.huateng.accsecure.controller;

import com.chinasofti.huateng.accsecure.model.request.RequestCaKeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestDpkReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestExportUserPriKeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestHecCardDateReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestQrLogicNumListReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestSignInsDataReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestSignPubkeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestUserSm2KeyReqDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestCaKeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestDpkRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestExportUserPriKeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestHecCardDateRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestQrLogicNumListRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestSignInsDataRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestSignPubkeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestUserSm2KeyRespDTO;
import com.chinasofti.huateng.accsecure.service.AccSecureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC 安全接口代理服务。
 */
@RestController
@RequestMapping("/ci/acc/secure")
public class AccSecureController {
    private static final Logger log = LoggerFactory.getLogger(AccSecureController.class);
    private final AccSecureService accSecureService;

    public AccSecureController(AccSecureService accSecureService) {
        this.accSecureService = accSecureService;
    }

    /**
     * IF7B-01 请求逻辑卡号。
     */
    @PostMapping("/requestQrLogicNumList")
    public RequestQrLogicNumListRespDTO requestQrLogicNumList(@RequestBody RequestQrLogicNumListReqDTO request) {
        log.info("接收到请求逻辑卡号报文: {}", request);
        return accSecureService.requestQrLogicNumList(request);
    }

    /**
     * IF7B-02 请求生成地铁CA密钥。
     */
    @PostMapping("/requestCaKey")
    public RequestCaKeyRespDTO requestCaKey(@RequestBody RequestCaKeyReqDTO request) {
        log.info("接收到请求生成地铁CA密钥报文: {}", request);
        return accSecureService.requestCaKey(request);
    }

    /**
     * IF7B-03 请求生成用户SM2密钥。
     */
    @PostMapping("/requestUserSm2Key")
    public RequestUserSm2KeyRespDTO requestUserSm2Key(@RequestBody RequestUserSm2KeyReqDTO request) {
        log.info("接收到请求生成用户SM2密钥报文: {}", request);
        return accSecureService.requestUserSm2Key(request);
    }

    /**
     * IF7B-04 请求签名用户公钥。
     */
    @PostMapping("/requestSignPubkey")
    public RequestSignPubkeyRespDTO requestSignPubkey(@RequestBody RequestSignPubkeyReqDTO request) {
        log.info("接收到请求签名用户公钥报文: {}", request);
        return accSecureService.requestSignPubkey(request);
    }

    /**
     * IF7B-05 请求导出用户私钥。
     */
    @PostMapping("/requestExportUserPriKey")
    public RequestExportUserPriKeyRespDTO requestExportUserPriKey(@RequestBody RequestExportUserPriKeyReqDTO request) {
        log.info("接收到请求导出用户私钥报文: {}", request);
        return accSecureService.requestExportUserPriKey(request);
    }

    /**
     * IF7B-06 请求签名行业数据。
     */
    @PostMapping("/requestSignInsData")
    public RequestSignInsDataRespDTO requestSignInsData(@RequestBody RequestSignInsDataReqDTO request) {
        log.info("接收到请求签名行业数据报文: {}", request);
        return accSecureService.requestSignInsData(request);
    }

    /**
     * IF7B-07 请求HCE卡片消费密钥。
     */
    @PostMapping("/requestDpk")
    public RequestDpkRespDTO requestDpk(@RequestBody RequestDpkReqDTO request) {
        log.info("接收到请求HCE卡片消费密钥报文: {}", request);
        return accSecureService.requestDpk(request);
    }

    /**
     * IF7B-08 请求发售HCE单程票。
     */
    @PostMapping("/requestHecCardDate")
    public RequestHecCardDateRespDTO requestHecCardDate(@RequestBody RequestHecCardDateReqDTO request) {
        log.info("接收到请求发售HCE单程票报文: {}", request);
        return accSecureService.requestHecCardDate(request);
    }
}
