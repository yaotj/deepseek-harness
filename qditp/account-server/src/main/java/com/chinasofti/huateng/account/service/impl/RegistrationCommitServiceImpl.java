package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.service.RegistrationCommitService;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


@Service
public class RegistrationCommitServiceImpl implements RegistrationCommitService {

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserItpRegLogMapper userItpRegLogMapper;

    private final TicketClient ticketClient;


    private final TransactionTemplate transactionTemplate;


    public RegistrationCommitServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper,
                                        UserItpRegLogMapper userItpRegLogMapper,
                                        TicketClient ticketClient,
                                        TransactionTemplate transactionTemplate) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userItpRegLogMapper = userItpRegLogMapper;
        this.ticketClient = ticketClient;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public RegisterRideStatusRespDTO registerRideStatus(UserItpRegInfo regInfo) {
        return ticketClient.registerRideStatus(buildTicketRequest(regInfo));
    }

    @Override
    public void persistRegistration(UserItpRegInfo regInfo) {
        transactionTemplate.executeWithoutResult(status -> {
            userItpRegInfoMapper.insert(regInfo);
            userItpRegLogMapper.insert(buildRegLog(regInfo));
        });
    }


    private RegisterRideStatusReqDTO buildTicketRequest(UserItpRegInfo regInfo) {
        RegisterRideStatusReqDTO request = new RegisterRideStatusReqDTO();
        request.setThirdUserId(regInfo.getThirdUserId());
        request.setCardId(regInfo.getCardId());
        request.setCardType(regInfo.getCardType());
        request.setMsisdn(regInfo.getMsisdn());
        request.setCardIssueCode(regInfo.getCardIssueCode());
        request.setChannel(regInfo.getChannel());
        request.setThirdPayId(regInfo.getThirdPayId());
        return request;
    }


    private UserItpRegLog buildRegLog(UserItpRegInfo regInfo) {
        UserItpRegLog regLog = new UserItpRegLog();
        regLog.setCardId(regInfo.getCardId());
        regLog.setCardType(regInfo.getCardType());
        regLog.setThirdUserId(regInfo.getThirdUserId());
        regLog.setMsisdn(regInfo.getMsisdn());
        regLog.setOperDateTime(LocalDateTime.now());
        regLog.setOperType(0);
        return regLog;
    }

    @Override
    public String normalizeIssueOrgCode(String cardIssueCode) {
        return cardIssueCode == null ? null : cardIssueCode.trim();
    }
}
