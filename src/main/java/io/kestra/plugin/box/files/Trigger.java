package io.kestra.plugin.box.files;

import java.time.Duration;
import java.util.Optional;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;

import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

// TODO(you): do this LAST. A trigger extends AbstractTrigger, NOT AbstractBoxTask, so it cannot inherit the
// connection properties. See "Step 8" in PLAN.md: move clientId/clientSecret/enterpriseId/userId/developerToken/jwtConfig
// and the client() logic out of AbstractBoxTask into an interface + static helper both classes use.
// Until then this skeleton only has the trigger-specific properties.
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger on new Box files",
    description = "Polls a Box folder and starts one execution for each new file. The last seen creation time is kept in the namespace KV Store."
)
@Plugin(
    examples = {
        @Example(
            title = "React when a new file lands in a folder.",
            full = true,
            code = """
                id: box_new_file
                namespace: company.team

                triggers:
                  - id: on_new_file
                    type: io.kestra.plugin.box.files.Trigger
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    folderId: "123456789"
                    interval: PT5M

                tasks:
                  - id: handle_file
                    type: io.kestra.plugin.core.log.Log
                    message: "New file: {{ trigger.name }}"
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {

    @Schema(title = "Folder ID", description = "Folder to watch for new files.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> folderId = Property.ofValue("0");

    @Schema(title = "Polling interval")
    @Builder.Default
    @PluginProperty(group = "execution")
    private final Duration interval = Duration.ofSeconds(60);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        // 1. RunContext runContext = conditionContext.getRunContext();
        RunContext runContext = conditionContext.getRunContext();

        // 2. list the folder like folders.List (marker pagination), keep only entries of type "file"
        // 3. read the watermark: KVStore kv = runContext.namespaceKv(context.getNamespace());
        //      Optional<KVValue> last = kv.getValue(key);    key e.g. "box_trigger_" + context.getTriggerId() + "_" + folderId
        //      parse the stored ISO string with OffsetDateTime.parse; first poll (nothing stored) = "now", so old files do not fire
        // 4. new files = createdAt strictly after the watermark, sorted by createdAt ascending
        // 5. write the watermark on EVERY poll, even when no new file:
        //      kv.put(key, new KVValueAndMetadata(null, newestCreatedAt.toString()));
        // 6. none new -> return Optional.empty()
        //    else -> Optional.of(TriggerService.generateExecution(this, conditionContext, context, output))
        //    Output must expose `name` so that {{ trigger.name }} works in the flow.
        //    Several new files: simplest is one Output holding the newest file, or emit one execution per poll listing all.
        // Tip: put steps 3-4 in a plain static method (watermark, items -> newItems) and unit test it without Box.
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "File ID")
        private final String id;

        @Schema(title = "File name")
        private final String name;
    }
}
