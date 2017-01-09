package org.folio.metadata.common

import io.vertx.groovy.ext.web.RoutingContext

class WebContext implements Context {
  private final RoutingContext routingContext

  WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext
  }

  @Override
  String getTenantId() {
    getHeader("X-Okapi-Tenant", "")
  }

  @Override
  String getOkapiLocation() {
    getHeader("X-Okapi-Url", "")
  }

  def getHeader(String header) {
    routingContext.request().getHeader(header)
  }

  def getHeader(String header, defaultValue) {
    hasHeader(header) ? getHeader(header) : defaultValue
  }

  def URL absoluteUrl(String path) {
    def currentRequestUrl = new URL(routingContext.request().absoluteURI())

    //It would seem Okapi preserves headers from the original request,
    // so there is no need to use X-Okapi-Url for this?

    new URL(currentRequestUrl.protocol, currentRequestUrl.host,
      currentRequestUrl.port, path)
  }

  private boolean hasHeader(String header) {
    routingContext.request().headers().contains(header)
  }
}