package com.hlkj.productmonomersolution.repository;

import com.hlkj.productmonomersolution.domain.TOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<TOrderItem, Integer> {

}