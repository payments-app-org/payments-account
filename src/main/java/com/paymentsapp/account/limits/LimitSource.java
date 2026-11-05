package com.paymentsapp.account.limits;

// К какому источнику относится лимит: к любому или только к кошельку / только к картам
public enum LimitSource {
    ANY,
    WALLET,
    CARD;

    public boolean covers(SourceKind source) {
        return this == ANY || name().equals(source.name());
    }
}
