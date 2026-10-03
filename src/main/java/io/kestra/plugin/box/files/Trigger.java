package io.kestra.plugin.box.files;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.folders.GetFolderItemsQueryParams;
import com.box.sdkgen.schemas.item.Item;
import com.box.sdkgen.schemas.items.Items;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.box.AbstractBoxTrigger;
import io.kestra.plugin.box.models.BoxItem;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger on new Box files",
    description = "Polls a Box folder and starts an execution for a new file. If several files arrived since the last poll, "
        + "they fire one per poll, oldest first, so none is lost. "
        + "The position of the last fired file is stored in the namespace KV Store. "
        + "The first poll only records the current position: files already in the folder do not fire."
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
public class Trigger extends AbstractBoxTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {

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
        RunContext runContext = conditionContext.getRunContext();
        String rFolderId = runContext.render(folderId).as(String.class).orElse("0");

        java.util.List<BoxItem> files = listFiles(client(runContext), rFolderId);

        KVStore kv = runContext.namespaceKv(context.getNamespace());
        String key = "box_trigger_" + context.getTriggerId() + "_" + rFolderId;
        Optional<Position> last = kv.getValue(key).map(KVValue::value).map(v -> Position.parse((String) v));

        // first poll: remember where we are, do not fire for what is already there
        if (last.isEmpty()) {
            // ponytail: empty folder starts at the local clock, a file created by a skewed Box clock just before it is missed
            Position start = files.stream().map(Position::of).max(Comparator.naturalOrder()).orElse(new Position(Instant.now(), ""));
            kv.put(key, new KVValueAndMetadata(new KVMetadata(null, (Duration) null), start.format()));
            return Optional.empty();
        }

        Optional<BoxItem> next = next(last.get(), files);
        if (next.isEmpty()) {
            return Optional.empty();
        }

        BoxItem file = next.get();
        kv.put(key, new KVValueAndMetadata(new KVMetadata(null, (Duration) null), Position.of(file).format()));
        runContext.logger().info("New Box file '{}' ({}) in folder {}", file.getName(), file.getId(), rFolderId);

        Output output = Output.builder()
            .id(file.getId())
            .name(file.getName())
            .size(file.getSize())
            .createdAt(file.getCreatedAt())
            .build();
        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    // oldest file strictly after the position
    static Optional<BoxItem> next(Position after, java.util.List<BoxItem> files) {
        return files.stream()
            .filter(f -> f.getCreatedAt() != null)
            .filter(f -> Position.of(f).compareTo(after) > 0)
            .min(Comparator.comparing(Position::of));
    }

    private static java.util.List<BoxItem> listFiles(BoxClient client, String folderId) {
        java.util.List<BoxItem> files = new ArrayList<>();
        String marker = null;
        do {
            // created_at is not in the default folder-item fields, ask for it
            var params = new GetFolderItemsQueryParams.Builder()
                .usemarker(true)
                .marker(marker)
                .limit(1000L)
                .fields(java.util.List.of("id", "name", "type", "size", "created_at"))
                .build();
            Items page = client.getFolders().getFolderItems(folderId, params);
            for (Item item : page.getEntries()) {
                if (item.isFileFull()) {
                    files.add(BoxItem.of(item.getFileFull()));
                }
            }
            marker = page.getNextMarker();
        } while (marker != null && !marker.isEmpty());
        return files;
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    // (creation time, id): a total order, so files created in the same second are neither skipped nor repeated
    record Position(Instant createdAt, String id) implements Comparable<Position> {
        static Position of(BoxItem file) {
            return new Position(file.getCreatedAt().toInstant(), file.getId());
        }

        static Position parse(String stored) {
            int sep = stored.indexOf('|');
            return new Position(Instant.parse(stored.substring(0, sep)), stored.substring(sep + 1));
        }

        String format() {
            return createdAt + "|" + id;
        }

        @Override
        public int compareTo(Position other) {
            int byTime = createdAt.compareTo(other.createdAt);
            return byTime != 0 ? byTime : id.compareTo(other.id);
        }
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "File ID")
        private final String id;

        @Schema(title = "File name")
        private final String name;

        @Schema(title = "File size in bytes")
        private final Long size;

        @Schema(title = "Creation time")
        private final OffsetDateTime createdAt;
    }
}
