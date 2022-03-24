package com.hlkj.productmonomersolution;

import com.hlkj.productmonomersolution.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;

@SpringBootTest
class ProductMonomerSolutionApplicationTests {

    @Autowired
    private OrderService orderService;

    private CountDownLatch cdl = new CountDownLatch(5);
    private CyclicBarrier cyclicBarrier = new CyclicBarrier(5);

    @Test
    void contextLoads() throws InterruptedException {
        ExecutorService es = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            es.execute(() -> {
                try {
                    cyclicBarrier.await();//等待，在某一时刻同时执行下面创建订单
                    orderService.orderPlace();//五个线程查到商品库存均为1，所以会创建5个订单！！！
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
                finally {
                    cdl.countDown();
                }
            });
        }
        cdl.await();//防止线程池关闭
        es.shutdown();
    }

}
