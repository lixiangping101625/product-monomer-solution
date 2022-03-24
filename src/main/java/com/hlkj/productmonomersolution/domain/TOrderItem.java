package com.hlkj.productmonomersolution.domain;

import lombok.Data;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author Lixiangping
 * @createTime 2022年03月24日 18:30
 * @decription:
 */
@Data
@Entity
@Table(name = "t_order_item", schema = "chaomai", catalog = "")
public class TOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private int orderId;
    private Integer count;
    private int productId;
}
