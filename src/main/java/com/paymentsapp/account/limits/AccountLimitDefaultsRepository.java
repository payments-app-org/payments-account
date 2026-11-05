package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLimitDefaultsRepository extends JpaRepository<AccountLimitDefaults, AccountType> {
}
