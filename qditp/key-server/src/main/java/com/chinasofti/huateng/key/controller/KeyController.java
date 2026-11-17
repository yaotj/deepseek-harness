package com.chinasofti.huateng.key.controller;

import com.chinasofti.huateng.key.service.KeySyncService;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListReqDTO;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class KeyController {
    private static final Logger log = LoggerFactory.getLogger(KeyController.class);

    private final KeySyncService keySyncService;

    public KeyController(KeySyncService keySyncService) {
        this.keySyncService = keySyncService;
    }


    @PostMapping("/requestKeyList")
    public RequestKeyListResult requestKeyList(@RequestBody RequestKeyListReqDTO request) {
        log.info("接收到IF8A-02请求同步密钥, thirdUserId={}, cardId={}, cardType={}",
                request == null ? null : request.getThirdUserId(),
                request == null ? null : request.getCardId(),
                request == null ? null : request.getCardType());
        return keySyncService.requestKeyList(request);
    }

    @PostMapping("/requestAgmSynKeyList")
    public RequestAgmSynKeyListResult requestAgmSynKeyList(@RequestBody RequestAgmSynKeyListReqDTO request) {
        log.info("Receive IF1A-02 AGM key synchronization request, deviceId={}, keyVersionCount={}",
                request == null ? null : request.getDeviceId(),
                request == null || request.getKeyCurVerList() == null ? 0 : request.getKeyCurVerList().size());
        return keySyncService.requestAgmSynKeyList(request);
    }
}
