Get Message
----

* **URL**

  `/push-notification/message/:id`

* **Method:**

  `POST`

  Retrieve the message based on the supplied message Id and users authority. Only if the found message state is either Acknowledge or Answer
  will the message be returned.
  An empty JSON request is required to be supplied to the service.

* **Response:**

    If the message was created successfully, a message id will be returned.


```json
{
  "id":"msg-some-id",
  "subject": "Weather",
  "body": "Is it raining?",
  "responses": {
    "yes": "Yes",
    "no": "No"
  }
}```

*  **URL Params**

   None
 
* **Success Response:**
  * **Code:** 200 <br />
  Message was created
* **Error Response:**

  * **Code:** 404 NOT_FOUND <br />
  The user does not have any registered devices.

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 403 FORBIDDEN <br />
    **Content:** `{"code":"FORBIDDEN","message":"Access denied"}`

  OR on failure.

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />