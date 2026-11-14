## Database tables

### Accounts and access

| Table | Purpose |
|---|---|
| `USERS` | Stores user accounts, profile details, and password hashes. |
| `USER_QUOTAS` | Tracks each user’s storage limit, bytes used, and bytes reserved for uploads. |
| `REFRESH_TOKENS` | Stores refresh-token hashes and tracks rotation and revocation for login sessions. |
| `PERSONAL_ACCESS_TOKENS` | Stores token hashes, scopes, and expiry for revocable CLI and script access. |

### Files and uploads

| Table | Purpose |
|---|---|
| `ENTRIES` | Represents files and folders in the drive. Each file entry points to its current revision. |
| `FILE_REVISIONS` | Records saved versions of a file, including edits saved as conflicts. |
| `BLOBS` | Stores the location, size, and checksum of file contents kept on disk or in object storage. |
| `UPLOADS` | Tracks an upload’s destination, status, and expiry. |
| `UPLOAD_CHUNKS` | Records which parts arrived so an interrupted upload can resume by sending the missing parts. |
| `IDEMPOTENCY_KEYS` | Records the outcome of a request so a retry can return the same result without repeating the action. |

### Delivery bundles

| Table | Purpose |
|---|---|
| `BUNDLES` | Represents a delivery and stores its title, manifest, and expiry. |
| `BUNDLE_ITEMS` | Pins the exact revision and ZIP path of each file in a delivery. |
| `BUNDLE_SHARES` | Stores hashed download tokens so each link can expire or be revoked independently. |

### Background processing and sync

| Table | Purpose |
|---|---|
| `OUTBOX_EVENTS` | Records events in the same database transaction as the change that produced them, ready for later publication to Kafka. |
| `PROCESSED_EVENTS` | Records which events each consumer has handled so a redelivered event can be recognized. |
| `FILE_THUMBNAILS` | Tracks the preview image and generation status for each file revision. |
| `FILE_ACTIVITIES` | Stores the timeline users see, such as “Uploaded `photo.jpg`.” |
| `CHANGE_LOG` | Tells sync clients which drive items changed since their last check, including renames, replacements, and deletions. |
```mermaid
---
config:
  layout: elk
  elk:
    mergeEdges: false
---
erDiagram
    USERS ||..o{ REFRESH_TOKENS : signs_in_with
    REFRESH_TOKENS o|..o| REFRESH_TOKENS : replaced_by
    USERS ||..o{ PERSONAL_ACCESS_TOKENS : creates
    USERS ||--|| USER_QUOTAS : has
    USERS ||..o{ ENTRIES : owns
    USERS ||..o{ BLOBS : owns
    USERS ||..o{ UPLOADS : starts
    USERS ||..o{ FILE_REVISIONS : authors
    USERS ||..o{ FILE_ACTIVITIES : has_activity
    USERS ||..o{ BUNDLES : creates
    USERS ||..o{ CHANGE_LOG : has_changes

    ENTRIES o|..o{ ENTRIES : parent_of
    ENTRIES o|..o{ ENTRIES : original_parent_of
    ENTRIES ||..o{ FILE_REVISIONS : has_history
    ENTRIES }o..o| FILE_REVISIONS : points_to_current
    FILE_REVISIONS o|..o{ FILE_REVISIONS : base_for
    BLOBS ||..o{ FILE_REVISIONS : backs
    FILE_REVISIONS ||--o| FILE_THUMBNAILS : has_preview

    ENTRIES o|..o{ UPLOADS : destination_folder_for
    ENTRIES o|..o{ UPLOADS : replacement_target_for
    ENTRIES o|..o{ UPLOADS : result_of
    FILE_REVISIONS o|..o{ UPLOADS : expected_base_for
    UPLOADS ||--o{ UPLOAD_CHUNKS : receives

    BUNDLES ||..|{ BUNDLE_ITEMS : contains
    FILE_REVISIONS ||..o{ BUNDLE_ITEMS : pinned_in
    BUNDLES ||..o{ BUNDLE_SHARES : shared_via

    USERS {
        uuid id PK
        text email UK "normalized lowercase"
        text password_hash
        text display_name
        timestamptz email_verified_at
        boolean is_active
        timestamptz password_changed_at
        timestamptz created_at
        timestamptz updated_at
    }

    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        text token_hash UK "SHA-256, never raw token"
        uuid replaced_by_id FK, UK "nullable, for rotation"
        timestamptz expires_at
        timestamptz revoked_at
        timestamptz created_at
    }

    USER_QUOTAS {
        uuid user_id PK, FK
        bigint quota_bytes
        bigint used_bytes
        bigint reserved_bytes
        timestamptz updated_at
    }

    BLOBS {
        uuid id PK
        uuid owner_id FK
        text storage_key UK
        bigint size_bytes
        text checksum_alg
        text checksum_hex
        text status "STORED | PENDING_DELETE | DELETED"
        timestamptz created_at
        timestamptz deleted_at
    }

    ENTRIES {
        uuid id PK
        uuid owner_id FK
        uuid parent_id FK "nullable only for root"
        text kind "FOLDER | FILE"
        boolean is_root
        text name
        text content_type
        uuid current_revision_id FK "nullable"
        bigint version
        timestamptz trashed_at
        uuid original_parent_id FK "nullable"
        timestamptz created_at
        timestamptz updated_at
    }

    UPLOADS {
        uuid id PK
        uuid owner_id FK
        uuid parent_id FK "nullable, for new file"
        text target_name "for new file"
        uuid target_entry_id FK "nullable, for replacement"
        uuid expected_base_revision_id FK "nullable, for replacement"
        int chunk_size_bytes
        bigint expected_bytes
        bigint reserved_bytes
        text expected_sha256
        text status "RESERVED | UPLOADING | COMPLETED | ABORTED | EXPIRED | FAILED"
        uuid result_entry_id FK "nullable until completion"
        timestamptz expires_at
        timestamptz created_at
        timestamptz updated_at
    }

    UPLOAD_CHUNKS {
        uuid upload_id PK, FK
        int chunk_number PK
        int size_bytes
        text sha256
        text storage_part_ref
        timestamptz received_at
    }

    FILE_REVISIONS {
        uuid id PK
        uuid file_id FK
        uuid blob_id FK
        int revision_number
        uuid base_revision_id FK "nullable"
        text status "READY | CONFLICTED"
        uuid created_by FK
        timestamptz created_at
    }

    IDEMPOTENCY_KEYS {
        uuid owner_id PK
        text operation PK
        text idempotency_key PK
        text request_hash
        int response_status
        jsonb response_body
        timestamptz expires_at
        timestamptz created_at
    }

    OUTBOX_EVENTS {
        uuid id PK
        uuid aggregate_id
        text event_type
        int schema_version
        jsonb payload
        timestamptz published_at
        int attempts
        timestamptz next_attempt_at
        timestamptz created_at
    }

    PROCESSED_EVENTS {
        text consumer_group PK
        uuid event_id PK
        timestamptz processed_at
    }

    FILE_THUMBNAILS {
        uuid revision_id PK, FK
        text storage_key
        int width
        int height
        bigint size_bytes
        text status "READY | UNSUPPORTED | FAILED"
        timestamptz created_at
    }

    FILE_ACTIVITIES {
        uuid id PK
        uuid source_event_id UK
        uuid owner_id FK
        uuid file_id
        uuid revision_id
        text activity_type
        jsonb details
        timestamptz occurred_at
    }

    BUNDLES {
        uuid id PK
        uuid owner_id FK
        text title
        text manifest_canonical
        text manifest_sha256
        bigint total_bytes
        timestamptz expires_at
        timestamptz deleted_at
        timestamptz created_at
    }

    BUNDLE_ITEMS {
        uuid id PK
        uuid bundle_id FK
        uuid revision_id FK "ON DELETE RESTRICT"
        text relative_path
        bigint size_bytes
        text sha256
    }

    BUNDLE_SHARES {
        uuid id PK
        uuid bundle_id FK
        text token_hash UK
        int download_count
        timestamptz expires_at
        timestamptz revoked_at
        timestamptz created_at
    }

    PERSONAL_ACCESS_TOKENS {
        uuid id PK
        uuid user_id FK
        text name
        text token_hash UK
        text scopes
        timestamptz last_used_at
        timestamptz expires_at
        timestamptz revoked_at
        timestamptz created_at
    }

    CHANGE_LOG {
        bigint seq PK
        uuid owner_id FK
        uuid entry_id
        text change_type
        uuid revision_id
        timestamptz occurred_at
    }
```

