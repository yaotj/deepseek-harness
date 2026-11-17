package com.chinasofti.huateng.accsimulator.service;

import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import com.chinasofti.huateng.accsimulator.entity.AccSimulationHistory;
import com.chinasofti.huateng.accsimulator.model.AccEmployeeCardSaveRequest;
import com.chinasofti.huateng.accsimulator.model.AccNotifySimulationRequest;
import com.chinasofti.huateng.accsimulator.model.AccUpdateSimulationRequest;
import com.chinasofti.huateng.accsimulator.model.SimulationExchange;
import com.github.pagehelper.PageInfo;


public interface AccSimulatorService {


    SimulationExchange sendNotify(AccNotifySimulationRequest request);


    SimulationExchange sendUpdateNotify(AccUpdateSimulationRequest request);


    PageInfo<AccSimulationHistory> history(int pageNum, int pageSize, String operation);


    void clearHistory();


    PageInfo<AccEmployeeCard> cards(int pageNum, int pageSize, String cardNo, String employeeName, Integer cardStatus);


    void saveCard(AccEmployeeCardSaveRequest request);


    void clearCards();


    AccEmployeeCard findCard(String cardNo);
}
