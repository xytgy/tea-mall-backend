package com.xytgy.teamallbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 
 * @TableName orders
 */
@TableName(value ="orders")
@Data
public class Orders {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String order_no;

    /**
     * 用户ID
     */
    private Long user_id;

    /**
     * 总金额
     */
    private BigDecimal total_amount;

    /**
     * 状态 0待支付 1已支付 2已发货 3已完成 4已取消
     */
    private Integer status;

    /**
     * 收货人
     */
    private String receiver_name;

    /**
     * 手机号
     */
    private String receiver_phone;

    /**
     * 地址
     */
    private String receiver_address;

    /**
     * 下单时间
     */
    private LocalDateTime create_time;

    /**
     * 支付时间
     */
    private LocalDateTime pay_time;

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
        Orders other = (Orders) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getOrder_no() == null ? other.getOrder_no() == null : this.getOrder_no().equals(other.getOrder_no()))
            && (this.getUser_id() == null ? other.getUser_id() == null : this.getUser_id().equals(other.getUser_id()))
            && (this.getTotal_amount() == null ? other.getTotal_amount() == null : this.getTotal_amount().equals(other.getTotal_amount()))
            && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
            && (this.getReceiver_name() == null ? other.getReceiver_name() == null : this.getReceiver_name().equals(other.getReceiver_name()))
            && (this.getReceiver_phone() == null ? other.getReceiver_phone() == null : this.getReceiver_phone().equals(other.getReceiver_phone()))
            && (this.getReceiver_address() == null ? other.getReceiver_address() == null : this.getReceiver_address().equals(other.getReceiver_address()))
            && (this.getCreate_time() == null ? other.getCreate_time() == null : this.getCreate_time().equals(other.getCreate_time()))
            && (this.getPay_time() == null ? other.getPay_time() == null : this.getPay_time().equals(other.getPay_time()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getOrder_no() == null) ? 0 : getOrder_no().hashCode());
        result = prime * result + ((getUser_id() == null) ? 0 : getUser_id().hashCode());
        result = prime * result + ((getTotal_amount() == null) ? 0 : getTotal_amount().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getReceiver_name() == null) ? 0 : getReceiver_name().hashCode());
        result = prime * result + ((getReceiver_phone() == null) ? 0 : getReceiver_phone().hashCode());
        result = prime * result + ((getReceiver_address() == null) ? 0 : getReceiver_address().hashCode());
        result = prime * result + ((getCreate_time() == null) ? 0 : getCreate_time().hashCode());
        result = prime * result + ((getPay_time() == null) ? 0 : getPay_time().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", order_no=").append(order_no);
        sb.append(", user_id=").append(user_id);
        sb.append(", total_amount=").append(total_amount);
        sb.append(", status=").append(status);
        sb.append(", receiver_name=").append(receiver_name);
        sb.append(", receiver_phone=").append(receiver_phone);
        sb.append(", receiver_address=").append(receiver_address);
        sb.append(", create_time=").append(create_time);
        sb.append(", pay_time=").append(pay_time);
        sb.append("]");
        return sb.toString();
    }
}