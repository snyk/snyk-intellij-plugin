package io.snyk.plugin

import fi.iki.elonen.NanoHTTPD.Response

package object embeddedserver {
  type Processor = (String, ParamSet) => Response
}
