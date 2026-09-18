package com.chinasofti.huateng.accsimulator.entity;

import java.time.LocalDateTime;

/**
 * 模拟 ACC 员工卡实体，对应表 ACC_SIM_EMPLOYEE_CARD。
 *
 * <p>以员工号（cardNo）为业务主键，存储模拟 ACC 侧的员工卡信息，
 * 供管理端维护与模拟 ACC 接口查询使用。</p>
 */
public class AccEmployeeCard {

    /**
     * 员工号/实体卡号，业务主键。
     */
    private String cardNo;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 电子卡状态：1 启用、2 禁用、3 未启用、4 注销。
     */
    private Integer cardStatus;

    /**
     * 员工姓名。
     */
    private String employeeName;

    /**
     * 身份证号。
     */
    private String idCardNo;

    /**
     * 所属公司。
     */
    private String company;

    /**
     * 所属中心。
     */
    private String center;

    /**
     * 所属部门。
     */
    private String department;

    /**
     * 岗位。
     */
    private String position;

    /**
     * 员工照片，Base64 编码。
     */
    private String photo;

    /**
     * 创建时间。
     */
    private LocalDateTime createTms;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTms;

    /**
     * 读取员工号/实体卡号。
     *
     * @return 员工号/实体卡号，业务主键
     */
    public String getCardNo() { return cardNo; }

    /**
     * 设置员工号/实体卡号。
     *
     * @param cardNo 员工号/实体卡号
     */
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }

    /**
     * 读取手机号。
     *
     * @return 手机号
     */
    public String getPhone() { return phone; }

    /**
     * 设置手机号。
     *
     * @param phone 手机号
     */
    public void setPhone(String phone) { this.phone = phone; }

    /**
     * 读取电子卡状态。
     *
     * @return 电子卡状态：1 启用、2 禁用、3 未启用、4 注销
     */
    public Integer getCardStatus() { return cardStatus; }

    /**
     * 设置电子卡状态。
     *
     * @param cardStatus 电子卡状态：1 启用、2 禁用、3 未启用、4 注销
     */
    public void setCardStatus(Integer cardStatus) { this.cardStatus = cardStatus; }

    /**
     * 读取员工姓名。
     *
     * @return 员工姓名
     */
    public String getEmployeeName() { return employeeName; }

    /**
     * 设置员工姓名。
     *
     * @param employeeName 员工姓名
     */
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    /**
     * 读取身份证号。
     *
     * @return 身份证号
     */
    public String getIdCardNo() { return idCardNo; }

    /**
     * 设置身份证号。
     *
     * @param idCardNo 身份证号
     */
    public void setIdCardNo(String idCardNo) { this.idCardNo = idCardNo; }

    /**
     * 读取所属公司。
     *
     * @return 所属公司
     */
    public String getCompany() { return company; }

    /**
     * 设置所属公司。
     *
     * @param company 所属公司
     */
    public void setCompany(String company) { this.company = company; }

    /**
     * 读取所属中心。
     *
     * @return 所属中心
     */
    public String getCenter() { return center; }

    /**
     * 设置所属中心。
     *
     * @param center 所属中心
     */
    public void setCenter(String center) { this.center = center; }

    /**
     * 读取所属部门。
     *
     * @return 所属部门
     */
    public String getDepartment() { return department; }

    /**
     * 设置所属部门。
     *
     * @param department 所属部门
     */
    public void setDepartment(String department) { this.department = department; }

    /**
     * 读取岗位。
     *
     * @return 岗位
     */
    public String getPosition() { return position; }

    /**
     * 设置岗位。
     *
     * @param position 岗位
     */
    public void setPosition(String position) { this.position = position; }

    /**
     * 读取员工照片。
     *
     * @return 员工照片 Base64 编码
     */
    public String getPhoto() { return photo; }

    /**
     * 设置员工照片。
     *
     * @param photo 员工照片 Base64 编码
     */
    public void setPhoto(String photo) { this.photo = photo; }

    /**
     * 读取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTms() { return createTms; }

    /**
     * 设置创建时间。
     *
     * @param createTms 创建时间
     */
    public void setCreateTms(LocalDateTime createTms) { this.createTms = createTms; }

    /**
     * 读取更新时间。
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTms() { return updateTms; }

    /**
     * 设置更新时间。
     *
     * @param updateTms 更新时间
     */
    public void setUpdateTms(LocalDateTime updateTms) { this.updateTms = updateTms; }
}
