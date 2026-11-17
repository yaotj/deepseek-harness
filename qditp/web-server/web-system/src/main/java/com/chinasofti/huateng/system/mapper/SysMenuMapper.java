package com.chinasofti.huateng.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.common.core.domain.entity.SysMenu;


@Mapper
public interface SysMenuMapper
{

    public List<SysMenu> selectMenuList(SysMenu menu);


    public List<String> selectMenuPerms();


    public List<SysMenu> selectMenuListByUserId(SysMenu menu);


    public List<String> selectMenuPermsByRoleId(Long roleId);


    public List<String> selectMenuPermsByUserId(Long userId);


    public List<SysMenu> selectMenuTreeAll();


    public List<SysMenu> selectMenuTreeByUserId(Long userId);


    public List<Long> selectMenuListByRoleId(@Param("roleId") Long roleId, @Param("menuCheckStrictly") boolean menuCheckStrictly);


    public SysMenu selectMenuById(Long menuId);


    public int hasChildByMenuId(Long menuId);


    public int insertMenu(SysMenu menu);


    public int updateMenu(SysMenu menu);


    public int deleteMenuById(Long menuId);


    public SysMenu checkMenuNameUnique(@Param("menuName") String menuName, @Param("parentId") Long parentId);
}
