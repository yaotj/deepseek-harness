package com.chinasofti.huateng.acc.es.server.service.impl;

import com.chinasofti.huateng.acc.es.server.mapper.TblStlTicketInfoMapper;
import com.chinasofti.huateng.acc.es.server.model.TblStlTicketInfo;
import com.chinasofti.huateng.acc.es.server.service.ITicketInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TicketInfoServiceImpl implements ITicketInfoService {

    private TblStlTicketInfoMapper ticketInfoMapper;


    @Override
    public int insert(TblStlTicketInfo tblStlTicketInfo) {
        return ticketInfoMapper.insert(tblStlTicketInfo);
    }
}
