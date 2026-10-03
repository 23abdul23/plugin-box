package io.kestra.plugin.box.folders;

import java.net.URI;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.box.AbstractBoxTask;
import io.kestra.plugin.box.models.BoxItem;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

// TODO(you): implement run(). Model it on plugin-dropbox files/List.java (pagination loop + fetchType switch).
// In this file "List" is YOUR class, so write java.util.List<BoxItem> for the Java type.
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "List Box folder items",
    description = "Lists the files, folders and web links directly inside a Box folder. `fetchType` controls memory vs storage output."
)
@Plugin(
    examples = {
        @Example(
            title = "List a folder and log the count.",
            full = true,
            code = """
                id: box_list_folder
                namespace: company.team

                tasks:
                  - id: list_items
                    type: io.kestra.plugin.box.folders.List
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    folderId: "123456789"
                    fetchType: FETCH

                  - id: log_results
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_items.size }} items"
                """
        )
    }
)
public class List extends AbstractBoxTask implements RunnableTask<List.Output> {

    @Schema(title = "Folder ID", description = "`0` is the root folder.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> folderId = Property.ofValue("0");

    @Schema(
        title = "Fetch strategy",
        description = "`FETCH_ONE`: first item only.\n`FETCH` (default): all items in memory.\n`STORE`: write items to Kestra storage and return the URI."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        // 1. render folderId and fetchType (see Dropbox List.java line 126 for the FetchType render)
        // 2. BoxClient client = client(runContext);
        // 3. page through the folder with MARKER pagination:
        //      String marker = null;
        //      do {
        //          var params = new GetFolderItemsQueryParams.Builder().usemarker(true).marker(marker).limit(1000L).build();
        //          Items page = client.getFolders().getFolderItems(rFolderId, params);
        //          for (Item item : page.getEntries()) rows.add(BoxItem.of(item));     // BoxItem.of(Item) exists
        //          marker = page.getNextMarker();
        //      } while (marker != null && !(rFetchType == FETCH_ONE && !rows.isEmpty()));
        //    GetFolderItemsQueryParams is in com.box.sdkgen.managers.folders, Items in com.box.sdkgen.schemas.items
        // 4. switch (rFetchType), copy Dropbox List.java lines 163-185:
        //      FETCH_ONE -> row = first item
        //      FETCH     -> rows = all items
        //      STORE     -> temp file via runContext.workingDir().createTempFile(".ion"), write each item with
        //                   FileSerde.write(outputStream, item), then runContext.storage().putFile(file) -> uri
        //    always set size = rows.size()
        throw new UnsupportedOperationException("TODO");
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Items", description = "Populated when `fetchType` is `FETCH`.")
        private final java.util.List<BoxItem> rows;

        @Schema(title = "First item", description = "Populated when `fetchType` is `FETCH_ONE`.")
        private final BoxItem row;

        @Schema(title = "Stored items URI", description = "Populated when `fetchType` is `STORE`.")
        private final URI uri;

        @Schema(title = "Item count")
        private final long size;
    }
}
