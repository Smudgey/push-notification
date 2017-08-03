Send Message
----

* **URL**

  `/push-notification/messages`

* **Method:**

  `POST`

  Send a notification message, which will appear as an in-app notification on all the user's registered devices.
  Hence a single message may result in multiple notifications if a user has more than one registered device.

  The id provided is the template identifier.  A template may contain parameters, which will replace placeholder text in the template:

  ```json
  {
    "id": "NGC_002",
    "parameters": {
      "fullName" : "Jane Jones",
      "agent" : "Acme Accounting Ltd",
      "callbackUrl" : "https://some.other.service/callback"  
     }
  }
  ```

  Note that a Bearer token must be included in the Authorization header, for example:

  ```
  curl -i -X POST -H "Content-Type: application/json" -H "Accept: application/vnd.hmrc.1.0+json" -H "Authorization: Bearer XlLM91CY3hEHqHlrKX9N0Y/F6eZJ6EhaSp4de7G6IuHLQSN2EtQPOmlZdpm4/eshG9yj2bxLY9bGVbbkcKBM0BKyztGq5csF60bCaqNfkPeOJvkZ5TQDDnf38fa3lhT03yxYiM08RPthxiPZtbaO8yhf65/Q7jWj5JuFl60avD01TnU/CoN5cH3wc88qbn82" -d '{
    "id": "NGC_002",
    "parameters": {
      "fullName" : "Jane Jones",
      "agent" : "Acme Accounting Ltd",
      "callbackUrl" : "https://some.other.service/callback"  
     }
  }' "localhost:8246/messages"
  ```


* **Response:**

    If the message was created successfully a 200 status will be returned. Note that a message id will be returned only if the message request includes a callbackUrl.

```json
{
  "id" : "c59e6746-9cd8-454f-a4fd-c5dc42db7d99"
}
```

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
