package com.spzx.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.HttpStatus;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.order.domain.OrderLog;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;
import com.spzx.order.mapper.OrderInfoMapper;
import com.spzx.order.mapper.OrderItemMapper;
import com.spzx.order.mapper.OrderLogMapper;
import com.spzx.order.service.IOrderInfoService;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.vo.SkuLockVo;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.user.api.RemoteUserAddressService;
import com.spzx.user.domain.UserAddress;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Autowired
    private RemoteCartService remoteCartService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    private RemoteUserAddressService remoteUserAddressService;

    @Autowired
    RabbitService rabbitService; //来自于公共模块：spzx-common-rabbit

    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单
     */
    @Override
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo) {
        return orderInfoMapper.selectOrderInfoList(orderInfo);
    }

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    @Override
    public OrderInfo selectOrderInfoById(Long id) {
        OrderInfo orderInfo = orderInfoMapper.selectById(id);
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }

    /**
     * 去结算
     *
     * @return
     */
    @Override
    public TradeVo trade() {
        Long userId = SecurityContextHolder.getUserId();
        //1、远程调用购物车的微服务，获取勾选商品List<CartInfo>
        R<List<CartInfo>> cartCheckedListR = remoteCartService.getCartCheckedList(userId, SecurityConstants.INNER);
        if (cartCheckedListR.getCode() == HttpStatus.ERROR) {
            throw new ServiceException(cartCheckedListR.getMsg());
        }

        //购物车中被勾选的商品
        List<CartInfo> cartInfoList = cartCheckedListR.getData();
        if (CollectionUtils.isEmpty(cartInfoList)) {
            throw new ServiceException("请选择要结算的商品");
        }

        //2、将List<CartInfo>转换成List<OrderItem>
        List<OrderItem> orderItemList = cartInfoList.stream().map((cartInfo) -> {
            OrderItem orderItem = new OrderItem();
            BeanUtils.copyProperties(cartInfo, orderItem);
            return orderItem;
        }).toList();

        //3、计算总金额（商品总金额：不包括优惠卷和运费）
        BigDecimal totalAmount = new BigDecimal(0);
        for (OrderItem orderItem : orderItemList) {
            totalAmount = totalAmount.add(orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum())));
        }
        //4、生成订单交易流水号
        String tradeNo = getTradeNo(userId);
        TradeVo tradeVo = new TradeVo();
        tradeVo.setOrderItemList(orderItemList);
        tradeVo.setTotalAmount(totalAmount);
        tradeVo.setTradeNo(tradeNo);
        return tradeVo;
    }

    @Override
    @Transactional
    public Long submitOrder(OrderForm orderForm) {
        //1、去重校验(下订单的按钮不能重复点击)
        //前端实现去重：按钮点击后变为不可用
        //后端实现去重：利用缓存区交易的流水号去重。首次提交删除缓存流水号。如果再次删除（重复提交）删除失败，给与提示：您不能重复提交订单。
        String tradeNo = orderForm.getTradeNo();
        Long userId = SecurityContextHolder.getUserId();
        String tradeKey = "user:trade:" + userId;

        /*if(!redisTemplate.hasKey(tradeKey)){
            throw new ServiceException("下单超时，请回购购物车页面重新下单！");
        }*/

        String script = "if redis.call('get',KEYS[1]) == ARGV[1]\n" +
                "then \n" +
                "\treturn redis.call('del',KEYS[1])\n" +
                "else\n" +
                "\treturn 0\n" +
                "end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        Long result = (Long) redisTemplate.execute(redisScript, Arrays.asList(tradeKey), tradeNo);
        if (result == 0) {
            throw new ServiceException("您不能重复提交订单");
        }

        //2、检验数据不能为空
        List<OrderItem> orderItemList = orderForm.getOrderItemList();
        if (CollectionUtils.isEmpty(orderItemList)) {
            throw new ServiceException("订单项集合数据不能为空！");
        }
        //3、验证最新商品价格，价格不一致更新购物车价格
        //3.1 远程调用，批量获取商品最新价格
        List<Long> skuIdList = orderItemList.stream().map(OrderItem::getSkuId).toList();
        R<List<SkuPrice>> skuPriceListR = remoteProductService.getSkuPriceList(skuIdList, SecurityConstants.INNER);
        if (skuPriceListR.getCode() == HttpStatus.ERROR) {
            throw new ServiceException(skuPriceListR.getMsg());
        }
        List<SkuPrice> skuPriceList = skuPriceListR.getData();//数据库中最新的商品价格
        Map<Long, BigDecimal> skuIdToSkuPriceMap =
                skuPriceList.stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));
        StringBuilder builder = new StringBuilder("");
        for (OrderItem orderItem : orderItemList) {
            BigDecimal salePriceDB = skuIdToSkuPriceMap.get(orderItem.getSkuId());
            if (orderItem.getSkuPrice().compareTo(salePriceDB) != 0) {
                builder.append(orderItem.getSkuName()).append("价格变了；");//xxx价格变了；yyy价格变了
            }
        }
        if (StringUtils.hasText(builder.toString())) {
            //修改购物车价格，并抛出异常，终止下单。
            remoteCartService.updateCartPrice(userId, SecurityConstants.INNER);
            throw new ServiceException(builder.toString());
        }

        //4、检查库存并锁定库存    同步请求，必须锁定库存才能下单
        List<SkuLockVo> skuLockVoList = orderItemList.stream().map(orderItem -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(orderItem.getSkuId());
            skuLockVo.setSkuNum(orderItem.getSkuNum());
//            skuLockVo.setIsHaveStock(false);/默认是false
            return skuLockVo;
        }).toList();

        R<String> stringR = remoteProductService.checkAndLock(tradeNo,skuLockVoList,SecurityConstants.INNER);
        if(stringR.getCode()==HttpStatus.ERROR){
            throw new ServiceException(stringR.getMsg());
        }
        String resultStr = stringR.getData();
        if(StringUtils.hasText(resultStr)){
            throw new ServiceException(resultStr);
        }
        //5、保存订单数据（订单表、订单项表，订单日志表） tx(事务)
        Long orderId = null;
        try {
             orderId = this.saveOrder(orderForm);
        } catch (Exception e) {
            e.printStackTrace();
            //4.1 下单失败，解锁库存
            rabbitService.sendMessage(MqConst.EXCHANGE_PRODUCT, MqConst.ROUTING_UNLOCK, orderForm.getTradeNo());
            //8、解锁库存      异步(axios,CompletableFuture,@Async,@EnableAsync;mq)操作即可： 发送mq消息，解锁库存
            throw new ServiceException("下单失败");
        }
        //6、删除购物车选中商品
        remoteCartService.deleteCartCheckedList(userId, SecurityConstants.INNER);

        //7、发送延迟消息：30min后需要判断用户是否已经支付，没支付就需要取消订单，解锁库存
        //7.发送延迟消息,取消订单 (15分钟未支付，消费者就会进行关闭订单->解锁库存。)
        rabbitService.sendDealyMessage(MqConst.EXCHANGE_CANCEL_ORDER,
                MqConst.ROUTING_CANCEL_ORDER,
                String.valueOf(orderId), MqConst.CANCEL_ORDER_DELAY_TIME);

        //返回订单id
        return 1L;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processCloseOrder(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(null != orderInfo && orderInfo.getOrderStatus().intValue() == 0) { //  订单状态orderStatus=0 说明15分钟未支付。
            orderInfo.setOrderStatus(-1); //  -1 取消订单
            orderInfo.setCancelTime(new Date());
            orderInfo.setCancelReason("未支付自动取消");
            orderInfoMapper.updateById(orderInfo);

            //记录日志
            OrderLog orderLog = new OrderLog();
            orderLog.setOrderId(orderInfo.getId());
            orderLog.setProcessStatus(-1);
            orderLog.setNote("系统取消订单");
            orderLogMapper.insert(orderLog);

            //发送MQ消息通知商品系统解锁库存
            rabbitService.sendMessage(MqConst.EXCHANGE_PRODUCT, MqConst.ROUTING_UNLOCK, orderInfo.getOrderNo());
        }
    }

    /**
     * 根据订单号查询订单详情
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    @Override
    public OrderInfo getByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderInfo.getId()));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }

    /**
     * 辅助方法：保存订单数据（订单表、订单项表，订单日志表）
     * @param orderForm
     * @return
     */
    @Transactional
    public Long saveOrder(OrderForm orderForm) {
        //1、保存订单对象
        Long userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUserName();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(orderForm.getTradeNo());//交易流水号作为订单号
        orderInfo.setUserId(userId);
        orderInfo.setNickName(userName);//用登录的账号作为昵称。否则，需要远程调用接口获取昵称数据。
        orderInfo.setRemark(orderForm.getRemark());//备注
        UserAddress userAddress = remoteUserAddressService.getUserAddress(orderForm.getUserAddressId(), SecurityConstants.INNER).getData();
        orderInfo.setReceiverName(userAddress.getName());
        orderInfo.setReceiverPhone(userAddress.getPhone());
        orderInfo.setReceiverTagName(userAddress.getTagName());
        orderInfo.setReceiverProvince(userAddress.getProvinceCode());
        orderInfo.setReceiverCity(userAddress.getCityCode());
        orderInfo.setReceiverDistrict(userAddress.getDistrictCode());
        orderInfo.setReceiverAddress(userAddress.getFullAddress());

        List<OrderItem> orderItemList = orderForm.getOrderItemList();
        BigDecimal totalAmount = new BigDecimal(0);
        for (OrderItem orderItem : orderItemList) {
            totalAmount = totalAmount.add(orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum())));
        }
        orderInfo.setTotalAmount(totalAmount);//实付价格（减去优惠价格+运费）之后的钱
        orderInfo.setCouponAmount(new BigDecimal(0));//优惠价格   暂时业务不做
        orderInfo.setOriginalTotalAmount(totalAmount);//商品总价格 不包含优惠价格+运费 只包含商品的钱
        orderInfo.setFeightFee(orderForm.getFeightFee());//运费  默认免运维
        //OrderInfo类的orderStatus属性的类型改为Integer
        orderInfo.setOrderStatus(0);//订单状态：0->待付款；1->待发货；2->已发货；3->已完成；-1->已取消
        orderInfoMapper.insert(orderInfo);//主键回旋

        //2、保存多个订单项
        //orderItemList = orderForm.getOrderItemList();
        for (OrderItem orderItem : orderItemList) {
            orderItem.setOrderId(orderInfo.getId());//FK 外键
            orderItemMapper.insert(orderItem);
        }
        //3、保存订单日志
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderInfo.getId());
        orderLog.setProcessStatus(0);
        orderLog.setNote("提交订单");
        orderLog.setOperateUser("用户");
        orderLogMapper.insert(orderLog);

        /*try {
            TimeUnit.SECONDS.sleep(10);
            int i = 1/0;//解锁下单异常，解锁库存
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }*/

        return orderInfo.getId();
    }

    /**
     * 生成订单交易流水号
     * @param userId
     * @return
     */
    private String getTradeNo(Long userId) {
        String tradeKey = "user:trade:" + userId;
        String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
        //交易流水号，需要存储在Redis中，过期时间5min。
        redisTemplate.opsForValue().set(tradeKey, tradeNo, 5, TimeUnit.MINUTES);
        return tradeNo;
    }

}