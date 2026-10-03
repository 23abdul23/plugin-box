package io.kestra.plugin.box;


import com.box.sdkgen.box.ccgauth.BoxCCGAuth;
import com.box.sdkgen.box.ccgauth.CCGConfig;
import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.box.jwtauth.BoxJWTAuth;
import com.box.sdkgen.box.jwtauth.JWTConfig;
import com.box.sdkgen.client.BoxClient;
import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractBoxTask extends Task {

    @ToString.Exclude
    @Schema(title = "Box client ID")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> clientId;

    @ToString.Exclude
    @Schema(title = "Box client secret")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> clientSecret;

    @Schema(title = "Enterprise ID", description = "Use with CCG to act as the app's service account.")
    @PluginProperty(group = "connection")
    private Property<String> enterpriseId;

    @Schema(title = "User ID", description = "Use with CCG to act as a specific user instead of the enterprise.")
    @PluginProperty(group = "connection")
    private Property<String> userId;


    @ToString.Exclude
    @Schema(title = "Developer token", description = "Short-lived token for quick tests. Takes priority over other auth.")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> developerToken;

    @ToString.Exclude
    @Schema(title = "JWT config JSON", description = "Contents of the JSON config file downloaded from the Box developer console. Used when no developer token is set and no client credentials are set.")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> jwtConfig;

    @VisibleForTesting
    protected BoxClient client(RunContext runContext) throws Exception {
        var rDeveloperToken = runContext.render(developerToken).as(String.class);
        if (rDeveloperToken.isPresent()) {
            return new BoxClient(new BoxDeveloperTokenAuth(rDeveloperToken.get()));
        }

        var rEnterpriseId = runContext.render(enterpriseId).as(String.class);
        var rUserId = runContext.render(userId).as(String.class);

        var rJwtConfig = runContext.render(jwtConfig).as(String.class);
        if (rJwtConfig.isPresent()) {
            BoxJWTAuth jwt = new BoxJWTAuth(JWTConfig.fromConfigJsonString(rJwtConfig.get()));
            // without a subject the JWT config's own enterprise ID is used
            if (rUserId.isPresent()) {
                jwt = jwt.withUserSubject(rUserId.get());
            } else if (rEnterpriseId.isPresent()) {
                jwt = jwt.withEnterpriseSubject(rEnterpriseId.get());
            }
            return new BoxClient(jwt);
        }

        var rClientId = runContext.render(clientId).as(String.class);
        var rClientSecret = runContext.render(clientSecret).as(String.class);
        if (rClientId.isPresent() && rClientSecret.isPresent()) {
            BoxCCGAuth ccg = new BoxCCGAuth(new CCGConfig(rClientId.get(), rClientSecret.get()));
            if (rUserId.isPresent()) {
                ccg = ccg.withUserSubject(rUserId.get());
            } else if (rEnterpriseId.isPresent()) {
                ccg = ccg.withEnterpriseSubject(rEnterpriseId.get());
            } else {
                throw new IllegalArgumentException("Client credentials need either 'enterpriseId' or 'userId'");
            }
            return new BoxClient(ccg);
        }

        throw new IllegalArgumentException(
            "No Box credentials: set 'developerToken', or 'jwtConfig', or 'clientId' + 'clientSecret' with 'enterpriseId' or 'userId'"
        );
    }
}
