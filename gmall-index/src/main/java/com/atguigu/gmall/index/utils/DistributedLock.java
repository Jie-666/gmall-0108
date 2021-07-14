package com.atguigu.gmall.index.utils;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class DistributedLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Timer timer;

    public Boolean lock(String lockName, String uuid, Integer expire) {
        String script = "if(redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1) " +
                "then " +
                "   redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
                "   redis.call('expire', KEYS[1], ARGV[2]) " +
                "   return 1 " +
                "else " +
                "   return 0 " +
                "end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expire.toString());
        if (!flag) {
            try {
                Thread.sleep(100);
                lock(lockName, uuid, expire);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else {
            this.renewExpire(lockName, uuid, expire);
        }
        return true;
    }

    public void unlock(String lockName, String uuid) {
        String script = "if(redis.call('hexists',KEYS[1],ARGV[1]) == 0) " +
                "then " +
                "   return nil " +
                "else  " +
                "if(redis.call('hincrby', KEYS[1] ,ARGV[1], -1)>0) " +
                "then " +
                "   return 0 " +
                "else " +
                "   redis.call('del',KEYS[1]) " +
                "   return 1 " +
                "end " +
                "end";
        //这里的返回值类型不可以使用bool，因为返回值中有nil ，空再java中也是false，推荐使用Long
        Long flag = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(lockName), uuid);
        if (flag == null) {
            throw new IllegalMonitorStateException("当前尝试释放的锁，不属于你");
        } else if (flag == 1){
            //释放锁成功，取消定时器
            this.timer.cancel();
        }
    }

    private void renewExpire(String lockName, String uuid, Integer expire) {
        String script = "if(redis.call('hexists', KEYS[1], ARGV[1]) == 1) then redis.call('expire', KEYS[1], ARGV[2]) end;";
        this.timer =  new Timer();
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expire.toString());
            }
        }, expire * 1000 / 3, expire * 1000 / 3);
    }

    public static void main(String[] args) {
//        System.out.println("当前时间：" + System.currentTimeMillis());
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.out.println("定时任务" + System.currentTimeMillis());
//            }
//        }, 5000, 1000);
        BloomFilter<CharSequence> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 20, 0.5);
        System.out.println(bloomFilter.put("1"));
        System.out.println(bloomFilter.put("2"));
        System.out.println(bloomFilter.put("3"));
        System.out.println(bloomFilter.put("4"));
        System.out.println(bloomFilter.put("5"));
        System.out.println(bloomFilter.put("6"));
        System.out.println(bloomFilter.put("7"));
        System.out.println(bloomFilter.put("8"));
        System.out.println("======================================");
        System.out.println(bloomFilter.mightContain("1"));
        System.out.println(bloomFilter.mightContain("3"));
        System.out.println(bloomFilter.mightContain("5"));
        System.out.println(bloomFilter.mightContain("7"));
        System.out.println(bloomFilter.mightContain("9"));
        System.out.println(bloomFilter.mightContain("10"));
        System.out.println(bloomFilter.mightContain("11"));
        System.out.println(bloomFilter.mightContain("12"));
        System.out.println(bloomFilter.mightContain("13"));
        System.out.println(bloomFilter.mightContain("14"));
        System.out.println(bloomFilter.mightContain("15"));
        System.out.println(bloomFilter.mightContain("122"));
        System.out.println(bloomFilter.mightContain("18"));
        System.out.println(bloomFilter.mightContain("145"));
        System.out.println(bloomFilter.mightContain("112"));
        System.out.println(bloomFilter.mightContain("145"));
        System.out.println(bloomFilter.mightContain("1564"));
        System.out.println(bloomFilter.mightContain("17"));
        System.out.println(bloomFilter.mightContain("156"));
        System.out.println(bloomFilter.mightContain("18"));
        System.out.println(bloomFilter.mightContain("13"));
        System.out.println(bloomFilter.mightContain("145"));
    }
}
