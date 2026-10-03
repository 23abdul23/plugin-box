package io.kestra.plugin.box.folders;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
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

// TODO(you): implement run(). Same shape as Upload's "simple" branch: build a request body, one SDK call.
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a Box folder",
    description = "Creates a folder inside a parent folder. Fails if a folder with the same name already exists there."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a folder at the root of Box.",
            full = true,
            code = """
                id: box_create_folder
                namespace: company.team

                tasks:
                  - id: create
                    type: io.kestra.plugin.box.folders.Create
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    parentFolderId: "0"
                    name: reports
                """
        )
    }
)
public class Create extends AbstractBoxTask implements RunnableTask<Create.Output> {

    @Schema(title = "Parent folder ID", description = "`0` is the root folder.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> parentFolderId = Property.ofValue("0");

    @Schema(title = "Folder name")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Override
    public Output run(RunContext runContext) throws Exception {
        // 1. render parentFolderId (orElse "0") and name (orElseThrow, like fileId in Delete)
        // 2. BoxClient client = client(runContext);
        // 3. build the request:
        //      new CreateFolderRequestBody(rName, new CreateFolderRequestBodyParentField(rParentId))
        //    both classes are in com.box.sdkgen.managers.folders
        // 4. FolderFull folder = client.getFolders().createFolder(body);
        // 5. return Output.builder().folder(BoxItem.of(folder)).build();   (BoxItem.of(Folder) already exists)
        throw new UnsupportedOperationException("TODO");
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created folder")
        private final BoxItem folder;
    }
}
