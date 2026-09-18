package com.chinasofti.huateng.accsimulator;

import java.time.LocalDateTime;

public class AccEmployeeCard {
    private String cardNo;
    private String phone;
    private Integer cardStatus;
    private String employeeName;
    private String idCardNo;
    private String company;
    private String center;
    private String department;
    private String position;
    private String photo;
    private LocalDateTime createTms;
    private LocalDateTime updateTms;

    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getCardStatus() { return cardStatus; }
    public void setCardStatus(Integer cardStatus) { this.cardStatus = cardStatus; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getIdCardNo() { return idCardNo; }
    public void setIdCardNo(String idCardNo) { this.idCardNo = idCardNo; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getCenter() { return center; }
    public void setCenter(String center) { this.center = center; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }
    public LocalDateTime getCreateTms() { return createTms; }
    public void setCreateTms(LocalDateTime createTms) { this.createTms = createTms; }
    public LocalDateTime getUpdateTms() { return updateTms; }
    public void setUpdateTms(LocalDateTime updateTms) { this.updateTms = updateTms; }
}
