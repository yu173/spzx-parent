package com.spzx.product;

import com.spzx.common.security.annotation.EnableCustomConfig;
import com.spzx.common.security.annotation.EnableRyFeignClients;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.mapper.ProductSkuMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;


@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class SpzxProductApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(SpzxProductApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  系统模块启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }

    @Autowired
    ProductSkuMapper productSkuMapper;
    @Autowired
    RedisTemplate redisTemplate;

    @Override
    public void run(String... args) throws Exception {
        String key = "sku:product:data";
        //这些数据一定是在数据库中有的
        List<ProductSku> productSkuList = productSkuMapper.selectList(null);//软删除不查
        for(ProductSku productSku: productSkuList){
            //bitmap特使字符串。由很多bit组成字符串。“11010001”  表示id为1，2，4，8的数据是存在的。
            redisTemplate.opsForValue().setBit(key,productSku.getId(),true);//productSkuId偏移位置，true数据存在，在bit位上存储1
        }
    }
}
