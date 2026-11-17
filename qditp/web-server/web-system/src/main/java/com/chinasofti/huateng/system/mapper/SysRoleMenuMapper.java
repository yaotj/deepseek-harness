package com.chinasofti.huateng.system.mapper;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysRoleMenu;


public interface SysRoleMenuMapper
{

    public int checkMenuExistRole(Long menuId);


    public int deleteRoleMenuByRoleId(Long roleId);


    public int deleteRoleMenu(Long[] ids);


    public int batchRoleMenu(List<SysRoleMenu> roleMenuList);
}
