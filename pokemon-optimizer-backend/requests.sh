#!/usr/bin/env bash
set -euo pipefail # script stops on errors, -e stops on command fail, -u stops on undf. var, pipefail stops if pipeline fails
BASE_URL="localhost:8080"
RESPONSE_FILE=$(mktemp)

#name #expected_status #JSON_body
failures=0
function expect() 
{
    local name="$1"
    local expected="$2"
    local body="$3"
    local status=$(curl -s -o "$RESPONSE_FILE" -w "%{http_code}" -X POST "${BASE_URL}"/api/collection -H 'Content-Type: application/json' -d "${body}")
    if [ "$status" = "$expected" ]; then 
        echo PASS "$name"
    else 
        failures=$((failures + 1))
        echo "FAIL $name (expected $expected, got $status)"
    fi
}

function expect_delete()
{
    local name="$1"
    local expected="$2"
    local id="$3"
    local status=$(curl -s -o "$RESPONSE_FILE" -w "%{http_code}" -X DELETE "$BASE_URL/api/collection/$id")
    if [ "$status" = "$expected" ]; then 
        echo DELETE "$name"
    else 
        failures=$((failures + 1))
        echo "FAIL $name (expected $expected, got $status)"
    fi
}
expect "valid" 201 '{"speciesId":"medicham","atkIv":1,"defIv":14,"staIv":15,"level":22.5}' # valid check
created_id=$(jq -r '.id' "$RESPONSE_FILE")
expect "invalidAtkIv" 400 '{"speciesId":"medicham","atkIv":16,"defIv":14,"staIv":15,"level":22.5}' # invalid atkIv
expect "invalidLevel" 400 '{"speciesId":"medicham","atkIv":1,"defIv":14,"staIv":15,"level":22.3}' # invalid level
expect "levelOverMax" 400 '{"speciesId":"medicham","atkIv":1,"defIv":14,"staIv":15,"level":52}' # level too high
expect "blankId" 400 '{"speciesId":"","atkIv":1,"defIv":14,"staIv":15,"level":22.5}' # blank id
expect "invalidId" 400 '{"speciesId":"notapokemon","atkIv":1,"defIv":14,"staIv":15,"level":22.5}' # invalid id
expect_delete "deleted created" 204 "$created_id"
expect_delete "deleted failed" 404 "$created_id"
if [ "$failures" -gt 0 ]; then
    echo ""$failures" failures"
    exit 1
else
    echo "All passed"
fi
