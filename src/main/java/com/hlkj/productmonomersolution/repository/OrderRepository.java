package com.hlkj.productmonomersolution.repository;

import com.hlkj.productmonomersolution.domain.TOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<TOrder, Integer> {

}