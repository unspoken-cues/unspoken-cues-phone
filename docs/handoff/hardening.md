# Hardening (2026-11-17 audit)

Server fixes from the 2026-11-17 audit. They are in [202611170005_hardening.sql](../../supabase/migrations/202611170005_hardening.sql) and in section 21 of [schema.sql](../../supabase/schema.sql). These changes have **not** been deployed to a live backend.

## What changed

| Area | Before | Now |
| --- | --- | --- |
| `event_members` reads | Co-members and the host could `select` every member row (account ID, RSVP). A blocked user who knew someone's ID could see them attending, skipping the privacy rules in `event_attendees()`. | One policy, "Users can read own memberships" (`user_id = auth.uid()`). The two old read policies are dropped. Other attendees are read only through `event_attendees()`. The app already reads only its own rows. SECURITY DEFINER functions are unaffected. |
| Profile sizes | No server limit. | Check constraints added `not valid`: `display_name` ≤ 80 characters (the watch shows 80), `bio` ≤ 120 (the app's `BIO_MAX_LENGTH`), and `preferences`/`boundaries` each at most 20 items of at most 60 characters. `event_profiles.display_name` ≤ 80. A violation raises `23514`. The app has no item-count or item-length limit for lists, and no name limit, so 20 × 60 and 80 are server choices. |
| Storage | Any file type and size. | `avatars` and `event-covers` accept only `image/jpeg` up to 5 MB (5242880 bytes). The app uploads JPEGs to `.jpg` paths. storage-kt 3.2.6 infers `image/jpeg` from the extension, since the app sets no content type. |
| Account deletion | None. | `public.delete_my_account()`: authenticated only; `28000` when signed out. It deletes the caller's `auth.users` row, and every app table cascades from it. Reports the caller filed stay, with `reporter` null. Reports about the caller are deleted with them (`reported` cascades). Storage objects do not cascade: the app deletes the caller's files through the Storage API first. |
| Event end rule | `coalesce(ends_at, starts_at + interval '3 hours')` was copied into `make_swap_temporary`, `post_event_announcement`, `event_wave_state` and `send_event_wave`. | One internal helper, `public.event_end(public.events)`, with no client execute. Those four functions call it and are otherwise unchanged. The app's `DEFAULT_EVENT_LENGTH` points to it. |

A new role fixture, [core_privacy_authorization.sql](../../supabase/tests/core_privacy_authorization.sql), covers the attendee list, binder, card swaps, direct table reads, the limits above and account deletion. [event_directory_authorization.sql](../../supabase/tests/event_directory_authorization.sql) now expects the host to read no membership rows directly. The test `bootstrap.sql` gains the bucket columns `file_size_limit` and `allowed_mime_types`, which real Supabase already has.

## Deploy

1. Apply 001–004 first. Then apply 005, which is transactional and repeatable. A new database can run `schema.sql` instead.
2. Before applying, find rows already over a limit. `not valid` keeps them, but **any later update of such a row fails**, even a cue change, until the oversized field is shortened:

   ```sql
   select id from public.profiles
   where char_length(display_name) > 80 or char_length(bio) > 120
      or not public.text_items_fit(preferences, 20, 60) or not public.text_items_fit(boundaries, 20, 60);
   select event_id, user_id from public.event_profiles where char_length(display_name) > 80;
   ```

   (Before 005, run the `text_items_fit` definition from the migration first, or check `cardinality` by hand.) Shorten those rows, or contact their owners. Once nothing is returned, you may run `alter table ... validate constraint`.
3. Ship app builds that cap names and lists in the editor. Otherwise the server's `23514` is the only feedback.

## Verify

Run each case in a fresh PGlite database:

- Fresh: `supabase/tests/bootstrap.sql`, then `supabase/schema.sql` twice, then every fixture in `supabase/tests/`.
- Upgrade: `bootstrap.sql`, then the schema at `f8ae8b2`, then 005 twice, then every fixture.

Both passed on 2026-11-17. After a double run, `event_members` has exactly four policies: join, own read, RSVP update and leave. Without the policy change, the core fixture fails at "a co-member reads only their own membership rows". With the co-member policy dropped but the host policy kept, it fails at "the host reads no membership rows directly".

## Rollback

Roll back clients first. Then, in one transaction:

- **Membership reads.** Drop "Users can read own memberships". Re-create "Members can read co-members" `using (public.is_event_member(event_id) and (cued_in or user_id = auth.uid()))`. Re-create "Host can read event members" from schema section 9. This brings the leak back.
- **Limits.** Drop the five `*_length`/`*_size` constraints, then `text_items_fit(text[], integer, integer)`.
- **Buckets.** Set `file_size_limit` and `allowed_mime_types` to null.
- **Account deletion.** Drop `delete_my_account()`. Deleted accounts cannot be restored.
- **Event end.** Restore the four functions with the inline expression (004's definitions, plus 001's `post_event_announcement` and the schema's `make_swap_temporary`). Then drop `event_end(public.events)`. Restore before dropping, or those functions fail.
