package com.hlkj.productmonomersolution.domain;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * @author Lixiangping
 * @createTime 2022年03月24日 18:30
 * @decription:
 */
@Data
@Entity
@Table(name = "t_order", schema = "chaomai", catalog = "")
public class TOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String title;
    private BigDecimal price;

}
