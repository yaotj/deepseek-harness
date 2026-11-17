package com.chinasofti.huateng.system.service;

import java.util.Date;
import java.util.List;
import com.chinasofti.huateng.common.core.domain.entity.SysUser;


public interface ISysUserService
{

    public List<SysUser> selectUserList(SysUser user);


    public List<SysUser> selectAllocatedList(SysUser user);


    public List<SysUser> selectUnallocatedList(SysUser user);


    public SysUser selectUserByUserName(String userName);


    public SysUser selectUserById(Long userId);


    public String selectUserRoleGroup(String userName);


    public String selectUserPostGroup(String userName);


    public boolean checkUserNameUnique(SysUser user);


    public boolean checkPhoneUnique(SysUser user);


    public boolean checkEmailUnique(SysUser user);


    public void checkUserAllowed(SysUser user);


    public void checkUserDataScope(Long userId);


    public int insertUser(SysUser user);


    public boolean registerUser(SysUser user);


    public int updateUser(SysUser user);


    public void insertUserAuth(Long userId, Long[] roleIds);


    public int updateUserStatus(SysUser user);


    public int updateUserProfile(SysUser user);


    public boolean updateUserAvatar(Long userId, String avatar);


    public void updateLoginInfo(Long userId, String loginIp, Date loginDate);


    public int resetPwd(SysUser user);


    public int resetUserPwd(Long userId, String password);


    public int deleteUserById(Long userId);


    public int deleteUserByIds(Long[] userIds);


    public String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName);
}
