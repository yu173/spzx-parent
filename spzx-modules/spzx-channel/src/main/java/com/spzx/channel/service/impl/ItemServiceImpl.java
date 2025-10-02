package com.spzx.channel.service.impl;

import com.alibaba.fastjson2.JSON;
import com.spzx.channel.domain.ItemVo;
import com.spzx.channel.service.IItemService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.Product;
import com.spzx.product.api.domain.ProductDetails;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.product.api.domain.vo.SkuStockVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
@Slf4j
public class ItemServiceImpl implements IItemService {

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    ThreadPoolExecutor threadPoolExecutor;

    /*
    远程调用6个接口获取数据，组装vo数据，返回。
    远程调用提高效率：  由同步串行调用  变为  异步并发调用
    CompletableFuture  来自JUC。专门用于多线程并发编程场景使用。
        supplyAsync()  供给型方法，需要根据输入，得到输出结果
        thenAcceptAsync()  根据输入完成异步操作。不会返回结果。
        runAsync()    无需输入，也不会返回结果，只会默默干活
        allOf()     等待所有的异步操作子线程完成任务，再继续执行主线程代码；属于阻塞性方法
     */

    @Override
    public ItemVo item(Long skuId) throws Exception {

        //使用布隆过滤器或bitmap判断数据是否存在，存在再调用远程从Redis中或数据库中获取数据；id不在bitmap中，根本不需要调用远程。
        String key = "sku:product:data:";
        Boolean isExists = redisTemplate.opsForValue().getBit(key, skuId);
        if(isExists){
            throw new Exception("数据不存在，不允许穿透");
        }


        ItemVo itemVo = new ItemVo();

        //任务1.获取sku信息
        CompletableFuture<ProductSku> productSkuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            R<ProductSku> productSkuResult = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            if (R.FAIL == productSkuResult.getCode()) {
                throw new ServiceException(productSkuResult.getMsg());
            }
            ProductSku productSku = productSkuResult.getData();
            if (productSku == null) {
                throw new ServiceException("商品不存在");
            }
            itemVo.setProductSku(productSku);
            return productSku;
        }, threadPoolExecutor);


        //任务2.获取商品信息
        CompletableFuture<Void> productCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> {
            R<Product> productResult = remoteProductService.getProduct(productSku.getProductId(), SecurityConstants.INNER);
            if (R.FAIL == productResult.getCode()) {
                throw new ServiceException(productResult.getMsg());
            }
            Product product = productResult.getData();
            itemVo.setProduct(product);
            itemVo.setSliderUrlList(Arrays.asList(product.getSliderUrls().split(",")));
            itemVo.setSpecValueList(JSON.parseArray(product.getSpecValue()));
        }, threadPoolExecutor);


        //任务3.获取商品最新价格
        CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
            R<SkuPrice> skuPriceResult = remoteProductService.getSkuPrice(skuId, SecurityConstants.INNER);
            if (R.FAIL == skuPriceResult.getCode()) {
                throw new ServiceException(skuPriceResult.getMsg());
            }
            SkuPrice skuPrice = skuPriceResult.getData();
            itemVo.setSkuPrice(skuPrice);
        }, threadPoolExecutor);


        //任务4.获取商品详情    只有输出没有返回
        CompletableFuture<Void> productDetailsCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> {
            R<ProductDetails> productDetailsResult = remoteProductService.getProductDetails(productSku.getProductId(), SecurityConstants.INNER);
            if (R.FAIL == productDetailsResult.getCode()) {
                throw new ServiceException(productDetailsResult.getMsg());
            }
            ProductDetails productDetails = productDetailsResult.getData();
            itemVo.setDetailsImageUrlList(Arrays.asList(productDetails.getImageUrls().split(",")));
        }, threadPoolExecutor);


        //任务5.获取商品规格对应商品skuId信息
        CompletableFuture<Void> skuSpecMapCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> {
            R<Map<String, Long>> skuSpecValueResult = remoteProductService.getSkuSpecValue(productSku.getProductId(), SecurityConstants.INNER);
            if (R.FAIL == skuSpecValueResult.getCode()) {
                throw new ServiceException(skuSpecValueResult.getMsg());
            }
            Map<String, Long> skuSpecValueMap = skuSpecValueResult.getData();
            itemVo.setSkuSpecValueMap(skuSpecValueMap);
        }, threadPoolExecutor);


        //任务6.获取商品库存信息
        CompletableFuture<Void> skuStockCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> {
            R<SkuStockVo> skuStockResult = remoteProductService.getSkuStock(skuId, SecurityConstants.INNER);
            if (R.FAIL == skuStockResult.getCode()) {
                throw new ServiceException(skuStockResult.getMsg());
            }
            SkuStockVo skuStockVo = skuStockResult.getData();
            itemVo.setSkuStockVo(skuStockVo);
            productSku.setStockNum(skuStockVo.getAvailableNum());
        }, threadPoolExecutor);

        CompletableFuture.allOf(productSkuCompletableFuture,
                productCompletableFuture,
                skuPriceCompletableFuture,
                productDetailsCompletableFuture,
                skuSpecMapCompletableFuture,
                skuStockCompletableFuture
                ).join();//阻塞，等待6个异步的任务都完成，代码继续执行
        return itemVo;
    }

    /*@Override
    public ItemVo item(Long skuId) throws Exception {

        //使用布隆过滤器或bitmap判断数据是否存在，存在再调用远程从Redis中或数据库中获取数据；id不在bitmap中，根本不需要调用远程。
        String key = "sku:product:data:";
        Boolean isExists = redisTemplate.opsForValue().getBit(key, skuId);
        if(!isExists){
            throw new Exception("数据不存在，不允许穿透");
        }


        ItemVo itemVo = new ItemVo();

        //任务1.获取sku信息
        R<ProductSku> productSkuResult = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
        if (R.FAIL == productSkuResult.getCode()) {
            throw new ServiceException(productSkuResult.getMsg());
        }
        ProductSku productSku = productSkuResult.getData();
        if(productSku == null){
            throw new ServiceException("商品不存在");
        }
        itemVo.setProductSku(productSku);


        //任务2.获取商品信息
        R<Product> productResult = remoteProductService.getProduct(productSku.getProductId(), SecurityConstants.INNER);
        if (R.FAIL == productResult.getCode()) {
            throw new ServiceException(productResult.getMsg());
        }
        Product product = productResult.getData();
        itemVo.setProduct(product);
        itemVo.setSliderUrlList(Arrays.asList(product.getSliderUrls().split(",")));
        itemVo.setSpecValueList(JSON.parseArray(product.getSpecValue()));


        //任务3.获取商品最新价格
        R<SkuPrice> skuPriceResult = remoteProductService.getSkuPrice(skuId, SecurityConstants.INNER);
        if (R.FAIL == skuPriceResult.getCode()) {
            throw new ServiceException(skuPriceResult.getMsg());
        }
        SkuPrice skuPrice = skuPriceResult.getData();
        itemVo.setSkuPrice(skuPrice);


        //任务4.获取商品详情
        R<ProductDetails> productDetailsResult = remoteProductService.getProductDetails(productSku.getProductId(), SecurityConstants.INNER);
        if (R.FAIL == productDetailsResult.getCode()) {
            throw new ServiceException(productDetailsResult.getMsg());
        }
        ProductDetails productDetails = productDetailsResult.getData();
        itemVo.setDetailsImageUrlList(Arrays.asList(productDetails.getImageUrls().split(",")));


        //任务5.获取商品规格对应商品skuId信息
        R<Map<String, Long>> skuSpecValueResult = remoteProductService.getSkuSpecValue(productSku.getProductId(), SecurityConstants.INNER);
        if (R.FAIL == skuSpecValueResult.getCode()) {
            throw new ServiceException(skuSpecValueResult.getMsg());
        }
        Map<String, Long> skuSpecValueMap = skuSpecValueResult.getData();
        itemVo.setSkuSpecValueMap(skuSpecValueMap);


        //任务6.获取商品库存信息
        R<SkuStockVo> skuStockResult = remoteProductService.getSkuStock(skuId, SecurityConstants.INNER);
        if (R.FAIL == skuStockResult.getCode()) {
            throw new ServiceException(skuStockResult.getMsg());
        }
        SkuStockVo skuStockVo = skuStockResult.getData();
        itemVo.setSkuStockVo(skuStockVo);
        productSku.setStockNum(skuStockVo.getAvailableNum());

        return itemVo;
    }*/
}