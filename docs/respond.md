Respond to Message
------------------

Introduction
============

There are two types of responses to messages:
* Acknowledge, which happens when we think that the user has seen the message, for example because it has been displayed to the user for 2 seconds (see NGC-1389).
* Answer, which happens when a user explicitly provides an answer to a message, for example by pressing a button to choose a response.

Details
=======
* **URL**

  `/push-notification/messages/:id/status`

* **Method:**

  `POST`

* **URL Params**

  `:id` The ID of the PushMessage to respond to.

* **Request Body:**

  The response to the message. The message ID in the body must match the `:id` URL path parameter.
  
  To Acknowledge: 

  ```json
  {
    "messageId": "msg-some-id" 
  }
  ```
  
  To Answer:

  ```json
  {
    "messageId": "msg-some-id",
    "answer": "yes"
  }
  ```

* **Success Responses:**
  * **Code:** 200 OK

  * **Code:** 202 Accepted <br />
  202 is returned when a request is made to acknowledged a message that was previously acknowledged, or to answer a message that was previously answered.
  In these cases the stored response is not updated, e.g. the first answer is retained and any other answers are ignored.

* **Error Responses:**

  * **Code:** 400 Bad Request <br />
    The request was invalid, for example the JSON supplied was invalid or the messageId in the path did not match the id in the payload.

  * **Code:** 404 Not Found <br />
    No message with the specified ID was found.

  OR on failure.

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />
