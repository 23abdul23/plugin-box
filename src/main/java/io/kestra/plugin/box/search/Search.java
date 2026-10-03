package io.kestra.plugin.box.search;

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
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

// TODO(you): implement run() AFTER folders.List. The fetchType switch is identical, so move it into a shared helper
// instead of copy-pasting (e.g. a package-private static method in io.kestra.plugin.box.models).
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Search Box content",
    description = "Searches files and folders by query, with optional file extension and owner filters."
)
@Plugin(
    examples = {
        @Example(
            title = "Find all PDF files matching a query.",
            full = true,
            code = """
                id: box_search
                namespace: company.team

                tasks:
                  - id: search
                    type: io.kestra.plugin.box.search.Search
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    query: invoice
                    fileExtensions:
                      - pdf
                    fetchType: FETCH
                """
        )
    }
)
public class Search extends AbstractBoxTask implements RunnableTask<Search.Output> {

    @Schema(title = "Search query")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> query;

    @Schema(title = "File extensions", description = "Only return files with these extensions, e.g. `pdf`, `csv`.")
    @PluginProperty(group = "filters")
    private Property<java.util.List<String>> fileExtensions;

    @Schema(title = "Owner user IDs", description = "Only return content owned by these Box users.")
    @PluginProperty(group = "filters")
    private Property<java.util.List<String>> ownerUserIds;

    @Schema(title = "Fetch strategy", description = "`FETCH_ONE`, `FETCH` (default) or `STORE`, same as `folders.List`.")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        // 1. render query; for the two lists use runContext.render(prop).asList(String.class) (empty list if unset)
        // 2. BoxClient client = client(runContext);
        // 3. page with OFFSET pagination (search has no marker):
        //      long offset = 0;
        //      var params = new SearchForContentQueryParams.Builder()
        //          .query(rQuery).fileExtensions(rExt).ownerUserIds(rOwners).limit(200L).offset(offset).build();
        //      var response = client.getSearch().searchForContent(params);
        //      var results = response.getSearchResults();            // OneOfTwo: use getSearchResults()
        //      results.getEntries() -> List<SearchResultItem> -> BoxItem.of(item)
        //      next page: offset += entries.size(); stop when entries is empty or offset >= results.getTotalCount()
        //    SearchForContentQueryParams is in com.box.sdkgen.managers.search
        //    Careful: pass null (not an empty list) for filters the user did not set, or Box may reject the request.
        // 4. same fetchType switch as folders.List -> rows / row / uri, always size
        throw new UnsupportedOperationException("TODO");
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Results", description = "Populated when `fetchType` is `FETCH`.")
        private final java.util.List<BoxItem> rows;

        @Schema(title = "First result", description = "Populated when `fetchType` is `FETCH_ONE`.")
        private final BoxItem row;

        @Schema(title = "Stored results URI", description = "Populated when `fetchType` is `STORE`.")
        private final URI uri;

        @Schema(title = "Result count")
        private final long size;
    }
}
