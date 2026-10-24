# PulseLedger Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Strict Multi-User Isolation**: Every collection lives under `/users/{userId}/...` (`transactions`, `payment_methods`, `budgets`, `parsed_messages`). Only an authenticated user where `request.auth.uid == userId` and `incoming().userId == request.auth.uid` can read, create, update, or delete records in their subcollections.
2. **Immutable Ownership & Creation Metadata**: On `update`, `userId` and `createdAt` must strictly equal `existing().userId` and `existing().createdAt`.
3. **Strict Schema & Bounds Validation**: Every `create` and `update` operation must pass `isValidTransaction`, `isValidPaymentMethod`, `isValidCategoryBudget`, or `isValidParsedMessage` with strict `.keys().hasAll(...)`, `.keys().hasOnly(...)`, string length bounds, enum validation, integer range checks, and server timestamp bounds (`<= request.time`).

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read/Write**: `request.auth == null` attempting to list `/users/alice/transactions` -> REJECTED.
2. **Cross-User Read/Write**: User `bob` attempting to read or write `/users/alice/transactions/tx1` -> REJECTED.
3. **Identity Spoofing in Body**: User `alice` writing to `/users/alice/transactions/tx1` with `{"userId": "bob", ...}` -> REJECTED.
4. **Shadow/Ghost Field Injection**: Adding `"isVerifiedAdmin": true` to a transaction or budget -> REJECTED by `hasOnly()`.
5. **Negative or Zero Transaction Amount**: Writing `amountCents: -500` or `0` -> REJECTED (`amountCents > 0`).
6. **Invalid Enum Value**: Writing `status: "HACKED"` or `type: "WIRE"` -> REJECTED.
7. **Oversized String ("Denial of Wallet")**: Writing a 5,000-character string into `title` or `notes` -> REJECTED by `.size()` bounds.
8. **Path ID Poisoning**: Using invalid characters or >128 chars in `{transactionId}` -> REJECTED by `isValidId()`.
9. **Immutable `createdAt` Tampering on Update**: Modifying `createdAt` during an update -> REJECTED by `incoming().createdAt == existing().createdAt`.
10. **Immutable `userId` Reassignment on Update**: Changing `userId` on an existing payment method -> REJECTED by `incoming().userId == existing().userId`.
11. **Future Timestamp Spoofing**: Submitting a future client timestamp `createdAt > request.time` -> REJECTED by `<= request.time`.
12. **Invalid Budget Threshold**: Setting `alertThresholdPercent: 150` or `0` -> REJECTED (`10 <= alertThresholdPercent <= 100`).
