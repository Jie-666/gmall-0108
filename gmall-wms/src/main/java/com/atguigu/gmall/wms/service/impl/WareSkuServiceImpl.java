package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.Vo.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private WareSkuMapper wareSkuMapper;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String LOCK_PREFIX = "stock:lock:";
    private static final String KEY_PREFIX = "stock:info:";

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public List<SkuLockVo> checkAndLock(List<SkuLockVo> lockVos, String orderToken) {
        if (CollectionUtils.isEmpty(lockVos)) {
            return null;
        }

        //遍历所有库存，并验库存、锁库存
        lockVos.forEach(lockVo -> {
            //每一个商品验库存并锁库存
            this.checkLock(lockVo);
        });

        //判断是否有锁定失败的记录，如果存在，把所有锁定成功的库存释放掉
        if (lockVos.stream().anyMatch(lockVo-> !lockVo.getLock())){
            //获取所有锁定成功的记录
            lockVos.stream().filter(SkuLockVo::getLock).collect(Collectors.toList()).forEach(lockVo->{
                //解锁对应成功的库存  本质是更新库存，库存数-count
                this.wareSkuMapper.unlock(lockVo.getWareSkuId(),lockVo.getCount());
            });
            //并返回锁定信息
            return lockVos;
        }

        //为了方便未来减库存 或者解锁库存，需要吧锁定信息缓存下来
        this.redisTemplate.opsForValue().set(KEY_PREFIX+orderToken, JSON.toJSONString(lockVos));

        //发送延时消息，定时解锁库存
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.ttl",orderToken);

        //如果都锁定成功，返回null
        return null;
    }

    //验库存、锁库存
    private void checkLock(SkuLockVo skuLockVo) {

        RLock lock = this.redissonClient.getLock(LOCK_PREFIX + skuLockVo.getSkuId());
        lock.lock();

        try {
            //验库存:查询库存数满足的库存列表表
            List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.checkStock(skuLockVo.getSkuId(), skuLockVo.getCount());
            if (CollectionUtils.isEmpty(wareSkuEntities)) { //返回值为空，说明验库存和锁库存失败
                //这只锁定状态为false
                skuLockVo.setLock(false);
                return;
            }
            //锁库存：更新库存锁定数  这里取第一个满足的仓库   （大数据分析就近仓库）
            WareSkuEntity wareSkuEntity = wareSkuEntities.get(0);
            //如果锁成功，影响条数为1
            if (this.wareSkuMapper.lock(wareSkuEntity.getId(), skuLockVo.getCount()) == 1) {
                //这只锁定状态为true
                skuLockVo.setLock(true);
                //保存锁定商品的仓库id，方便减库存和解锁库存
                skuLockVo.setWareSkuId(wareSkuEntity.getId());
            }
        } finally {
            lock.unlock();
        }


    }


}