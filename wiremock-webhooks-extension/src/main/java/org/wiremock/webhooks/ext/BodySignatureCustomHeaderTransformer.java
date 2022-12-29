package org.wiremock.webhooks.ext;

import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.apache.commons.codec.binary.Hex;
import org.wiremock.webhooks.WebhookDefinition;
import org.wiremock.webhooks.WebhookTransformer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.function.Function;

public class BodySignatureCustomHeaderTransformer implements WebhookTransformer {

    private final Function<String, String> envResolver;
    public BodySignatureCustomHeaderTransformer() {
        this(System::getenv);
    }

    public BodySignatureCustomHeaderTransformer(Function<String, String> envResolver) {
        this.envResolver = envResolver;
    }

    @Override
    public WebhookDefinition transform(ServeEvent serveEvent, WebhookDefinition webhookDefinition) {
        Metadata bodySignatureParameter = webhookDefinition.getExtraParameters().getMetadata("bodySignature", null);
        if (bodySignatureParameter == null) {
            return webhookDefinition;
        }

        String secretEnvVarName = bodySignatureParameter.getString("secretEnvVarName", null);
        String headerName = bodySignatureParameter.getString("headerName", null);
        if (secretEnvVarName == null || headerName == null) {
            return webhookDefinition;
        }

        String secret = envResolver.apply(secretEnvVarName);

        if (secret == null) {
            return webhookDefinition;
        }

        return webhookDefinition.withHeader(headerName, signBody(secret, webhookDefinition.getBody()));
    }

    private String signBody(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec
                    = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] sigBytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(sigBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
