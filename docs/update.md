Update Notifications Status
---------------------------

* **URL**

  `/push-notification/notifications/status`

* **Method:**

  `POST`

* **URL Params**

   None

* **Request Body:**

  Multiple notifications can be updated in one request.

  The request body is a map of `notificationId` to notification status, for example:

  ```json
  {
    "id1": "delivered",
    "id2": "sent"
  }
  ```

  For a list of notification statuses see [NotificationStatus](../app/uk/gov/hmrc/pushnotification/domain/Notification.scala#L26)

* **Response:**

    If all notifications were updated successfully a 204 status will be returned.
    If some notifications were updated successfully (but some updates failed) a 202 status will be returned.

* **Success Response:**
  * **Code:** 204 No Content <br />
  All notifications were updated successfully

* **Partial Success Response:**
  * **Code:** 202 Accepted <br />
  Some notifications were updated successfully (but some updates failed)

* **Error Response:**

  * **Code:** 400 Bad Request <br />
    The request was invalid, for example an unknown notification status was used.

  OR on failure.

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />
