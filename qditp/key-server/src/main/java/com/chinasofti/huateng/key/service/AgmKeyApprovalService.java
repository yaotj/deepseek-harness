package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.common.response.ResultVO;


public interface AgmKeyApprovalService {


    ResultVO<Void> approve(String providerId, Long batchNumber, String targetStatus, String operator);
}
