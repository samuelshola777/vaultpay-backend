# VaultPay Backend

VaultPay is a secure digital-wallet REST API built with Java 21, Spring Boot, and PostgreSQL. Version 1 supports NGN, while wallet balances and ledger entries are designed for additional currencies.

## Highlights

- Email verification, password recovery, JWT authentication, and role-based access
- Automatic wallet provisioning and securely hashed transaction PINs
- Idempotent wallet transfers and withdrawals
- Double-entry ledger records for every completed money movement
- Pessimistic balance locking plus optimistic entity versioning
- Paystack funding, bank-account resolution, recipients, payouts, and signed webhooks
- Durable webhook deduplication, manual verification, and scheduled provider reconciliation
- Available/held balances for safe asynchronous withdrawals
- Refresh tokens, logout revocation, failed-login lockout, and public-endpoint rate limiting
- Configurable per-transaction and daily financial limits
- Notifications, activity logs, admin controls, Actuator, Swagger, Docker, and CI

## Requirements

- Java 21
- Maven 3.9+
- A Neon PostgreSQL database
- SMTP credentials
- Paystack test credentials

## Configuration

Set these environment variables before starting the application:

```text
DB_URL=jdbc:postgresql://YOUR_NEON_HOST/neondb?sslmode=require
DB_USERNAME=YOUR_NEON_USERNAME
DB_PASSWORD=YOUR_NEON_PASSWORD
PORT=8080
JWT_SECRET=REPLACE_WITH_A_BASE64_ENCODED_32_BYTE_SECRET
JWT_EXPIRATION_MS=3600000
JWT_REFRESH_EXPIRATION_MS=604800000
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=YOUR_MAIL_USERNAME
MAIL_PASSWORD=YOUR_MAIL_PASSWORD
MAIL_FROM=no-reply@example.com
OTP_EXPIRATION_MINUTES=10
PAYSTACK_BASE_URL=https://api.paystack.co
PAYSTACK_SECRET_KEY=sk_test_REPLACE_ME
PAYSTACK_CALLBACK_URL=http://localhost:3000/payment/callback
DEFAULT_ADMIN_EMAIL=admin@example.com
DEFAULT_ADMIN_PASSWORD=REPLACE_WITH_A_STRONG_PASSWORD
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

All transaction limits, reconciliation intervals, login lockout values, and rate limits are configurable in
`.env.example`. This project intentionally uses `spring.jpa.hibernate.ddl-auto=update`; Flyway is not used.

Never commit real credentials or a `.env` file.

## Run

```bash
./mvnw spring-boot:run
```

Health endpoint: `GET /api/v1/system/public/health`

Swagger UI: `/swagger-ui.html`

## Authentication endpoints

- `POST /api/v1/users/public/register`
- `POST /api/v1/users/public/verify-email`
- `POST /api/v1/users/public/resend-verification`
- `POST /api/v1/users/public/login`
- `POST /api/v1/users/public/refresh-token`
- `POST /api/v1/users/private/logout`
- `POST /api/v1/users/public/forgot-password`
- `POST /api/v1/users/public/reset-password`
- `GET /api/v1/users/private/profile`

### Password reset flow

1. Request a reset code. The response is intentionally the same whether or not the email exists.

```http
POST /api/v1/users/public/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

2. Submit the six-digit code from the email with the new password. The code expires after
   `OTP_EXPIRATION_MINUTES`, can be used once, and is invalidated when a newer code is requested.

```http
POST /api/v1/users/public/reset-password
Content-Type: application/json

{
  "email": "user@example.com",
  "otp": "123456",
  "newPassword": "NewStrongPassword1!",
  "confirmPassword": "NewStrongPassword1!"
}
```

The new password must be 8-72 characters and include uppercase, lowercase, numeric, and special characters.
Successful reset invalidates existing access and refresh tokens and clears any login lock.

## Wallet endpoints

- `GET /api/v1/wallets/private/me`
- `POST /api/v1/wallets/private/pin`
- `PUT /api/v1/wallets/private/pin`

An NGN wallet is created automatically after successful email verification. The balance model supports adding more currencies later.

## Financial endpoints

- `POST /api/v1/transfers/private/wallet`
- `GET /api/v1/transactions/private`
- `GET /api/v1/transactions/private/{reference}`
- `POST /api/v1/payments/private/fund`
- `POST /api/v1/payments/private/verify/{reference}`
- `POST /api/v1/payments/public/paystack/webhook`
- `GET /api/v1/beneficiaries/private/banks`
- `POST /api/v1/beneficiaries/private`
- `GET /api/v1/beneficiaries/private`
- `DELETE /api/v1/beneficiaries/private/{id}`
- `POST /api/v1/withdrawals/private`

Transfers and withdrawals require a unique `Idempotency-Key` request header.

## Notification and admin endpoints

- `GET /api/v1/notifications/private`
- `GET /api/v1/notifications/private/unread-count`
- `PUT /api/v1/notifications/private/{id}/read`
- `GET /api/v1/admin/private/dashboard`
- `GET /api/v1/admin/private/users`
- `GET /api/v1/admin/private/transactions`
- `PUT /api/v1/admin/private/users/{userId}/status`
- `PUT /api/v1/admin/private/wallets/{walletId}/status`
- `GET /api/v1/admin/private/transactions/{reference}`
- `GET /api/v1/admin/private/activity-logs`
- `POST /api/v1/admin/private/reconciliation/funding/{reference}`
- `POST /api/v1/admin/private/reconciliation/withdrawal/{reference}`

## Money movement model

Wallet transfers create equal debit and credit entries. Funding debits the Paystack clearing account and credits the wallet. Completed withdrawals debit the wallet and credit the payout-clearing and platform-fee accounts. Pending withdrawals move funds from available balance into held balance until a signed webhook confirms the outcome.

## Testing

```bash
./mvnw test
```

Tests use an H2 database in PostgreSQL compatibility mode. Provider calls should use Paystack test keys.

The scheduled reconciliation job safely rechecks old initialized funding attempts and processing withdrawals.
Exact duplicate Paystack payloads are recorded and processed once, while failed/stale events remain retryable.

## Production safeguards

- Never expose the Paystack webhook endpoint without HTTPS.
- Point Paystack to `/api/v1/payments/public/paystack/webhook` and keep signature verification enabled.
- Use unique `Idempotency-Key` values for transfers and withdrawals.
- Use a strong Base64 JWT secret and rotate credentials outside source control.
- Review `/actuator/health`, correlation IDs, activity logs, and reconciliation warnings after deployment.
- KYC is intentionally outside version 1 scope.

Use a Base64-encoded secret containing at least 32 random bytes for `JWT_SECRET`.

## Modules

- `userauthmgt` - identity and authentication
- `wallet` - wallets, currencies, balances, and PINs
- `transaction` and `ledger` - history and accounting records
- `transfer` - internal wallet transfers and idempotency
- `payment` - Paystack funding and webhook processing
- `beneficiary` and `withdrawal` - verified bank payouts
- `notification`, `activitylog`, and `admin` - operations and oversight
- `utils` - shared responses, exceptions, security, and email
