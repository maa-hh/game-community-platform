#!/bin/bash

load_services() {
  local conf_file="$1"
  SERVICES=()
  SERVICE_MODULES=()
  SERVICE_PORTS=()

  while IFS='|' read -r name module port; do
    [[ -z "$name" || "$name" =~ ^# ]] && continue
    SERVICES+=("$name")
    SERVICE_MODULES+=("$module")
    SERVICE_PORTS+=("$port")
  done < "$conf_file"
}

get_service_index() {
  local target="$1"
  local i
  for i in "${!SERVICES[@]}"; do
    if [[ "${SERVICES[$i]}" == "$target" ]]; then
      echo "$i"
      return 0
    fi
  done
  return 1
}
