package com.chinasofti.huateng.generator.service;

import java.util.List;
import com.chinasofti.huateng.generator.domain.GenTableColumn;


public interface IGenTableColumnService
{

    public List<GenTableColumn> selectGenTableColumnListByTableId(Long tableId);


    public int insertGenTableColumn(GenTableColumn genTableColumn);


    public int updateGenTableColumn(GenTableColumn genTableColumn);


    public int deleteGenTableColumnByIds(String ids);
}
