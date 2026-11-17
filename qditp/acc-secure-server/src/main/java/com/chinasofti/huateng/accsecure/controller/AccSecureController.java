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


@RestController
@RequestMapping("/ci/acc/secure")
public class AccSecureController {
    private static final Logger log = LoggerFactory.getLogger(AccSecureController.class);
    private final AccSecureService accSecureService;

    public AccSecureController(AccSecureService accSecureService) {
        this.accSecureService = accSecureService;
    }


    @PostMapping("/requestQrLogicNumList")
    public RequestQrLogicNumListRespDTO requestQrLogicNumList(@RequestBody RequestQrLogicNumListReqDTO request) {
        log.info("接收到请求逻辑卡号报文: {}", request);
        return accSecureService.requestQrLogicNumList(request);
    }


    @PostMapping("/requestCaKey")
    public RequestCaKeyRespDTO requestCaKey(@RequestBody RequestCaKeyReqDTO request) {
        log.info("接收到请求生成地铁CA密钥报文: {}", request);
        return accSecureService.requestCaKey(request);
    }


    @PostMapping("/requestUserSm2Key")
    public RequestUserSm2KeyRespDTO requestUserSm2Key(@RequestBody RequestUserSm2KeyReqDTO request) {
        log.info("接收到请求生成用户SM2密钥报文: {}", request);
        return accSecureService.requestUserSm2Key(request);
    }


    @PostMapping("/requestSignPubkey")
    public RequestSignPubkeyRespDTO requestSignPubkey(@RequestBody RequestSignPubkeyReqDTO request) {
        log.info("接收到请求签名用户公钥报文: {}", request);
        return accSecureService.requestSignPubkey(request);
    }


    @PostMapping("/requestExportUserPriKey")
    public RequestExportUserPriKeyRespDTO requestExportUserPriKey(@RequestBody RequestExportUserPriKeyReqDTO request) {
        log.info("接收到请求导出用户私钥报文: {}", request);
        return accSecureService.requestExportUserPriKey(request);
    }


    @PostMapping("/requestSignInsData")
    public RequestSignInsDataRespDTO requestSignInsData(@RequestBody RequestSignInsDataReqDTO request) {
        log.info("接收到请求签名行业数据报文: {}", request);
        return accSecureService.requestSignInsData(request);
    }


    @PostMapping("/requestDpk")
    public RequestDpkRespDTO requestDpk(@RequestBody RequestDpkReqDTO request) {
        log.info("接收到请求HCE卡片消费密钥报文: {}", request);
        return accSecureService.requestDpk(request);
    }


    @PostMapping("/requestHecCardDate")
    public RequestHecCardDateRespDTO requestHecCardDate(@RequestBody RequestHecCardDateReqDTO request) {
        log.info("接收到请求发售HCE单程票报文: {}", request);
        return accSecureService.requestHecCardDate(request);
    }
}
