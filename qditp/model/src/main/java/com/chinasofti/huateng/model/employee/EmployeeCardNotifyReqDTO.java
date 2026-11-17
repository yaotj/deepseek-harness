package com.chinasofti.huateng.model.employee;

import java.util.List;


public class EmployeeCardNotifyReqDTO {
    private List<EmployeeCardInfoDTO> cardList;

    public List<EmployeeCardInfoDTO> getCardList() { return cardList; }
    public void setCardList(List<EmployeeCardInfoDTO> cardList) { this.cardList = cardList; }
}
