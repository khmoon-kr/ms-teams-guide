package com.example.billingteams.controller;

import com.example.billingteams.domain.PaymentTransaction;
import com.example.billingteams.dto.AuthorizePaymentRequest;
import com.example.billingteams.dto.BillingEventResponse;
import com.example.billingteams.dto.CapturePaymentRequest;
import com.example.billingteams.dto.RefundPaymentRequest;
import com.example.billingteams.service.BillingOperationsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingEventController {

    private final BillingOperationsService billingService;

    public BillingEventController(BillingOperationsService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/events/authorize")
    public BillingEventResponse authorize(@Valid @RequestBody AuthorizePaymentRequest request) {
        return billingService.authorizePayment(request);
    }

    @PostMapping("/events/capture")
    public BillingEventResponse capture(@Valid @RequestBody CapturePaymentRequest request) {
        return billingService.capturePayment(request);
    }

    @PostMapping("/events/refund")
    public BillingEventResponse refund(@Valid @RequestBody RefundPaymentRequest request) {
        return billingService.refundPayment(request);
    }

    @GetMapping("/transactions")
    public List<PaymentTransaction> listTransactions() {
        return billingService.listTransactions();
    }

    @GetMapping("/transactions/{txId}")
    public PaymentTransaction getTransaction(@PathVariable String txId) {
        return billingService.getRequiredTransaction(txId);
    }
}
