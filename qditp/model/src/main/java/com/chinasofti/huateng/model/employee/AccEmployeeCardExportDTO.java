package com.chinasofti.huateng.model.employee;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 员工卡 ACC 模拟器卡片异步导出 DTO。
 */
public class AccEmployeeCardExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String cardNo;
    private String employeeName;
    private String phone;
    private Integer cardStatus;
    private String company;
    private String center;
    private String department;
    private String position;
    private LocalDateTime updateTms;

    public String getCardNo()
    {
        return cardNo;
    }

    public void setCardNo(String cardNo)
    {
        this.cardNo = cardNo;
    }

    public String getEmployeeName()
    {
        return employeeName;
    }

    public void setEmployeeName(String employeeName)
    {
        this.employeeName = employeeName;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public Integer getCardStatus()
    {
        return cardStatus;
    }

    public void setCardStatus(Integer cardStatus)
    {
        this.cardStatus = cardStatus;
    }

    public String getCompany()
    {
        return company;
    }

    public void setCompany(String company)
    {
        this.company = company;
    }

    public String getCenter()
    {
        return center;
    }

    public void setCenter(String center)
    {
        this.center = center;
    }

    public String getDepartment()
    {
        return department;
    }

    public void setDepartment(String department)
    {
        this.department = department;
    }

    public String getPosition()
    {
        return position;
    }

    public void setPosition(String position)
    {
        this.position = position;
    }

    public LocalDateTime getUpdateTms()
    {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms)
    {
        this.updateTms = updateTms;
    }
}
