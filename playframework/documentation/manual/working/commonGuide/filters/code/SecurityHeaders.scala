/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */

package detailedtopics.configuration.securityheaders

object SecurityHeaders {

  //#filters
  import javax.inject.Inject

  import play.api.http.DefaultHttpFilters
  import play.filters.headers.SecurityHeadersFilter

  class Filters @Inject() (securityHeadersFilter: SecurityHeadersFilter) extends DefaultHttpFilters(securityHeadersFilter)
  //#filters

  import play.api.mvc.Action
  import play.api.mvc.Results.Ok
  def index = Action {
  //#allowActionSpecificHeaders
  	Ok("Index").withHeaders(SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER -> "my page-specific header")
  //#allowActionSpecificHeaders
  }
}
