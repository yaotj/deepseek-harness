package com.chinasofti.huateng.generator.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.chinasofti.huateng.common.core.text.Convert;
import com.chinasofti.huateng.generator.domain.GenTableColumn;
import com.chinasofti.huateng.generator.mapper.GenTableColumnMapper;


@Service
public class GenTableColumnServiceImpl implements IGenTableColumnService 
{
	@Autowired
	private GenTableColumnMapper genTableColumnMapper;


	@Override
	public List<GenTableColumn> selectGenTableColumnListByTableId(Long tableId)
	{
	    return genTableColumnMapper.selectGenTableColumnListByTableId(tableId);
	}
	

	@Override
	public int insertGenTableColumn(GenTableColumn genTableColumn)
	{
	    return genTableColumnMapper.insertGenTableColumn(genTableColumn);
	}
	

	@Override
	public int updateGenTableColumn(GenTableColumn genTableColumn)
	{
	    return genTableColumnMapper.updateGenTableColumn(genTableColumn);
	}


	@Override
	public int deleteGenTableColumnByIds(String ids)
	{
		return genTableColumnMapper.deleteGenTableColumnByIds(Convert.toLongArray(ids));
	}
}
