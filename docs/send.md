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

* **Response:**

    If the message was created successfully, a message id will be returned.

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