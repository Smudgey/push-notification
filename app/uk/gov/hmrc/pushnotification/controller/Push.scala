package uk.gov.hmrc.pushnotification.controller

import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController

trait Push extends BaseController {

  def ping() = Action{Ok}
}

object Push extends Push