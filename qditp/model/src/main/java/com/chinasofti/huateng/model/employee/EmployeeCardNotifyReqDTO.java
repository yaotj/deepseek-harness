package com.chinasofti.huateng.model.employee;

import java.util.List;

/**
 * ACC 员工码状态通知请求。
 */
public class EmployeeCardNotifyReqDTO {
    private List<EmployeeCardInfoDTO> cardList;

    public List<EmployeeCardInfoDTO> getCardList() { return cardList; }
    public void setCardList(List<EmployeeCardInfoDTO> cardList) { this.cardList = cardList; }
}
