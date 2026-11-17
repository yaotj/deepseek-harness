package com.chinasofti.huateng.recon.thirdparty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;


@RestController
@RequestMapping("/internal/recon/thirdparty")
public class ThirdPartyReconController {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyReconController.class);

    private final ThirdPartyReconScheduler scheduler;

    public ThirdPartyReconController(ThirdPartyReconScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestParam(value = "billDate", required = false) String billDate) {
        try {
            if (billDate != null && !billDate.isBlank()) {
                String fileName = scheduler.runForDate(billDate);
                return ResponseEntity.ok().body("{\"fileName\":\"" + fileName + "\"}");
            }
            List<String> files = scheduler.runMatchedFiles();
            return ResponseEntity.ok().body("{\"files\":" + files + "}");
        } catch (IOException ex) {
            log.error("第三方对账触发失败", ex);
            return ResponseEntity.status(500).body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
