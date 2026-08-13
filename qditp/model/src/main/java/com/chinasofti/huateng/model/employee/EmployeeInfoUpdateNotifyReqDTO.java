package com.chinasofti.huateng.model.employee;

/**
 * ACC 员工信息变更通知。
 */
public class EmployeeInfoUpdateNotifyReqDTO {
    private String company;
    private String center;
    private String department;
    private String position;
    private String cardNo;
    private String photo;

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getCenter() { return center; }
    public void setCenter(String center) { this.center = center; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }
}
