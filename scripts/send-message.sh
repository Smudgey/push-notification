#!/usr/bin/env bash

export AUTH_TOKEN="Bearer _bearer_token_value_"
export NOTIFICATION_HOST=localhost
export NOTIFICATION_PORT=8246

result=`curl -i -o - --silent -X POST -H "Content-Type: application/json" -H "Accept: application/vnd.hmrc.1.0+json" -H "Authorization: ${AUTH_TOKEN}" -d '{
"id" : "NGC_001",
"params" : { }
}' "${NOTIFICATION_HOST}:${NOTIFICATION_PORT}/messages" | head -1 | cut -f2 -d' '`

case ${result} in
201)
    echo "Message accepted."
    ;;
404)
    echo "Message could not be created. Check that the user has registered devices!"
    ;;
*)
    echo "Unexpected failure..."
    ;;
esac