package com.xytgy.teamallbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 
 * @TableName order_item
 */
@TableName(value ="order_item")
@Data
public class OrderItem {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    private Long order_id;

    /**
     * 商品ID
     */
    private Long product_id;

    /**
     * 商品名称（快照）
     */
    private String product_name;

    /**
     * 商品价格（快照）
     */
    private BigDecimal product_price;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 小计金额
     */
    private BigDecimal total_amount;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        OrderItem other = (OrderItem) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getOrder_id() == null ? other.getOrder_id() == null : this.getOrder_id().equals(other.getOrder_id()))
            && (this.getProduct_id() == null ? other.getProduct_id() == null : this.getProduct_id().equals(other.getProduct_id()))
            && (this.getProduct_name() == null ? other.getProduct_name() == null : this.getProduct_name().equals(other.getProduct_name()))
            && (this.getProduct_price() == null ? other.getProduct_price() == null : this.getProduct_price().equals(other.getProduct_price()))
            && (this.getQuantity() == null ? other.getQuantity() == null : this.getQuantity().equals(other.getQuantity()))
            && (this.getTotal_amount() == null ? other.getTotal_amount() == null : this.getTotal_amount().equals(other.getTotal_amount()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getOrder_id() == null) ? 0 : getOrder_id().hashCode());
        result = prime * result + ((getProduct_id() == null) ? 0 : getProduct_id().hashCode());
        result = prime * result + ((getProduct_name() == null) ? 0 : getProduct_name().hashCode());
        result = prime * result + ((getProduct_price() == null) ? 0 : getProduct_price().hashCode());
        result = prime * result + ((getQuantity() == null) ? 0 : getQuantity().hashCode());
        result = prime * result + ((getTotal_amount() == null) ? 0 : getTotal_amount().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", order_id=").append(order_id);
        sb.append(", product_id=").append(product_id);
        sb.append(", product_name=").append(product_name);
        sb.append(", product_price=").append(product_price);
        sb.append(", quantity=").append(quantity);
        sb.append(", total_amount=").append(total_amount);
        sb.append("]");
        return sb.toString();
    }
}