package com.chinasofti.huateng.system.mapper;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysNotice;


public interface SysNoticeMapper
{

    public SysNotice selectNoticeById(Long noticeId);


    public List<SysNotice> selectNoticeList(SysNotice notice);


    public int insertNotice(SysNotice notice);


    public int updateNotice(SysNotice notice);


    public int deleteNoticeById(Long noticeId);


    public int deleteNoticeByIds(Long[] noticeIds);
}
