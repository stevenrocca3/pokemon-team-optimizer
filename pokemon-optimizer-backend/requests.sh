#!/usr/bin/env bash
set -euo pipefail # script stops on errors, -e stops on command fail, -u stops on undf. var, pipefail stops if pipeline fails
BASE_URL="localhost:8080"

#name #expected_status #JSON_body
failures=0
function expect() 
{
    local name="$1"
    local expected="$2"
    local body="$3"
    local status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}"/api/collection -H 'Content-Type: application/json' -d "${body}")
    if [ "$status" = "$expected" ]; then 
        echo PASS "$name"
    else 
        failures=$((failures + 1))
        echo "FAIL $name (expected $expected, got $status)"
    fi
}
expect "valid" 201 '{"speciesId":"medicham","atkIv":1,"defIv":14,"staIv":15,"level":22.5}' # valid check
expect "invalidAtkIv" 400 '{"speciesId":"medicham","atkIv":16,"defIv":14,"staIv":15,"level":22.5}' # invalid atkIv
expect "invalidLevel" 400 '{"speciesId":"medicham","atkIv":1,"defIv":14,"staIv":15,"level":22.3}' # invalid level
expect "levelOverMax" 400 '{"speciesId":"medicham","atkIv":1,"defIv":14,"staIv":15,"level":52}' # level too high
expect "blankId" 400 '{"speciesId":"","atkIv":1,"defIv":14,"staIv":15,"level":22.5}' # blank id
expect "invalidId" 400 '{"speciesId":"notapokemon","atkIv":1,"defIv":14,"staIv":15,"level":22.5}' # invalid id

if [ $failures -gt 0 ]; then
    echo ""$failures" failures"
    exit 1
else
    echo "All passed"
fi
