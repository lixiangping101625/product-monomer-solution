###数据表初始化脚本
    商品表：
    DROP TABLE IF EXISTS `t_product`;
    CREATE TABLE `t_product`  (
      `id` int(11) NOT NULL,
      `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
      `price` decimal(10, 2) NULL DEFAULT NULL,
      `count` int(11) NULL DEFAULT NULL COMMENT '数量',
      PRIMARY KEY (`id`) USING BTREE
    ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;
    
    -- ----------------------------
    -- Records of t_product
    -- ----------------------------
    INSERT INTO `t_product` VALUES (100100, '测试商品', 100.00, 1);
    
    订单表：
    DROP TABLE IF EXISTS `t_order`;
        CREATE TABLE `t_order`  (
          `id` int(11) NOT NULL AUTO_INCREMENT,
          `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
          `price` decimal(10, 2) NULL DEFAULT NULL,
          PRIMARY KEY (`id`) USING BTREE
        ) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;
     
    订单详情表：
    DROP TABLE IF EXISTS `t_order_item`;
    CREATE TABLE `t_order_item`  (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `order_id` int(11) NOT NULL,
      `count` int(11) NULL DEFAULT NULL,
      `product_id` int(11) NOT NULL,
      PRIMARY KEY (`id`) USING BTREE
    ) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;
    
### 超卖问题分析：
    1、场景模拟：
        使用CountDownLatch和CyclicBarrier模拟多个用户同时创建订单，代码和数据库均未加锁
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
        
    2、超卖现象：
        现象1：下单时直接修改库存（set count= XXX），出现商品库存扣减正常-0，有多个订单
            原因：查询到库存都为1，所以库存校验通过，新增了5个订单
        现象2：下单时采用库存增量修改库存（set count = count-XXX）的方式，出现商品库存为负数，有多个订单
            原因：查询到库存都为1，所以库存校验通过，新增了5个订单（update 是有行锁的，所以库存递减成了负数）
        
### 解决方案：加锁    
    超卖问题关键：并发校验库存，造成了库存充足的假象    
    解决思路：对库存校验和库存扣减进行统一加锁，使之成为原子性的操作。
             并发场景只有获得锁的线程进行上述原子性操作，操作完成后释放锁，再由其他获得该锁的线程进行该原子性操作。
             
    解决方案：Java关键字synchronized
        方法加锁 + @Transactional(rollbackFor = Exception.class) 测试事务：
            理想目标：只有当前线程释放锁后，其他线程才可以执行该方法
            结果：由于事务原因导致依然存在超卖现象！！
            原因：上一个事务未提交，导致下一个执行线程查询库存任然为1.
            
            @Transactional(rollbackFor = Exception.class)
            public synchronized void orderPlace() {
                //1、查询库存
                TProduct product = productRepository.getById(purchaseProductId);
                if (product==null)
                    throw new RuntimeException("商品不存在");
                Integer currentCount = product.getCount();
                //2、库存校验&修改库存
                if (purchaseProductCount > currentCount)
                    throw new RuntimeException("商品仅剩" + currentCount + " 件，无法购买~");
                Integer leftCount = currentCount - purchaseProductCount;
                //3、更新库存（如果采用增量方式修改商品库存，就会出现库存为负数的场景。这里是直接修改库存，所以尽管商品库存正常（0），但实际上是创建了5个订单，还是超卖）
                product.setCount(leftCount);
                TProduct saveProduct = productRepository.save(product);
                ...
                新增订单、新增订单详情
                ...
            }    
        
         方案1、"方法加锁synchronized + 手动事务" 解决才能真正解决超卖问题
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
            
         方案2： synchronized(){代码块：主要造成库存充足假象的代码块} + 手动事务  解决超卖问题：
             /**
             * synchronized(){代码块：主要造成库存充足假象的代码块} + 手动事务  解决超卖问题：
             *      synchronized (this){代码块} 和 synchronized (当前类对象属性){代码块} 一样，因为Springboot默认是单例的。这里示例就采用synchronized (this){代码块}的方式。
             *      synchronized (当前类){代码块} 最安全。因为类只有一个，而对象特殊情况下可能会创建多个，所以不是太安全。
             */
            public void orderPlace() {
                TProduct product = null;
                synchronized (this){
                    //手动开启事务！
                    TransactionStatus transactionStatus1 = transactionManager.getTransaction(transactionDefinition);
                    //1、查询库存
                    product = productRepository.getById(purchaseProductId);
                    if (product==null) {
                        //异常事务回滚
                        transactionManager.rollback(transactionStatus1);
                        throw new RuntimeException("商品不存在");
                    }
                    Integer currentCount = product.getCount();
                    //2、库存校验&修改库存
                    if (purchaseProductCount > currentCount) {
                        //异常事务回滚
                        transactionManager.rollback(transactionStatus1);
                        throw new RuntimeException("商品仅剩" + currentCount + " 件，无法购买~");
                    }
                    Integer leftCount = currentCount - purchaseProductCount;
                    //3、更新库存（如果采用增量方式修改商品库存，就会出现库存为负数的场景。这里是直接修改库存，所以尽管商品库存正常（0），但实际上是创建了5个订单，还是超卖）
                    product.setCount(leftCount);
                    TProduct saveProduct = productRepository.save(product);
        
                    transactionManager.commit(transactionStatus1);
                }
                /*
                 * 尽量避免事务嵌套！！！
                 */
                //手动开启事务！
                TransactionStatus transactionStatus = transactionManager.getTransaction(transactionDefinition);
                //4、新增订单
                TOrder order = new TOrder();
                order.setPrice(product.getPrice().multiply(BigDecimal.valueOf(purchaseProductCount)));
                order.setTitle("测试订单");
                TOrder saveOrder = orderRepository.save(order);
                //5、新增订单详情
                
        方案三：使用lock + 手动事务 解决超卖问题
            /**
             * 使用JDK1.5提供的可重入锁ReentrantLock + 手动事务 解决超卖问题：
             *      注意：
             *          1、使用lock锁需要手动释放锁
             *          2、强烈建议配合try..finally..使用
             *               lock.lock();
             *               try{
             *                   代码块
             *               }finally{
             *                   lock.unLock();
             *               }
             */
            private Lock lock = new ReentrantLock();
            public void orderPlace() {
                TProduct product = null;
        
                //加锁
                lock.lock();
                try {
                    //手动开启事务！
                    TransactionStatus transactionStatus1 = transactionManager.getTransaction(transactionDefinition);
                    //1、查询库存
                    product = productRepository.getById(purchaseProductId);
                    if (product==null) {
                        //异常事务回滚
                        transactionManager.rollback(transactionStatus1);
                        throw new RuntimeException("商品不存在");
                    }
                    Integer currentCount = product.getCount();
                    //2、库存校验&修改库存
                    if (purchaseProductCount > currentCount) {
                        //异常事务回滚
                        transactionManager.rollback(transactionStatus1);
                        throw new RuntimeException("商品仅剩" + currentCount + " 件，无法购买~");
                    }
                    Integer leftCount = currentCount - purchaseProductCount;
                    //3、更新库存（如果采用增量方式修改商品库存，就会出现库存为负数的场景。这里是直接修改库存，所以尽管商品库存正常（0），但实际上是创建了5个订单，还是超卖）
                    product.setCount(leftCount);
                    TProduct saveProduct = productRepository.save(product);
        
                    transactionManager.commit(transactionStatus1);
                } finally {
                    lock.unlock();
                }
        
                /*
                 * 精良避免事务嵌套！！！
                 */
                //手动开启事务！
                TransactionStatus transactionStatus = transactionManager.getTransaction(transactionDefinition);
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
        
        补充：以上解决方案其实是悲观锁方案，通俗的说就是让查询库存和修改库存在一个事务内进行，这样就可以保证操作的原子性。 
                乐观锁参考：
                    update Sku s 
                    set s.stock = s.stock-:count
                    where s.id=:id
                    and s.stock >= :count