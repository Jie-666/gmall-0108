package com.atguigu.gmall.ums.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.ums.entity.UserEntity;

import java.util.Map;

/**
 * 用户表
 *
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-07-13 19:17:25
 */
public interface UserService extends IService<UserEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    Boolean checkUserData(String data, Integer type);

    void register(UserEntity userEntity, String code);

    UserEntity queryUser(String loginName, String password);

    void sendCode(String phone);
}

