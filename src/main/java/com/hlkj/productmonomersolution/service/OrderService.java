package com.hlkj.productmonomersolution.service;

import com.hlkj.productmonomersolution.domain.TOrder;
import com.hlkj.productmonomersolution.domain.TOrderItem;
import com.hlkj.productmonomersolution.domain.TProduct;
import com.hlkj.productmonomersolution.repository.OrderItemRepository;
import com.hlkj.productmonomersolution.repository.OrderRepository;
import com.hlkj.productmonomersolution.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * @author Lixiangping
 * @createTime 2022年03月24日 18:40
 * @decription: 订单服务
 */
@Service
public class OrderService {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

    private int purchaseProductId = 100100;
    private int purchaseProductCount = 1;

    ///////////////////////////////////////////////////////////////////////////////////////
    /////////在没有加锁的情况下，5个线程同时执行，查询到商品库存均为1，所以会创建5个订单
    //      一定要注意：使用synchronized关键字对方法加锁时，一定要把事务提交包含在内，不然在下一个线程执行该方法时，
    //                   上一个线程的事务可能没提交操作，导致超卖。所以一定要使用手动事务！！！！
    ///////////////////////////////////////////////////////////////////////////////////////

//    @Transactional(rollbackFor = Exception.class)
//    public synchronized void orderPlace() {
//        //1、查询库存
//        TProduct product = productRepository.getById(purchaseProductId);
//        if (product==null)
//            throw new RuntimeException("商品不存在");
//        Integer currentCount = product.getCount();
//        //2、库存校验&修改库存
//        if (purchaseProductCount > currentCount)
//            throw new RuntimeException("商品仅剩" + currentCount + " 件，无法购买~");
//        Integer leftCount = currentCount - purchaseProductCount;
//        //3、更新库存（如果采用增量方式修改商品库存，就会出现库存为负数的场景。这里是直接修改库存，所以尽管商品库存正常（0），但实际上是创建了5个订单，还是超卖）
//        product.setCount(leftCount);
//        TProduct saveProduct = productRepository.save(product);
//
//        //4、新增订单
//        TOrder order = new TOrder();
//        order.setPrice(product.getPrice().multiply(BigDecimal.valueOf(purchaseProductCount)));
//        order.setTitle("测试订单");
//        TOrder saveOrder = orderRepository.save(order);
//        //5、新增订单详情
//        TOrderItem orderItem = new TOrderItem();
//        orderItem.setCount(purchaseProductCount);
//        orderItem.setOrderId(saveOrder.getId());
//        orderItem.setProductId(purchaseProductId);
//        TOrderItem saveOrderItem = orderItemRepository.save(orderItem);
//
//        System.out.println("下单成功，订单id：" + saveOrder.getId());
//    }


    /////////////////////////////////////////////////////////////
    //////synchronized + 手动事务 实现并发场景对资源加锁
    ////////////////////////////////////////////////////////////

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    TransactionDefinition transactionDefinition;

    public synchronized void orderPlace() {
        //手动开启事务！
        TransactionStatus transactionStatus = transactionManager.getTransaction(transactionDefinition);
        //1、查询库存
        TProduct product = productRepository.getById(purchaseProductId);
        if (product==null) {
            //事务回滚
            transactionManager.rollback(transactionStatus);
            throw new RuntimeException("商品不存在");
        }
        Integer currentCount = product.getCount();
        //2、库存校验&修改库存
        if (purchaseProductCount > currentCount) {
            //事务回滚
            transactionManager.rollback(transactionStatus);
            throw new RuntimeException("商品仅剩" + currentCount + " 件，无法购买~");
        }
        Integer leftCount = currentCount - purchaseProductCount;
        //3、更新库存（如果采用增量方式修改商品库存，就会出现库存为负数的场景。这里是直接修改库存，所以尽管商品库存正常（0），但实际上是创建了5个订单，还是超卖）
        product.setCount(leftCount);
        TProduct saveProduct = productRepository.save(product);

        //4、新增订单
        TOrder order = new TOrder();
        order.setPrice(product.getPrice().multiply(BigDecimal.valueOf(purchaseProductCount)));
        order.setTitle("测试订单");
        TOrder saveOrder = orderRepository.save(order);
        //5、新增订单详情
        TOrderItem orderItem = new TOrderItem();
        orderItem.setCount(purchaseProductCount);
        orderItem.setOrderId(saveOrder.getId());
        orderItem.setProductId(purchaseProductId);
        TOrderItem saveOrderItem = orderItemRepository.save(orderItem);

        System.out.println("下单成功，订单id：" + saveOrder.getId());

        //提交事务
        transactionManager.commit(transactionStatus);
    }

}
