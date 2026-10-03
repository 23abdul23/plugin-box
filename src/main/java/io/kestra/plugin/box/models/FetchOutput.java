package io.kestra.plugin.box.models;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** Output shared by every task that returns a list of Box items, shaped by `fetchType`. */
@SuperBuilder
@Getter
public class FetchOutput implements io.kestra.core.models.tasks.Output {
    @Schema(title = "Items", description = "Populated when `fetchType` is `FETCH`.")
    private final java.util.List<BoxItem> rows;

    @Schema(title = "First item", description = "Populated when `fetchType` is `FETCH_ONE`.")
    private final BoxItem row;

    @Schema(title = "Stored items URI", description = "Populated when `fetchType` is `STORE`.")
    private final URI uri;

    @Schema(title = "Item count")
    private final long size;

    public static FetchOutput of(RunContext runContext, FetchType fetchType, java.util.List<BoxItem> items) throws IOException {
        FetchOutputBuilder<?, ?> output = FetchOutput.builder().size(items.size());
        switch (fetchType) {
            case FETCH_ONE -> output.row(items.isEmpty() ? null : items.getFirst());
            case FETCH -> output.rows(items);
            case STORE -> {
                Path tempFile = runContext.workingDir().createTempFile(".ion");
                try (var out = new BufferedOutputStream(Files.newOutputStream(tempFile), FileSerde.BUFFER_SIZE)) {
                    for (BoxItem item : items) {
                        FileSerde.write(out, item);
                    }
                }
                output.uri(runContext.storage().putFile(tempFile.toFile()));
            }
            case NONE -> { }
        }
        return output.build();
    }
}
