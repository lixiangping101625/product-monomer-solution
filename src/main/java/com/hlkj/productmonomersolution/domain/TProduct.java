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
@Entity
@Table(name = "t_product", schema = "chaomai", catalog = "")
@Data
public class TProduct {
    @Id
    private int id;
    private String name;
    private BigDecimal price;
    private Integer count;

}
