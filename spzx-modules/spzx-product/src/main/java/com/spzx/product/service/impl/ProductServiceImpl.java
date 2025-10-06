package com.spzx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.core.utils.bean.BeanUtils;
import com.spzx.product.api.domain.Product;
import com.spzx.product.api.domain.ProductDetails;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuLockVo;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.product.api.domain.vo.SkuQuery;
import com.spzx.product.api.domain.vo.SkuStockVo;
import com.spzx.product.domain.SkuStock;
import com.spzx.product.mapper.ProductDetailsMapper;
import com.spzx.product.mapper.ProductMapper;
import com.spzx.product.mapper.ProductSkuMapper;
import com.spzx.product.mapper.SkuStockMapper;
import com.spzx.product.service.IProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品Service业务层处理
 */
@Slf4j
@Service
@Transactional
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private ProductDetailsMapper productDetailsMapper;

    @Autowired
    private SkuStockMapper skuStockMapper;

    //@Autowired
    //private StringRedisTemplate stringRedisTemplate; //适合 key和value都是字符串类型

    @Autowired
    private RedisTemplate redisTemplate; //适合值是任意类型

    /**
     * 查询商品列表
     *
     * @param product 商品
     * @return 商品
     */
    @Override
    public List<Product> selectProductList(Product product) {
        return productMapper.selectProductList(product);
    }

    //原子性
    @Override
    public int insertProduct(Product product) {
        //1.保存Product对象到product表
        productMapper.insert(product); //主键回填

        //2.保存List<ProductSku>对象到product_sku表
        List<ProductSku> productSkuList = product.getProductSkuList();
        if (CollectionUtils.isEmpty(productSkuList)) {
            throw new ServiceException("SKU数据为空");
        }
        int size = productSkuList.size();
        for (int i = 0; i < size; i++) {
            ProductSku productSku = productSkuList.get(i);
            productSku.setSkuCode(product.getId() + "_" + i);
            productSku.setSkuName(product.getName() + " " + productSku.getSkuSpec());
            productSku.setProductId(product.getId());
            productSkuMapper.insert(productSku);

            //添加商品库存  //3.保存List<SkuStock>对象到sku_stock表
            SkuStock skuStock = new SkuStock();
            skuStock.setSkuId(productSku.getId());
            skuStock.setTotalNum(productSku.getStockNum());
            skuStock.setLockNum(0);
            skuStock.setAvailableNum(productSku.getStockNum());
            skuStock.setSaleNum(0);
            skuStockMapper.insert(skuStock);
        }

        //4.保存ProductDetails对象到product_details表
        ProductDetails productDetails = new ProductDetails();
        productDetails.setImageUrls(String.join(",", product.getDetailsImageUrlList()));
        productDetails.setProductId(product.getId());
        productDetailsMapper.insert(productDetails);

        return 1;
    }


    @Override
    public Product selectProductById(Long id) {
        //1.根据id查询Product对象
        Product product = productMapper.selectById(id);

        //2.封装扩展字段：查询商品对应多个List<ProductSku>
        //select * from product_sku where product_id =?
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        List<Long> productSkuIdList = productSkuList.stream().map(productSku -> productSku.getId()).toList();


        // select * from sku_stock where sku_id in (1,2,3,4,5,6)
        List<SkuStock> skuStockList = skuStockMapper.selectList(new LambdaQueryWrapper<SkuStock>().in(SkuStock::getSkuId, productSkuIdList));

        Map<Long, Integer> skuIdToTatalNumMap = skuStockList.stream().collect(Collectors.toMap(SkuStock::getSkuId, SkuStock::getTotalNum));
        productSkuList.forEach(productSku -> {
            //返回ProductSku对象，携带了库存数据；
            productSku.setStockNum(skuIdToTatalNumMap.get(productSku.getId()));
        });

        product.setProductSkuList(productSkuList);

        //3.封装扩展字段：商品详情图片List<String>
        ProductDetails productDetails = productDetailsMapper.selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, id));
        String imageUrls = productDetails.getImageUrls();   //url,url,url
        String[] urls = imageUrls.split(",");
        product.setDetailsImageUrlList(Arrays.asList(urls));
        //返回Product对象
        return product;
    }


    @Override
    public int updateProduct(Product product) {
        //1.更新Product
        productMapper.updateById(product);

        //2.更新SKU   List<ProductSku>
        List<ProductSku> productSkuList = product.getProductSkuList();
        if (CollectionUtils.isEmpty(productSkuList)) {
            throw new ServiceException("SKU数据为空");
        }
        productSkuList.forEach(productSku -> {
            productSkuMapper.updateById(productSku);

            //3.更新库存   List<ProductSku> -> 获取扩展字段stockNum
            SkuStock skuStock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>().eq(SkuStock::getSkuId, productSku.getId()));
            skuStock.setTotalNum(productSku.getStockNum());
            skuStock.setAvailableNum(skuStock.getTotalNum() - skuStock.getLockNum());
            skuStockMapper.updateById(skuStock);
        });

        //4.更新详情ProductDetails
        ProductDetails productDetails = productDetailsMapper
                .selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, product.getId()));
        productDetails.setImageUrls(String.join(",", product.getDetailsImageUrlList()));
        productDetailsMapper.updateById(productDetails);

        return 1;
    }


    @Override
    public int deleteProductByIds(Long[] ids) {
        //1.删除Product表数据
        // delete from product where id in (1,2)
        productMapper.deleteBatchIds(Arrays.asList(ids));

        //redisTemplate.opsForValue().setBit(key,productSku.getId(),false);怎么使用循环删除
        for (Long id : ids) {
            if (id != null) {  // 修正：只有当id不为null时才处理
                // 根据商品ID查询其所有的SKU
                List<ProductSku> productSkuList = productSkuMapper.selectList(
                        new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id)
                );

                // 将每个SKU在Redis位图中标识为不可用
                for (ProductSku productSku : productSkuList) {
                    redisTemplate.opsForValue().setBit("product:sku:data", productSku.getId(), false);
                }
            }
        }

        //2.删除ProductSku表数据
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId, Arrays.asList(ids)));
        List<Long> productSkuIdList = productSkuList.stream().map(ProductSku::getId).toList();
        productSkuMapper.deleteBatchIds(productSkuIdList);

        //3.删除SkuStock表数据
        skuStockMapper.delete(new LambdaQueryWrapper<SkuStock>().in(SkuStock::getSkuId, productSkuIdList));

        //4.删除ProductDetails表数据
        // delete from product_details where product_id in (1,2)
        productDetailsMapper.delete(new LambdaQueryWrapper<ProductDetails>().in(ProductDetails::getProductId, Arrays.asList(ids)));
        return 1;
    }


    @Override
    public void updateAuditStatus(Long id, Integer auditStatus) {
        Product product = new Product();
        product.setId(id);
        if (auditStatus == 1) {
            product.setAuditStatus(1);
            product.setAuditMessage("审批通过");
        } else {
            product.setAuditStatus(-1);
            product.setAuditMessage("审批拒绝");
        }
        productMapper.updateById(product);
    }

    //下架
    @Override
    public void updateStatus(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);

        //根据spu对象id，查询sku集合
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        String key = "product:sku:data";

        if (status == 1) {
            product.setStatus(1);
            for (ProductSku productSku : productSkuList) {
                redisTemplate.opsForValue().setBit(key, productSku.getId(), true);
            }
        } else {
            product.setStatus(-1);
            for (ProductSku productSku : productSkuList) {
                redisTemplate.opsForValue().setBit(key, productSku.getId(), false);
            }
        }
        productMapper.updateById(product);
    }


    @Override
    public List<ProductSku> getTopSale() {
        return productSkuMapper.getTopSale();
    }


    @Override
    public List<ProductSku> skuList(SkuQuery skuQuery) {
        return productSkuMapper.skuList(skuQuery);
    }


    /**
     * 服务提供者：6个接口来服务于商品详情查询。需要进行优化，提供查询效率。
     * 需要使用redis来提高性能。
     */

    @Override
    public ProductSku getProductSku(Long skuId) {
        try {
            ProductSku productSku = null;
            String dataKey = "product:sku:" + skuId;
            //1、先判断缓存有没有
            if (redisTemplate.hasKey(dataKey)) {//缓存有
                //2、缓存有从缓存取并返回
                productSku = (ProductSku) redisTemplate.opsForValue().get(dataKey);
                System.out.println(Thread.currentThread().getName() + "从缓存中获取 productSku" + productSku);
                return productSku;
            } else {//缓存没有
                //解决缓存击穿问题 - 分布式锁
                String lockKey = "product:sku:lock:" + skuId;
                //设置uuid值，防止死锁
                String lockValue = UUID.randomUUID().toString().replaceAll("-", "");
                //加上分布式锁并设置过期时间
                Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 1, TimeUnit.MINUTES);//避免死锁
                //判断锁是否加成功
                if (isLocked) {
                    try {
                        //3、缓存没有从数据库取数据，存放到缓存并返回
                        productSku = getSkuDataFromDB(skuId);
                        System.out.println(Thread.currentThread().getName() + "从数据库中获取 productSku" + productSku);
                        //加上随机时间是为了解决缓存雪崩问题 - 增加随机过期时间：为了不能让它同时过期
                        //解决缓存穿透：1、查询不到数据，即使是null也存储一份，避免同一个key的值继续穿透到数据库。2、布隆过滤器解决/bitmap
                        if (productSku == null) {
                            redisTemplate.opsForValue().set(dataKey, productSku, 10 + new Random().nextInt(10), TimeUnit.MINUTES);
                        } else {
                            redisTemplate.opsForValue().set(dataKey, productSku, 60 + new Random().nextInt(10), TimeUnit.MINUTES);
                        }
                    } finally {
                        //避免释放他人的锁 - lua脚本解决
                        String script = "if redis.call('get',KEYS[1]) == ARGV[1]\n" +
                                "then \n" +
                                "\treturn redis.call('del',KEYS[1])\n" +
                                "else\n" +
                                "\treturn 0\n" +
                                "end";
                        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                        redisScript.setScriptText(script);
                        redisScript.setResultType(Long.class);
                        //这里如果使用的是StringRedisTemplate，就不用强转了
                        //Long result = (Long) redisTemplate.execute(redisScript, Arrays.asList(lockKey, lockValue));
                        redisTemplate.execute(redisScript, Arrays.asList(lockKey, lockValue));
                    }
                } else {//加锁没有成功，就要自旋
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //自旋就是递归调用整个方法本身
                    System.out.println(Thread.currentThread().getName() + "没抢到锁自旋-------");
                    return getProductSku(skuId);//睡一觉，自旋一次 - 递归调用
                }
            }
            return productSku;
        } catch (Exception e) {
            log.error(e.getMessage());
            return getSkuDataFromDB(skuId);//兜底的方法，有异常直接查询数据库了。
        }
    }

    private ProductSku getSkuDataFromDB(Long skuId) {
        return productSkuMapper.selectById(skuId);
    }


    @Override
    public Product getProduct(Long id) {
        return productMapper.selectById(id);
    }


    @Override
    public SkuPrice getSkuPrice(Long skuId) {
        ProductSku productSku = productSkuMapper.selectOne(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getId, skuId).select(ProductSku::getSalePrice, ProductSku::getMarketPrice));
        SkuPrice skuPrice = new SkuPrice();
        BeanUtils.copyProperties(productSku, skuPrice);
        return skuPrice;
    }


    @Override
    public ProductDetails getProductDetails(Long id) {
        return productDetailsMapper.selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, id));
    }


    @Override
    public Map<String, Long> getSkuSpecValue(Long id) {
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id).select(ProductSku::getId, ProductSku::getSkuSpec));
        Map<String, Long> skuSpecValueMap = new HashMap<>();
        productSkuList.forEach(item -> {
            skuSpecValueMap.put(item.getSkuSpec(), item.getId());
        });
        return skuSpecValueMap;
    }


    @Override
    public SkuStockVo getSkuStock(Long skuId) {
        SkuStock skuStock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>().eq(SkuStock::getSkuId, skuId));
        SkuStockVo skuStockVo = new SkuStockVo();
        BeanUtils.copyProperties(skuStock, skuStockVo);
        return skuStockVo;
    }


    // select * from product_sku where id in (1,2,3)
    // select id,sale_price,market_price from product_sku where id in (1,2,3)
    @Override
    public List<SkuPrice> getSkuPriceList(List<Long> skuIdList) {
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getId, skuIdList)
                .select(ProductSku::getId, ProductSku::getSalePrice, ProductSku::getMarketPrice));
        List<SkuPrice> skuPriceList = productSkuList.stream().map((productSku) -> {
            SkuPrice skuPrice = new SkuPrice();
            skuPrice.setSkuId(productSku.getId());
            skuPrice.setSalePrice(productSku.getSalePrice());
            skuPrice.setMarketPrice(productSku.getMarketPrice());
            return skuPrice;
        }).collect(Collectors.toList());
        return skuPriceList;
    }

    /**
     * 检查与锁定库存
     *
     * @param orderNo       订单号
     * @param skuLockVoList 需要锁定的库存商品信息
     * @return 是否锁定成功。空串表示成功，非空表示失败。
     */
    @Transactional
    @Override
    public String checkAndLock(String orderNo, List<SkuLockVo> skuLockVoList) {
        //1、去重  openfeign远程调用去重，可能重试
        //通过分布式锁来解决去重
        String lockKey = "sku:checkAndLock:" + orderNo;
        String dataKey = "sku:lock:data:" + orderNo;//锁定库存数据的缓存key
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, orderNo, 1, TimeUnit.HOURS);
        if (!isLocked) {
            if (redisTemplate.hasKey(dataKey)) {
                return "";//重复请求，不用再执行库存锁定，直接返回成功。
            } else {
                return "重复提交";
            }
        }
        //2、检查库存
        for (SkuLockVo skuLockVo : skuLockVoList) {
            SkuStock skuStock = skuStockMapper.check(skuLockVo.getSkuId(), skuLockVo.getSkuNum());//查询库存数据，增加数据库行锁  for update
            if (skuStock == null) {
                skuLockVo.setIsHaveStock(false);
            } else {
                skuLockVo.setIsHaveStock(true);
            }
        }
        if (skuLockVoList.stream().anyMatch((skuLockVo) -> !skuLockVo.getIsHaveStock())) {
            StringBuilder builder = new StringBuilder();
            //2.1 只要有一个商品库存不够取消事务回滚，不在进行库存锁定
            List<SkuLockVo> noHasStockList = skuLockVoList.stream().filter((skuLockVo) -> !skuLockVo.getIsHaveStock()).toList();
            for (SkuLockVo skuLockVo : noHasStockList) {
                builder.append("商品【" + skuLockVo.getSkuId() + "】库存不多");
            }
            redisTemplate.delete(lockKey);
            return builder.toString();//非空
        } else {
            //2.2 所有商品库存够才会锁库存
            for (SkuLockVo skuLockVo : skuLockVoList) {
                int count = skuStockMapper.lock(skuLockVo.getSkuId(), skuLockVo.getSkuNum());
                if (count == 0) {
                    redisTemplate.delete(lockKey);
                    //假设存在锁库存失败的情况，事务回滚
                    throw new ServiceException("锁库存失败");
                }
            }
        }
        //3、将锁定库存数据保存到缓存中，用于后期  【解锁库存】  或  【减库存】 使用。
        redisTemplate.opsForValue().set(dataKey, skuLockVoList);//不需要设置过期时间，什么时候清理缓存：解锁库存或减库存会删除缓存
        return "";//成功字符串
    }

    /**
     * 解锁库存
     *
     * @param orderNo
     */
    @Transactional
    @Override
    public void unlock(String orderNo) {
        //去重：消息幂等性处理
        String key = "sku:unlock:" + orderNo;
        String dataKey = "sku:lock:data:" + orderNo;
        //业务去重，防止重复消费
        Boolean isExist = redisTemplate.opsForValue().setIfAbsent(key, orderNo, 1, TimeUnit.HOURS);
        if(!isExist) return;

        // 获取锁定库存的缓存信息
        List<SkuLockVo> skuLockVoList = (List<SkuLockVo>)this.redisTemplate.opsForValue().get(dataKey);
        if (CollectionUtils.isEmpty(skuLockVoList)){
            return ;//缓存没了  - 有可能已经支付，并减库存了，删除了缓存。
        }

        // 解锁库存
        skuLockVoList.forEach(skuLockVo -> {
            int row = skuStockMapper.unlock(skuLockVo.getSkuId(), skuLockVo.getSkuNum());
            if(row == 0) {
                //解除去重
                this.redisTemplate.delete(key);//删除分布式锁
                throw new ServiceException("解锁出库失败");//事务回滚
            }
        });

        // 解锁库存之后，删除锁定库存的缓存。以防止重复解锁库存
        this.redisTemplate.delete(dataKey);
    }

    /**
     * 扣减库存
     *
     * @param orderNo
     */
    @Override
    public void minus(String orderNo) {
        String key = "sku:minus:" + orderNo;
        String dataKey = "sku:lock:data:" + orderNo;
        //业务去重，防止重复消费
        Boolean isExist = redisTemplate.opsForValue().setIfAbsent(key, orderNo, 1, TimeUnit.HOURS);
        if(!isExist) return;

        // 获取锁定库存的缓存信息
        List<SkuLockVo> skuLockVoList = (List<SkuLockVo>)this.redisTemplate.opsForValue().get(dataKey);
        if (CollectionUtils.isEmpty(skuLockVoList)){
            return ;//有可能解锁完库存，清理掉缓存。  也有可能1小时后分布式失效后，又过来重复消息。
        }

        // 减库存
        skuLockVoList.forEach(skuLockVo -> {
            int row = skuStockMapper.minus(skuLockVo.getSkuId(), skuLockVo.getSkuNum());
            if(row == 0) {
                //解除去重
                this.redisTemplate.delete(key);
                throw new ServiceException("减出库失败");
            }
        });

        // 解锁库存之后，删除锁定库存的缓存。以防止重复解锁库存
        this.redisTemplate.delete(dataKey);
    }

}