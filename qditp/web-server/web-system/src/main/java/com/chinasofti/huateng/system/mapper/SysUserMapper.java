package com.chinasofti.huateng.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.common.core.domain.entity.SysUser;


public interface SysUserMapper
{

    public List<SysUser> selectUserList(SysUser sysUser);


    public List<SysUser> selectAllocatedList(SysUser user);


    public List<SysUser> selectUnallocatedList(SysUser user);


    public SysUser selectUserByUserName(String userName);


    public SysUser selectUserById(Long userId);


    public int insertUser(SysUser user);


    public int updateUser(SysUser user);


    public int updateUserAvatar(@Param("userId") Long userId, @Param("avatar") String avatar);


    public int updateUserStatus(@Param("userId") Long userId, @Param("status") String status);


    public int updateLoginInfo(@Param("userId") Long userId, @Param("loginIp") String loginIp, @Param("loginDate") Date loginDate);


    public int resetUserPwd(@Param("userId") Long userId, @Param("password") String password);


    public int deleteUserById(Long userId);


    public int deleteUserByIds(Long[] userIds);


    public int clearDeptIdByDeptIds(@Param("deptIds") List<Long> deptIds);


    public SysUser checkUserNameUnique(String userName);


    public SysUser checkPhoneUnique(String phonenumber);


    public SysUser checkEmailUnique(String email);
}
