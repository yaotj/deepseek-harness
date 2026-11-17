package com.chinasofti.huateng.system.mapper;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysPost;


public interface SysPostMapper
{

    public List<SysPost> selectPostList(SysPost post);


    public List<SysPost> selectPostAll();


    public SysPost selectPostById(Long postId);


    public List<Long> selectPostListByUserId(Long userId);


    public List<SysPost> selectPostsByUserName(String userName);


    public int deletePostById(Long postId);


    public int deletePostByIds(Long[] postIds);


    public int updatePost(SysPost post);


    public int insertPost(SysPost post);


    public SysPost checkPostNameUnique(String postName);


    public SysPost checkPostCodeUnique(String postCode);
}
