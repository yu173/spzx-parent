package com.spzx.cart.service.impl;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.constant.HttpStatus;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CartServiceImpl implements ICartService {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    RemoteProductService remoteProductService;

    @Override
    public void addToCart(Long skuId, Integer skuNum) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = "user:cart:" + userId;
        String hashKey = skuId.toString();
        BoundHashOperations hashOperations = redisTemplate.boundHashOps(cartKey);
        if (hashOperations.hasKey(hashKey)) {//添加过购物车
            CartInfo cartInfo = (CartInfo) hashOperations.get(hashKey);
            //单个商品数量不能超过99
            int total = (cartInfo.getSkuNum() + skuNum);
            int threadHold = 99;
            int l = total > threadHold ? threadHold : total;
            cartInfo.setSkuNum(l);
            hashOperations.put(hashKey, cartInfo);
        } else {//首次添加
            //商品种类不能超过50种
            Long size = hashOperations.size();//hashKey键的数量
            if(size >= 50){
                throw new ServiceException("商品种类不能超过50种");
            }
            CartInfo cartInfo = new CartInfo();
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            cartInfo.setSkuNum(1);//首次添加该商品，默认数量=1


            //远程调用接口  根据skuId远程调用商品微服务接口，来查询ProductSku对象
            R<ProductSku> productSkuR = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            if (productSkuR.getCode() != HttpStatus.SUCCESS) {//500表示服务接口降级处理了
                throw new ServiceException(productSkuR.getMsg());
            }
            ProductSku productSku = productSkuR.getData();
            cartInfo.setCartPrice(productSku.getSalePrice());
            cartInfo.setSkuPrice(productSku.getSalePrice());
            cartInfo.setThumbImg(productSku.getThumbImg());
            cartInfo.setSkuName(productSku.getSkuName());
//        cartInfo.setIsChecked(1);默认选中

            hashOperations.put(hashKey, cartInfo);
        }


    }
}