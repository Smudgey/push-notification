Get Message
----

* **URL**

  `/push-notification/messages/current`

* **Method:**

  `GET`

  Retrieves all messages that have not yet been answered or acknowledged based on a users authority.

* **Response:**

    If the message was created successfully, a message id will be returned.


```json
{
  "messages": [
    {
      "subject": "snarkle",
      "body": "Foo, bar baz!",
      "callbackUrl": "http://example.com/quux",
      "responses": {
        "yes": "Sure",
        "no": "Nope"
      },
      "messageId": "msg-some-id"
    },
    {
      "subject": "stumble",
      "body": "Alpha, Bravo!",
      "callbackUrl": "http://abstract.com/",
      "responses": {
        "yes": "Sure",
        "no": "Nope"
      },
      "messageId": "msg-other-id"
    }
  ]
}```

*  **URL Params**

   None
 
* **Success Response:**
  * **Code:** 200 <br />
  
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 403 FORBIDDEN <br />
    **Content:** `{"code":"FORBIDDEN","message":"Access denied"}`

  OR on failure.

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />