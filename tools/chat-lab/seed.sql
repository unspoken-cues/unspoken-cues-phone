-- Apply only to the isolated lab database after the Supabase test bootstrap and schema.
insert into auth.users(id, raw_user_meta_data) values
 ('60000000-0000-4000-8000-000000000001', '{"display_name":"Alex"}'),
 ('60000000-0000-4000-8000-000000000002', '{"display_name":"Blair"}'),
 ('60000000-0000-4000-8000-000000000003', '{"display_name":"Organizer"}');
insert into public.events(id, host_id, name, join_code) values
 ('60000000-0000-4000-8000-000000000010','60000000-0000-4000-8000-000000000003','Community Meetup','LAB600');
insert into public.event_members(event_id,user_id) select '60000000-0000-4000-8000-000000000010', id from auth.users
 where id in ('60000000-0000-4000-8000-000000000001','60000000-0000-4000-8000-000000000002');
insert into public.events(id, host_id, name, join_code) values
 ('60000000-0000-4000-8000-000000000011','60000000-0000-4000-8000-000000000003','Delivery benchmark','LAB611');
insert into public.event_members(event_id,user_id) select '60000000-0000-4000-8000-000000000011', id from auth.users
 where id in ('60000000-0000-4000-8000-000000000001','60000000-0000-4000-8000-000000000002');
