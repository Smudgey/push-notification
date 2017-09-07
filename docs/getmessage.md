Get Message
----

* **URL**

  `/push-notification/message/:id`

* **Method:**

  `GET`

  Retrieve the message based on the supplied message Id and users authority. Message details will only be returned if 
  the current state of the message is either Acknowledge or Answer.
  
* **Response:**

  The details of push notification message:


```json
{
  "id":"msg-some-id",
  "subject": "Weather",
  "body": "Is it raining?",
  "responses": {
    "yes": "Yes",
    "no": "No"
  }
}
```

*  **URL Params**

   None
 
* **Success Response:**
  * **Code:** 200 <br />
  Message was created
* **Error Response:**

  * **Code:** 404 NOT_FOUND <br />
  No message was found. Note that message details will only be returned if the message was not previously acknowledged or answered.

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 403 FORBIDDEN <br />
    **Content:** `{"code":"FORBIDDEN","message":"Access denied"}`

  OR on failure.

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />