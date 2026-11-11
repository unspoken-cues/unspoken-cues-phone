# API and repository reference

Return to the [handoff index](README.md). Sources: [data package](../../app/src/main/java/com/example/unspokenqueues/data), [schema.sql](../../supabase/schema.sql).

## Transport and authentication

The Android client uses `supabase-kt`, not manually constructed REST requests. `SupabaseClientProvider.client` installs Auth, Postgrest and Storage on one lazy client with Ktor's OkHttp engine. BuildConfig supplies the base project URL and public client key. The Auth SDK manages the session; table/RPC authorization depends on the current user's JWT and SQL RLS. There are no checked-in Edge Functions or custom backend services.

The paths below identify logical Supabase REST resources (`/rest/v1`, `/auth/v1`, `/storage/v1`) for tracing requests. Exact headers, grant query parameters, token refresh and serialization are SDK-managed, not separate HTTP implementations in this code. No network capture was used to validate wire traffic. Calls throw unless a method documents a nullable result; UI handling varies by caller.

## AuthRepository

All Auth methods use SDK APIs under the project's Auth service. Email/password is the only installed provider flow.

| Method/property | SDK operation | Input/result and behavior |
| --- | --- | --- |
| `sessionStatus` | `auth.sessionStatus` | Flow of initialization/authenticated/not-authenticated states |
| `currentUser`, `currentUserId` | `currentUserOrNull` | Local current user or null; no explicit network request |
| `signIn(email,password)` | `signInWith(Email)` | Trims email, stores session through SDK |
| `signUp(email,password)` | `signUpWith(Email)` | Trims email; throws `EmailConfirmationRequiredException` if signup returns no session |
| `signOut()` | `auth.signOut()` | SDK sign-out; root caller currently discards failure |
| `requestPasswordReset(email)` | `resetPasswordForEmail` | Trimmed email, redirect `unspokencues://password-reset` |
| `recoverPassword(link)` | `awaitInitialization`, `parseSessionFromUrl`, `retrieveUser`, `importSession` | Requires dedicated callback and recovery type; validates user before import |
| `updatePassword(password)` | `updateUser { password = ... }` | Waits for initialization and requires a current session; caller controls recovery readiness |
| `cancelPasswordRecovery()` | `clearSession()` | Local clear, including offline |

`authFailureMessage` translates Auth error codes, confirmation-required and I/O errors into safe copy. It covers invalid credentials, unconfirmed email, duplicate account, weak/same password, expiry, invalid email, rate limits, disabled signup/provider and banned accounts, with a generic fallback. Never show `exception.message` in auth UI: it may contain request details.

## ProfileRepository → `/rest/v1/profiles`

| Method | Database operation | Result/important exclusions |
| --- | --- | --- |
| `loadProfile(userId)` | SELECT all, `id = userId`, limit 1 | `ProfileRow?`; absent/invisible row is null |
| `upsertProfile(userId,profile,status)` | UPSERT `ProfileUpsert` | Writes id, display_name, bio, preferences, boundaries, is_public, status, avatar_url |
| `updateStatus(userId,status)` | UPDATE status where id matches | Uppercase enum name |
| `updateVisibility(userId,isPublic)` | UPDATE is_public where id matches | Boolean |
| `updateCardImage(userId,url)` | UPDATE card_image_url where id matches | Empty string clears reference |
| `markOnboardingComplete(userId)` | UPDATE onboarding_complete=true where id matches | One-way client operation |

Partial updates do not select affected rows; an RLS-hidden/missing row can produce no changed row without an application error. Upsert deliberately excludes `onboarding_complete`, `card_image_url` and timestamps. Read DTO adds the two client-managed fields above; SQL also supplies creation/update timestamps ignored by the client. `ProfileRow.toCueStatus` falls back to GREEN for an unknown string. Local URI fields never cross the network.

Example upsert shape (all values synthetic):

```json
{
  "id": "<signed-in-user-uuid>",
  "display_name": "Example Person",
  "bio": "Enjoys small groups",
  "preferences": ["Text first"],
  "boundaries": ["Ask before photos"],
  "is_public": true,
  "status": "GREEN",
  "avatar_url": ""
}
```

## SwapRepository

| Method | Resource and operation | Result |
| --- | --- | --- |
| `myCardToken()` | RPC `my_card_token`, no arguments | String; creates owner token on first request |
| `swapByToken(token)` | RPC `swap_by_card_token`, `{"card_token":"<32-hex-token>"}` | Owner `ProfileRow` mapped to `CollectedCard`; creates mutual swap |
| `loadBinder(userId)` | SELECT `swaps`; derive opposite user IDs; SELECT `profiles` with id IN partners | Sorted list; skips second query when there are no partners |
| `removeSwap(userId,otherUserId)` | DELETE `swaps` matching either orientation of the pair | No affected-row verification; either participant can delete |

RPCs are logical POSTs under `/rest/v1/rpc/<function>`. The swaps SELECT has no user filter: RLS is essential. A collected card is a live profile projection with partner userId and cue. `swapFailureMessage` maps SQLSTATE `P0002` to an invalid card message and `22023` to own card; other errors become a connection/retry message. No client token rotation/revocation API exists.

## EventRepository

| Method | Resource/operation | Result and limits |
| --- | --- | --- |
| `createEvent(hostId,name,details)` | Delegates to draft overload | No time/place supplied by this overload |
| `createEvent(hostId,draft)` | INSERT `events`, select returned row | Random join_code; retries `23505` up to 5 total attempts |
| `myEvents(userId)` | SELECT `events`, created_at descending | userId parameter unused; all RLS-readable events, including unrelated public ones |
| `discoverEvents()` | SELECT `events`, is_public=true and active=true, starts_at ascending, limit 60 | UI removes time-past rows after limit |
| `event(eventId)` | SELECT `events` where id matches | First mapped event or null for no readable row; network errors still throw |
| `myRsvps(userId)` | SELECT `event_members` where user_id matches | Map eventId → known `going`/`maybe`; unknown wire values skipped |
| `eventAttendees(eventId)` | SELECT members ordered joined_at ascending, then profiles where id IN member IDs | Joined profiles only; RLS-hidden profiles skipped |
| `joinByCode(code)` | RPC `join_event_by_code`, `{"code":"<trimmed-code>"}` | Event; server case-insensitive active-code lookup and idempotent membership insert |
| `rsvp(eventId,userId,answer)` | UPSERT `event_members`, conflict target `event_id,user_id` | Payload event_id, user_id, rsvp (`going`/`maybe`) |
| `endEvent(eventId)` | UPDATE events.active=false where id matches | No selected result/affected-row check |
| `leaveEvent(eventId,userId)` | DELETE matching member; select removed rows | Throws if result empty |
| `deleteEvent(eventId)` | DELETE event where id matches; select removed rows | Throws if result empty; DB cascades memberships |

`EventInsert` sends host_id, trimmed name/details/location/host_name, generated join_code, ISO instant starts_at/ends_at (nullable), cover_url, tags, is_public. The server sets id, active, created_at and going_count defaults. Draft overload callers must normalize tags themselves. This is not an edit-event API: no repository method updates name, schedule or location after creation.

`EventRow` maps the returned fields to `Event`; timestamp parsing accepts `OffsetDateTime` and returns null for missing/bad values. `EventMemberRow` has event_id, user_id, joined_at, rsvp with legacy default Going. `ProfileRow.toAttendees` preserves membership order after profile lookup.

`joinFailureMessage` recognizes `P0002` (no active event); other failures are generic. RPC unauthenticated error is `28000`. UI validation of code length, event name/time and tags is not repeated as equivalent SQL constraints.

## Image repositories → Storage

Uploads first call `compressToJpeg` on `Dispatchers.IO`: decode dimensions, choose power-of-two sampling, preserve aspect ratio without upscaling, rotate EXIF 90/180/270 when specified, encode JPEG. Mirrored EXIF orientations are not specially handled. Decode/read failures throw `IOException`.

| Method | Bucket/object path | Encoding | Result |
| --- | --- | --- | --- |
| `AvatarRepository.upload(resolver,userId,uri)` | `avatars/<userId>/avatar.jpg`, upsert | max side 512, quality 85 | Public URL with `?v=<currentMillis>` |
| `AvatarRepository.uploadCardImage(...)` | `avatars/<userId>/card.jpg`, upsert | max side 1024, quality 85 | Public URL with `?v=<currentMillis>` |
| `EventCoverRepository.upload(...)` | `event-covers/<userId>/cover-<currentMillis>.jpg`, new object | max side 1280, quality 82 | Public URL to versioned path |

Public URL form: `<SUPABASE_URL>/storage/v1/object/public/<bucket>/<path>`. Repositories return URLs; callers perform the separate row insert/update. A later row-write failure can leave an orphan upload. Removing a URL does not delete an object; these clients expose no storage-delete operation. Public reads do not inherit profile/event row visibility.

## Other contracts

Phone/watch publishing uses the Data Layer, not HTTP: [complete contract](watch-sync.md). Card URIs use `unspokencues://card/<32-hex-token>`. Password callbacks use `unspokencues://password-reset` with SDK recovery parameters. Event QR data is a raw join code. Do not substitute one payload format for another.

When adding an API operation, update the repository, DTO serial names/defaults, SQL authorization, error mapping, invoking screen feedback, tests and this reference together. A client-supplied user ID is a filter or payload value, never authorization by itself.
