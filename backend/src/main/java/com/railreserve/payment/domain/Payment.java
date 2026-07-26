package com.railreserve.payment.domain;

import com.railreserve.booking.domain.Booking;
import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String gatewayRef;

    @CreationTimestamp
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(Booking booking, BigDecimal amount, PaymentStatus status, String gatewayRef) {
        this.booking = booking;
        this.amount = amount;
        this.status = status;
        this.gatewayRef = gatewayRef;
    }

    public Booking getBooking() {
        return booking;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getGatewayRef() {
        return gatewayRef;
    }

    public void setGatewayRef(String gatewayRef) {
        this.gatewayRef = gatewayRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
