package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private DistributedLock lock;
    @Autowired
    private RedissonClient redissonClient;

    private static final String KEY_PREFIX = "index:cates:";
    private static final String LOCK_PREFIX = "index:cates:lock:";

    public List<CategoryEntity> queryLvl1Categories() {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        return categoryEntities;
    }

    @GmallCache(prefix = KEY_PREFIX, timeout = 259200, random = 14400,lock = LOCK_PREFIX)
    public List<CategoryEntity> queryLvl2WithSubsByPid(Long pid) {

        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2WithSubsByPid(pid);
        return listResponseVo.getData();

    }

    public List<CategoryEntity> queryLvl2WithSubsByPid2(Long pid) {
        //先查询缓存，如果缓存不为空，直接返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseArray(json, CategoryEntity.class);
        }

        //为了防止缓存击穿添加分布式锁
        RLock fairLock = this.redissonClient.getFairLock(LOCK_PREFIX + pid);
        fairLock.lock();

        try {
            // 在当前线程获取锁的过程，可能其他线程已经把数据放入缓存，此时最好在此查询缓存。
            String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
            if (StringUtils.isNotBlank(json2)) {
                return JSON.parseArray(json2, CategoryEntity.class);
            }
            //如果缓存为空，则远程调用或者直接查询数据库，并放入缓存
            ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2WithSubsByPid(pid);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();
            //放入缓存
            if (CollectionUtils.isEmpty(categoryEntities)) {
                //为了防止缓存穿透，即使是数据为空，也放入缓存，缓存时间不能过长
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 5, TimeUnit.MINUTES);
            } else {
                //为了防止缓存雪崩，给缓存时间添加随机值。
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 180 + new Random().nextInt(10), TimeUnit.DAYS);
            }
            return categoryEntities;
        } finally {
            fairLock.unlock();
        }
    }

    public void testLock() {
        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();

        try {
            // 获取锁成功的线程，执行业务操作
            String numStr = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isEmpty(numStr)) {
                this.redisTemplate.opsForValue().set("num", "1");
            }
            int num = Integer.parseInt(numStr);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
            try {
                TimeUnit.SECONDS.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } finally {
            lock.unlock();
        }
    }

    public void testLock3() {
        String uuid = UUID.randomUUID().toString();
        Boolean flag = this.lock.lock("lock", uuid, 30);
        if (flag) {
            // 获取锁成功的线程，执行业务操作
            String numStr = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isEmpty(numStr)) {
                this.redisTemplate.opsForValue().set("num", "1");
            }
            int num = Integer.parseInt(numStr);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

            //this.testSub("lock", uuid);
            try {
                TimeUnit.SECONDS.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            lock.unlock("lock", uuid);
        }
    }

    //测试可重入锁
    public void testSub(String lockName, String uuid) {
        lock.lock(lockName, uuid, 30);
        System.out.println("测试可重入锁");
        lock.unlock(lockName, uuid);

    }

    public void testLock2() {
        //获取锁,setIfAbsent() == setnx
        String uuid = UUID.randomUUID().toString();
        Boolean flag = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);
        if (!flag) {
            //获取锁失败的线程，重复获取锁
            try {
                Thread.sleep(100);
                this.testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            // 获取锁成功的线程，执行业务操作
            String numStr = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isEmpty(numStr)) {
                this.redisTemplate.opsForValue().set("num", "1");
            }
            int num = Integer.parseInt(numStr);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
            //释放锁 使用lua脚本，实现释放锁和判断是否是自己的锁的原子性。
            String script = "if(redis.call('get',KEYS[1])==ARGV[1]) then return redis.call('del',KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList("lock"), uuid);
//            if (StringUtils.equals(uuid, this.redisTemplate.opsForValue().get("lock"))) {
//                //释放锁之前判断是不是自己的锁，防止误删。
//                this.redisTemplate.delete("lock");
//            }
        }
    }

    public void testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock(10, TimeUnit.SECONDS);
        System.out.println("=================测试读锁======");
        //rwLock.readLock().unlock();
    }

    public void testWrite() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);
        System.out.println("=================测试写锁======");
        //rwLock.writeLock().unlock();
    }

    public void testLatch() {
        try {
            RCountDownLatch cdl = this.redissonClient.getCountDownLatch("cdl");
            cdl.trySetCount(6);
            cdl.await();

            System.out.println("班长准备锁门了。。。。。。。。");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void testCountDown() {
        RCountDownLatch cdl = this.redissonClient.getCountDownLatch("cdl");
        cdl.countDown();

        System.out.println("==============出来了一位同学===========");
    }
}
