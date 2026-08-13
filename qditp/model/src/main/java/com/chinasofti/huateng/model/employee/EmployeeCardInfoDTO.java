package com.chinasofti.huateng.model.employee;

/**
 * ACC 员工码信息。
 *
 * <p>{@code cardNo} 为员工号，字段名称按 ACC 接口约定保留。</p>
 */
public class EmployeeCardInfoDTO {
    private String phone;
    private String cardNo;
    private Integer cardStatus;
    private String employeeName;
    private String idCardNo;
    private String company;
    private String center;
    private String department;
    private String position;
    private String photoUrl;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
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
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
