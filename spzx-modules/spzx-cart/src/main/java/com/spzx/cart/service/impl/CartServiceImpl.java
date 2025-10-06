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
import com.spzx.product.api.domain.vo.SkuPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl implements ICartService {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    RemoteProductService remoteProductService;


    private String getCartKey(Long userId) {
        String cartKey = "user:cart:" + userId;
        return cartKey;
    }


    @Override
    public void addToCart(Long skuId, Integer skuNum) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        String hashKey = skuId.toString();
        BoundHashOperations hashOperations = redisTemplate.boundHashOps(cartKey);
        if (hashOperations.hasKey(hashKey)) {//添加过购物车
            CartInfo cartInfo = (CartInfo) hashOperations.get(hashKey);
            //单个商品数量不能超过99
            int total = (cartInfo.getSkuNum() + skuNum);
            int threadHold = 99;
            int l = total > threadHold ? threadHold : total;
            cartInfo.setUpdateTime(new Date());
            cartInfo.setSkuNum(l);
            hashOperations.put(hashKey, cartInfo);
        } else {//首次添加
            //商品种类不能超过50种
            Long size = hashOperations.size();//hashKey键的数量
            if (size >= 50) {
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
            cartInfo.setCreateTime(new Date());
//        cartInfo.setIsChecked(1);默认选中

            hashOperations.put(hashKey, cartInfo);
        }


    }

    @Override
    public List<CartInfo> cartList() {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        //List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        BoundHashOperations hashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartInfoList = hashOperations.values();//购物车中旧的商品的价格

        if (CollectionUtils.isEmpty(cartInfoList)) {
            return new ArrayList<>();
        }

        //远程调用商品列表：批量获取最新价格
        List<Long> skuList = cartInfoList.stream().map(CartInfo::getSkuId).toList();
        R<List<SkuPrice>> skuPriceListR = remoteProductService.getSkuPriceList(skuList, SecurityConstants.INNER);
        if (skuPriceListR.getCode() == HttpStatus.ERROR) {
            throw new ServiceException(skuPriceListR.getMsg());
        }
        List<SkuPrice> skuPriceList = skuPriceListR.getData();//数据库中最新的商品价格

        //将List集合转换为map集合
        Map<Long, BigDecimal> skuIdToSkuPriceMap = skuPriceList.stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));

        for (CartInfo cartInfo : cartInfoList) {
            Long skuId = cartInfo.getSkuId();
            BigDecimal skuPrice = skuIdToSkuPriceMap.get(skuId);
            cartInfo.setSkuPrice(skuPrice);//修改最新的价格
        }

        //按照添加的购物车时间进行排序
        cartInfoList = cartInfoList.stream().sorted((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime())).toList();
        return cartInfoList;
    }

    @Override
    public void deleteCart(Long skuId) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        String hashKey = skuId.toString();
        BoundHashOperations hashOperations = redisTemplate.boundHashOps(cartKey);
        if (hashOperations.hasKey(hashKey)) {
            hashOperations.delete(hashKey);
        }
    }

    @Override
    public void checkCart(Long skuId, Integer isChecked) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        String hashKey = skuId.toString();
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        if (hashOperations.hasKey(hashKey)) {
            CartInfo cartInfo = hashOperations.get(hashKey);
            cartInfo.setIsChecked(isChecked);
            hashOperations.put(hashKey, cartInfo);
        }
    }

    @Override
    public void allCheckCart(Integer isChecked) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartInfoList = hashOperations.values();
        if (!CollectionUtils.isEmpty(cartInfoList)) {
            for (CartInfo cartInfo : cartInfoList) {
                cartInfo.setIsChecked(isChecked);
                hashOperations.put(cartInfo.getSkuId().toString(), cartInfo);
            }
        }
    }

    @Override
    public void clearCart() {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        if (redisTemplate.hasKey(cartKey)) {
            redisTemplate.delete(cartKey);
        }
    }

    @Override
    public List<CartInfo> getCartCheckedList(Long userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        String cartKey = this.getCartKey(userId);
        List<CartInfo> cartCachInfoList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(cartCachInfoList)) {
            for (CartInfo cartInfo : cartCachInfoList) {
                // 获取选中的商品！
                if (cartInfo.getIsChecked().intValue() == 1) {
                    cartInfoList.add(cartInfo);
                }
            }
        }
        return cartInfoList;
    }

    @Override
    public Boolean updateCartPrice(Long userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        String cartKey = this.getCartKey(userId);
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartCachInfoList = hashOperations.values();
        if (!CollectionUtils.isEmpty(cartCachInfoList)) {

            //调用商品微服务，根据sukIdList批量查询List<SkuPrice>
            List<Long> skuIdList = cartCachInfoList.stream().filter(cartInfo -> cartInfo.getIsChecked().intValue() == 1).map(CartInfo::getSkuId).toList();
            R<List<SkuPrice>> skuPriceListR = remoteProductService.getSkuPriceList(skuIdList, SecurityConstants.INNER);
            if (skuPriceListR.getCode() == HttpStatus.ERROR) {
                throw new ServiceException(skuPriceListR.getMsg());
            }
            List<SkuPrice> skuPriceList = skuPriceListR.getData();
            //将List集合转换为map集合
            Map<Long, BigDecimal> skuIdToSalePriceMap = skuPriceList.stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));
            for (CartInfo cartInfo : cartCachInfoList) {
                // 获取选中的商品！
                if (cartInfo.getIsChecked().intValue() == 1) {
                    BigDecimal salePrice = skuIdToSalePriceMap.get(cartInfo.getSkuId());
                    cartInfo.setSkuPrice(salePrice);
                    cartInfo.setCartPrice(salePrice);
                    hashOperations.put(cartInfo.getSkuId().toString(), cartInfo);
                }
            }
        }
        return true;
    }

    @Override
    public Boolean deleteCartCheckedList(Long userId) {
        String cartKey = this.getCartKey(userId);
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartInfoList = hashOperations.values();
        if (!CollectionUtils.isEmpty(cartInfoList)) {
            for (CartInfo cartInfo : cartInfoList) {
                if (cartInfo.getIsChecked().intValue() == 1) {
                    hashOperations.delete(cartInfo.getSkuId().toString());
                }
            }
        }
        return true;
    }

}