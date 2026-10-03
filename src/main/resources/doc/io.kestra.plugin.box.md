Upload, download, search and manage files and folders in Box, and start flows when new files arrive, without custom scripts.

## What this plugin ships

- `files.Upload`, `files.Download`, `files.Get` and `files.Delete` move and manage single files. Files above 50 MB are uploaded with Box chunked upload.
- `folders.List` and `folders.Create` list a folder's items and create folders.
- `search.Search` finds content by query, file extension and owner.
- `files.Trigger` polls a folder and starts an execution for each new file.

Tasks that return many items (`folders.List`, `search.Search`) take a `fetchType`: `FETCH` returns the items in `rows`, `FETCH_ONE` returns the first one in `row`, and `STORE` writes them to Kestra internal storage and returns a `uri`.

## Authentication

Every task and the trigger accept the same connection properties. Store all credentials as secrets, e.g. `{{ secret('BOX_CLIENT_SECRET') }}`. The first method that is set wins:

1. **Developer token** (`developerToken`): quick tests only, it expires after 60 minutes.
2. **JWT** (`jwtConfig`): the contents of the JSON config file from your Box app. Add `userId` or `enterpriseId` to act as a specific subject.
3. **Client Credentials Grant** (`clientId`, `clientSecret`, plus `enterpriseId` or `userId`): the usual server-side setup.

To use client credentials, create a *Custom App* with *Server Authentication (Client Credentials Grant)* in the Box developer console, then have a Box admin authorize the app in the Admin Console. Without that authorization Box answers `unauthorized_client` even if the credentials are correct.

## Example

```yaml
id: box_upload_report
namespace: company.team

tasks:
  - id: upload
    type: io.kestra.plugin.box.files.Upload
    clientId: "{{ secret('BOX_CLIENT_ID') }}"
    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
    from: "{{ outputs.generate.outputFiles['report.csv'] }}"
    folderId: "0"
    name: report.csv
```

## Good to know

- Folder `0` is the root folder. Other folder IDs are the number at the end of the folder's URL in the Box web app.
- `files.Trigger` fires one execution per poll, oldest new file first. The first poll only records the folder's current state, so files already there do not fire. Its position is kept in the namespace KV Store.
