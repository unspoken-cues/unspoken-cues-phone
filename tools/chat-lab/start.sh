#!/usr/bin/env bash
# Start a disposable, loopback-only relay. All accounts and tokens are local fixtures.
set -euo pipefail
cd "$(dirname "$0")/../.."
lab_dir="${CHAT_LAB_DIR:-${TMPDIR:-/tmp}/unspoken-event-chat-lab}"
mkdir -p "$lab_dir"
chmod 700 "$lab_dir"
db=uc-event-chat-lab-db
api=uc-event-chat-lab-rest
network=uc-event-chat-lab
umask 077
if ! docker network inspect "$network" >/dev/null 2>&1; then docker network create "$network" >/dev/null; fi
if ! docker container inspect "$db" >/dev/null 2>&1; then
    openssl rand -hex 32 > "$lab_dir/db-password"
    openssl rand -hex 32 > "$lab_dir/jwt-secret"
    docker run -d --name "$db" --network "$network" -e "POSTGRES_PASSWORD=$(cat "$lab_dir/db-password")" postgres:16-alpine >/dev/null
else
    docker start "$db" >/dev/null
fi
for i in {1..60}; do
    if docker exec "$db" pg_isready -U postgres >/dev/null; then break; fi
    sleep 1
done
if ! docker exec "$db" psql -U postgres -Atc "select to_regclass('public.chat_lab_ready') is not null" | grep -qx t; then
    # One transaction, so an interrupted setup can be retried.
    {
        cat supabase/tests/bootstrap.sql
        sed -E '/^(begin|commit);$/d' supabase/schema.sql
        cat tools/chat-lab/seed.sql
        echo "create role lab_gateway login noinherit password '$(cat "$lab_dir/db-password")';"
        echo "grant authenticated, anon to lab_gateway; create table public.chat_lab_ready(id int); revoke all on public.chat_lab_ready from public, anon, authenticated;"
    } | docker exec -i "$db" psql -U postgres -Xq -1 -v ON_ERROR_STOP=1 >/dev/null
fi
if ! docker container inspect "$api" >/dev/null 2>&1; then
    docker run -d --name "$api" --network "$network" -p 127.0.0.1:18764:3000 \
      -e "PGRST_DB_URI=postgres://lab_gateway:$(cat "$lab_dir/db-password")@$db:5432/postgres" \
      -e PGRST_DB_SCHEMAS=public -e PGRST_DB_ANON_ROLE=anon \
      -e "PGRST_JWT_SECRET=$(cat "$lab_dir/jwt-secret")" postgrest/postgrest:v13.0.7 >/dev/null
else
    docker start "$api" >/dev/null
fi
python3 - "$lab_dir" <<'PY'
import base64, hashlib, hmac, json, pathlib, sys, time
directory = pathlib.Path(sys.argv[1])
secret = (directory / 'jwt-secret').read_text().strip().encode()
def encode(data):
    return base64.urlsafe_b64encode(data).decode().rstrip('=')
def jwt(user):
    header = encode(b'{"alg":"HS256","typ":"JWT"}')
    payload = encode(json.dumps(dict(sub=user, role='authenticated', exp=int(time.time())+7*86400)).encode())
    message = header + '.' + payload
    return message + '.' + encode(hmac.digest(secret, message.encode(), hashlib.sha256))
accounts = ['60000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000002']
(directory / 'tokens').write_text(''.join(jwt(user)+'\n' for user in accounts))
PY
echo "Local chat relay is on 127.0.0.1:18764. Run attach.sh after installing the chatLab debug build."
