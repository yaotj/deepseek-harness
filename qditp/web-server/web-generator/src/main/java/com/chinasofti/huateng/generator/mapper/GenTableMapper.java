package com.chinasofti.huateng.generator.mapper;

import java.util.List;
import com.chinasofti.huateng.generator.domain.GenTable;


public interface GenTableMapper
{

    public List<GenTable> selectGenTableList(GenTable genTable);


    public List<GenTable> selectDbTableList(GenTable genTable);


    public List<GenTable> selectDbTableListByNames(String[] tableNames);


    public List<GenTable> selectGenTableAll();


    public GenTable selectGenTableById(Long id);


    public GenTable selectGenTableByName(String tableName);


    public int insertGenTable(GenTable genTable);


    public int updateGenTable(GenTable genTable);


    public int deleteGenTableByIds(Long[] ids);


    public int createTable(String sql);
}
