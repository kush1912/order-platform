package com.orderplatform.order.application.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EtaCalculator {

    private final Executor etaExecutor;

    public EtaCalculator(@Qualifier("etaExecutor") Executor etaExecutor) {
        this.etaExecutor = etaExecutor;
    }

    public CompletableFuture<Integer> calculateAsync() {
        return CompletableFuture.supplyAsync(
                () -> ThreadLocalRandom.current().nextInt(3, 11),
                etaExecutor);
    }
}
