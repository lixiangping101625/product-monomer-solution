package com.hlkj.productmonomersolution.repository;

import com.hlkj.productmonomersolution.domain.TProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<TProduct, Integer> {

}