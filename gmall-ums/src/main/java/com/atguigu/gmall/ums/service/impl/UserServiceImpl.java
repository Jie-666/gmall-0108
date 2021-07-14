package com.atguigu.gmall.ums.service.impl;

import org.apache.catalina.User;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.ums.mapper.UserMapper;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.ums.service.UserService;
import org.springframework.util.CollectionUtils;


@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<UserEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<UserEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public Boolean checkUserData(String data, Integer type) {
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        switch (type){
            case 1: queryWrapper.eq("username", data); break;
            case 2: queryWrapper.eq("phone", data); break;
            case 3: queryWrapper.eq("email", data); break;

            default:
                return null;

        }

        return this.count(queryWrapper)==0;
    }

    @Override
    public void register(UserEntity userEntity, String code) {
        //判断验证码是否正确

        //生成盐
        String salt = StringUtils.substring(UUID.randomUUID().toString(), 0, 6);
        userEntity.setSalt(salt);
        //对密码进行加盐加密
        userEntity.setPassword(DigestUtils.md5Hex(userEntity.getPassword()+salt));

        //新增用户\
        //保存之前设置默认值
        userEntity.setLevelId(1l);
        userEntity.setNickname(userEntity.getUsername());
        userEntity.setSourceType(1);
        userEntity.setIntegration(1000);
        userEntity.setGrowth(1000);
        userEntity.setStatus(1);
        userEntity.setCreateTime(new Date());
        this.save(userEntity);
        //删除验证码
    }

    @Override
    public UserEntity queryUser(String loginName, String password) {
        //1、根据登录名查询用户信息
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginName)
                .or().eq("phone", loginName)
                .or().eq("email", loginName);
        List<UserEntity> userEntities = this.list(queryWrapper);
        //2、如果用户名为空，直接返回null
        if (CollectionUtils.isEmpty(userEntities)){
            return null;
        }
        //3、获取用户名的盐，对用户输入的明文密码进行加盐加密
        for (UserEntity userEntity : userEntities) {
            String salt = userEntity.getSalt();
            String saltpw = DigestUtils.md5Hex(password + salt);
            //4、用户输入密码加密后，和 数据库中的密码比较
            if (StringUtils.equals(saltpw, userEntity.getPassword())) {
                return userEntity;
            }
        }
        return null;
    }

    @Override
    public void sendCode(String phone) {

    }

}