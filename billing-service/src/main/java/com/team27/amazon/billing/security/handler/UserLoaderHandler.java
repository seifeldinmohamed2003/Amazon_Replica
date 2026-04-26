package com.team27.amazon.billing.security.handler;

import com.team27.amazon.billing.repository.TransactionRepository;

public class UserLoaderHandler extends AuthHandler {

    private final TransactionRepository transactionRepository;

    public UserLoaderHandler(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public String handle(AuthContext ctx) {
        Long userId = ctx.getUserId();
        if (userId == null || transactionRepository.countUserById(userId) == 0) {
            return "401:User not found";
        }
        return passToNext(ctx);
    }
}
