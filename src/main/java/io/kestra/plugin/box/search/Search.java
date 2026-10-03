package io.kestra.plugin.box.search;

import java.util.ArrayList;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.search.SearchForContentQueryParams;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.box.AbstractBoxTask;
import io.kestra.plugin.box.models.BoxItem;
import io.kestra.plugin.box.models.FetchOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class Search extends AbstractBoxTask implements RunnableTask<FetchOutput> {

    @Schema(title = "Search query")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> query;

    @Schema(title = "File extensions", description = "Only return files with these extensions, e.g. `pdf`, `csv`.")
    @PluginProperty(group = "advanced")
    private Property<java.util.List<String>> fileExtensions;

    @Schema(title = "Owner user IDs", description = "Only return content owned by these Box users.")
    @PluginProperty(group = "advanced")
    private Property<java.util.List<String>> ownerUserIds;

    @Schema(title = "Fetch strategy", description = "`FETCH_ONE`, `FETCH` (default) or `STORE`, same as `folders.List`.")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        String rQuery = runContext.render(query).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'query' is required"));
        FetchType rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        BoxClient client = client(runContext);

        // Box rejects an empty filter list, so an unset filter must be sent as null
        java.util.List<String> rExtensions = nullIfEmpty(runContext.render(fileExtensions).asList(String.class));
        java.util.List<String> rOwners = nullIfEmpty(runContext.render(ownerUserIds).asList(String.class));

        java.util.List<BoxItem> items = new ArrayList<>();
        long offset = 0;
        while (true) {
            var params = new SearchForContentQueryParams.Builder()
                .query(rQuery)
                .fileExtensions(rExtensions)
                .ownerUserIds(rOwners)
                .limit(rFetchType == FetchType.FETCH_ONE ? 1L : 200L)
                .offset(offset)
                .build();
            var results = client.getSearch().searchForContent(params).getSearchResults();

            results.getEntries().forEach(entry -> items.add(BoxItem.of(entry)));
            offset += results.getEntries().size();
            if (results.getEntries().isEmpty() || offset >= results.getTotalCount() || rFetchType == FetchType.FETCH_ONE) {
                break;
            }
        }

        runContext.logger().debug("Found {} Box results for '{}'", items.size(), rQuery);
        return FetchOutput.of(runContext, rFetchType, items);
    }

    private static java.util.List<String> nullIfEmpty(java.util.List<String> list) {
        return list == null || list.isEmpty() ? null : list;
    }
}
