package com.vaultpay.payment;

import tools.jackson.databind.JsonNode;
import com.vaultpay.utils.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaystackClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.paystack.base-url}")
    private String baseUrl;

    @Value("${app.paystack.secret-key}")
    private String secretKey;

    @Value("${app.paystack.callback-url}")
    private String paystackCallbackUrl;

    public JsonNode initializeTransaction(
            String email,
            long amountInKobo,
            String reference
    ) {
        Map<String, Object> payload = Map.of(
                "email", email,
                "amount", amountInKobo,
                "currency", "NGN",
                "reference", reference,
                "callback_url", paystackCallbackUrl
        );

        return post("/transaction/initialize", payload);
    }

    public JsonNode resolveAccount(String accountNumber, String bankCode) {
        return restClientBuilder.baseUrl(baseUrl).build().get()
                .uri(uri -> uri.path("/bank/resolve")
                        .queryParam("account_number", accountNumber)
                        .queryParam("bank_code", bankCode).build())
                .header("Authorization", "Bearer " + secretKey)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode listNigerianBanks() {
        JsonNode response = restClientBuilder.baseUrl(baseUrl).build().get()
                .uri(uri -> uri.path("/bank")
                        .queryParam("country", "nigeria")
                        .queryParam("currency", "NGN")
                        .queryParam("type", "nuban")
                        .build())
                .header("Authorization", "Bearer " + secretKey)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("status").asBoolean(false)) {
            throw new InvalidOperationException("Bank list could not be retrieved");
        }
        return response;
    }

    public JsonNode createTransferRecipient(String name, String accountNumber, String bankCode) {
        return post("/transferrecipient", Map.of(
                "type", "nuban",
                "name", name,
                "account_number", accountNumber,
                "bank_code", bankCode,
                "currency", "NGN"
        ));
    }

    public JsonNode initiateTransfer(long amountInKobo, String recipientCode, String reference, String reason) {
        return post("/transfer", Map.of(
                "source", "balance",
                "amount", amountInKobo,
                "recipient", recipientCode,
                "reference", reference,
                "reason", reason
        ));
    }

    public JsonNode verifyTransaction(String reference) {
        return get("/transaction/verify/" + reference);
    }

    public JsonNode verifyTransfer(String reference) {
        return get("/transfer/verify/" + reference);
    }

    public boolean isValidWebhook(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] provided = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception exception) {
            return false;
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        JsonNode response = restClientBuilder.baseUrl(baseUrl).build().post()
                .uri(path)
                .header("Authorization", "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("status").asBoolean(false)) {
            throw new InvalidOperationException("Payment provider could not process the request");
        }
        return response;
    }

    private JsonNode get(String path) {
        JsonNode response = restClientBuilder.baseUrl(baseUrl).build().get()
                .uri(path)
                .header("Authorization", "Bearer " + secretKey)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("status").asBoolean(false)) {
            throw new InvalidOperationException("Payment provider could not verify the request");
        }
        return response;
    }
}
