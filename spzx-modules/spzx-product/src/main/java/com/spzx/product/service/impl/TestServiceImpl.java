package com.spzx.product.service.impl;

import com.spzx.common.core.utils.StringUtils;
import com.spzx.product.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {

    @Autowired
    RedisTemplate redisTemplate;//可以操作任意数据类型：string、hash、list、set、zset

    @Autowired
    StringRedisTemplate stringRedisTemplate; //SpringBoot自动化配置

    //synchronized 本地锁，只能同步当前jvm虚拟机内部的多个线程
    //本地锁在集群环境下，失效的
    //集群环境下，需要跨多个进程的线程之间同步，需要使用分布式锁。
    /*@Override
    public synchronized void testLock() {

        // 查询Redis中的num值
        String value = (String) this.stringRedisTemplate.opsForValue().get("num");
        // 没有该值return
        if (StringUtils.isBlank(value)) {
            return;
        }
        // 有值就转成成int
        int num = Integer.parseInt(value);
        // 把Redis中的num值+1
        this.stringRedisTemplate.opsForValue().set("num", String.valueOf(++num)); //  java 中  ++ 操作不是原子的。

    }*/

    /**
     * 1、分布式锁如果不能正确的释放锁，会导致死锁。例如，加锁后服务器宕机。 9205上锁宕机，9305、9405无法上锁，一直自旋无法上锁 - 死锁。
        给分布式锁标记，加上超时设置
     */
    @Override
    public void testLock() {
        //获取分布式锁： setnx lock 0
        //先加锁，在设置过期，代码不是原子性的。依然会出现死锁。
//        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent("lock", "0");
//        stringRedisTemplate.expire("lock", 5, TimeUnit.MINUTES);

        //解决死锁问题：加锁的同时，设置过期时间，是原子性的。
        //Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent("lock", "0", 10, TimeUnit.SECONDS);

        String uuid = UUID.randomUUID().toString().replaceAll("-","");
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent("lock", uuid, 10, TimeUnit.SECONDS);//原子性的

        if (isLock) {
            try {
                Thread.sleep(15000);//15s
                // 查询Redis中的num值
                String value = (String) this.stringRedisTemplate.opsForValue().get("num");
                // 没有该值return
                if (StringUtils.isBlank(value)) {
                    return;
                }
                // 有值就转成成int
                int num = Integer.parseInt(value);
                // 把Redis中的num值+1
                this.stringRedisTemplate.opsForValue().set("num", String.valueOf(++num)); //  java 中  ++ 操作不是原子的。
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                //删除分布式锁
                //避免释放他人的锁：删除锁时，判断锁是否为自己上的锁，是才会删除，不是，不能删。因为锁是别人加的。
                //设置过期时间，时间到了，锁会被自动释放，其他线程抢到锁干活去了。
                // 当前线程继续执行业务，然后会被删除锁。此时删除的锁可能是别人上的锁。
                //stringRedisTemplate.delete("lock");

                /*if (uuid.equals(stringRedisTemplate.opsForValue().get("lock"))) {
                    System.out.println("锁是自己的，释放掉");
                    stringRedisTemplate.delete("lock");
                }else{
                    System.out.println("锁不是自己的，别删");
                }*/
                //判断和删除依然不是原子性的，所以删除时依然可能出现删除别人的锁。
                //使用LUA脚本来保证原子性
                String script = "if redis.call('get',KEYS[1]) == ARGV[1]\n" +
                        "then \n" +
                        "\treturn redis.call('del',KEYS[1])\n" +
                        "else\n" +
                        "\treturn 0\n" +
                        "end";
                RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

                Long flag = stringRedisTemplate.execute(redisScript, Arrays.asList("lock"), uuid);
                System.out.println("flag= "+flag);
            }
        } else {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            testLock();
        }
    }

}
