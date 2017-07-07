
# push-notification

[![Build Status](https://travis-ci.org/hmrc/push-notification.svg?branch=master)](https://travis-ci.org/hmrc/push-notification) [ ![Download](https://api.bintray.com/packages/hmrc/releases/push-notification/images/download.svg) ](https://bintray.com/hmrc/releases/push-notification/_latestVersion)

The Push Notification service enables the delivery of push notifications to the HMRC mobile application.

### API

The following endpoints are exposed by this service:

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/push-notification/messages``` | POST | Create a new message, to be sent to one or more devices. [More...](docs/send.md)  |
| ```/push-notification/messages/:id/status``` | POST | Respond to a message. [More...](docs/respond.md)  |
| ```//push-notification/notifications/unsent``` | GET | Retrieve queued notifications. [More...](docs/queued.md) |
| ```//push-notification/notifications/status``` | POST | Update the status of notifications. [More...](docs/update.md) |
| ```//push-notification/messages/:id``` | POST | Retrieve message associated with Id and update state to answer. [More...](docs/getmessage.md) |
| ```//push-notification/messages/current``` | GET | Returns all messages that have not yet been answered or acknowledged. [More...](docs/getcurrentmessage.md) |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
    