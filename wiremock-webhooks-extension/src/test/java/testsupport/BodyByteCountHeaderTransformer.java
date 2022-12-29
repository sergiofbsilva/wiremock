package testsupport;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.wiremock.webhooks.WebhookDefinition;
import org.wiremock.webhooks.WebhookTransformer;

public class BodyByteCountHeaderTransformer implements WebhookTransformer {

  @Override
  public WebhookDefinition transform(ServeEvent serveEvent, WebhookDefinition webhookDefinition) {
    String body = webhookDefinition.getBody();
    int length = body == null ? 0 : body.length();
    return webhookDefinition.withHeader("x-body-length", Integer.toString(length));
  }

}
