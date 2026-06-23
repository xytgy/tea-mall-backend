#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.prodlike.yml"
ENV_FILE="${ROOT_DIR}/.env.prodlike"

NACOS_ADDR_LOCAL="http://localhost:8848"
NACOS_NAMESPACE_NAME="prodlike-local"
NACOS_GROUP="DEFAULT_GROUP"
NACOS_DATA_ID="tea-mall-backend.yaml"
NACOS_CONFIG_FILE="${ROOT_DIR}/docker/nacos/init/tea-mall-backend.yaml"

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32
    return 0
  fi
  python3 - <<'PY'
import os,base64
print(base64.b64encode(os.urandom(32)).decode())
PY
}

ensure_env_file() {
  if [[ -f "${ENV_FILE}" ]]; then
    return 0
  fi

  cat > "${ENV_FILE}" <<EOF
MYSQL_ROOT_PASSWORD=$(random_secret)
REDIS_PASSWORD=$(random_secret)
NACOS_MYSQL_PASSWORD=$(random_secret)
ES_PASSWORD=$(random_secret)
JWT_SECRET=$(random_secret)
NACOS_NAMESPACE=
EOF
}

set_env_var() {
  local key="$1"
  local value="$2"
  python3 - "${ENV_FILE}" "${key}" "${value}" <<'PY'
import sys

path, key, value = sys.argv[1], sys.argv[2], sys.argv[3]

lines = []
found = False
with open(path, "r", encoding="utf-8") as f:
    for line in f:
        if line.startswith(f"{key}="):
            lines.append(f"{key}={value}\n")
            found = True
        else:
            lines.append(line)

if not found:
    if lines and not lines[-1].endswith("\n"):
        lines[-1] = lines[-1] + "\n"
    lines.append(f"{key}={value}\n")

with open(path, "w", encoding="utf-8") as f:
    f.writelines(lines)
PY
}

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

wait_nacos() {
  local max_seconds="${1:-180}"
  local start_ts
  start_ts="$(date +%s)"

  while true; do
    if curl -fsS "${NACOS_ADDR_LOCAL}/nacos/" >/dev/null 2>&1; then
      return 0
    fi
    if (( "$(date +%s)" - start_ts > max_seconds )); then
      echo "Nacos 未在 ${max_seconds}s 内就绪：${NACOS_ADDR_LOCAL}" >&2
      return 1
    fi
    sleep 2
  done
}

get_namespace_id() {
  curl -fsS "${NACOS_ADDR_LOCAL}/nacos/v1/console/namespaces" | python3 - "${NACOS_NAMESPACE_NAME}" <<'PY'
import json,sys

name = sys.argv[1]
payload = json.load(sys.stdin)
for item in payload.get("data", []):
    if item.get("namespaceShowName") == name:
        print(item.get("namespace", ""))
        sys.exit(0)
print("")
PY
}

ensure_namespace() {
  local ns_id
  ns_id="$(get_namespace_id)"
  if [[ -n "${ns_id}" ]]; then
    echo "${ns_id}"
    return 0
  fi

  curl -fsS -X POST "${NACOS_ADDR_LOCAL}/nacos/v1/console/namespaces" \
    -d "namespaceName=${NACOS_NAMESPACE_NAME}" \
    -d "namespaceDesc=local prodlike namespace" >/dev/null

  ns_id="$(get_namespace_id)"
  if [[ -z "${ns_id}" ]]; then
    echo "创建 Nacos namespace 失败：${NACOS_NAMESPACE_NAME}" >&2
    return 1
  fi

  echo "${ns_id}"
}

publish_config() {
  local ns_id="$1"
  curl -fsS -X POST "${NACOS_ADDR_LOCAL}/nacos/v1/cs/configs" \
    -d "dataId=${NACOS_DATA_ID}" \
    -d "group=${NACOS_GROUP}" \
    -d "tenant=${ns_id}" \
    -d "type=yaml" \
    --data-urlencode "content@${NACOS_CONFIG_FILE}" >/dev/null
}

cmd_up() {
  ensure_env_file

  compose up -d \
    mysql redis \
    rocketmq-namesrv rocketmq-broker rocketmq-broker-slave \
    elasticsearch zipkin \
    nacos1 nacos2 nacos3

  wait_nacos 240

  local ns_id
  ns_id="$(ensure_namespace)"
  publish_config "${ns_id}"

  # 先写入 env 再启动后端，避免后端启动后仍停留在 public namespace
  set_env_var "NACOS_NAMESPACE" "${ns_id}"

  compose up -d --build backend

  echo "后端：http://localhost:8082"
  echo "Nacos：${NACOS_ADDR_LOCAL}/nacos"
  echo "ES：http://localhost:9200"
  echo "Zipkin：http://localhost:9411"
}

cmd_down() {
  ensure_env_file
  compose down
}

cmd_clean() {
  ensure_env_file
  compose down -v
}

case "${1:-up}" in
  up) cmd_up ;;
  down) cmd_down ;;
  clean) cmd_clean ;;
  *)
    echo "用法：bash dev.sh [up|down|clean]" >&2
    exit 1
    ;;
esac

