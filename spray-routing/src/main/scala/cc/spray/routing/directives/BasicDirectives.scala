/*
 * Copyright (C) 2011-2012 spray.cc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.routing
package directives

import cc.spray.http.{HttpResponse, HttpRequest}
import cc.spray.util.identityFunc
import shapeless._


trait BasicDirectives {

  def transformInnerRoute(f: Route => Route): Directive0 = new Directive0 {
    def happly(inner: HNil => Route) = f(inner(HNil))
  }

  def transformRequestContext(f: RequestContext => RequestContext): Directive0 =
    transformInnerRoute { inner => ctx => inner(f(ctx)) }

  def transformRequest(f: HttpRequest => HttpRequest): Directive0 =
    transformInnerRoute { inner => ctx => inner(ctx.withRequestTransformed(f)) }

  def transformRouteResponse(f: Any => Any): Directive0 =
    transformInnerRoute { inner => ctx => inner(ctx.withRouteResponseTransformed(f)) }

  def transformHttpResponse(f: HttpResponse => HttpResponse): Directive0 =
    transformInnerRoute { inner => ctx => inner(ctx.withHttpResponseTransformed(f)) }

  def transformRouteResponsePF(f: PartialFunction[Any, Any]): Directive0 =
    transformInnerRoute { inner => ctx => inner(ctx.withRouteResponseTransformedPF(f)) }

  def filter[T <: HList](f: RequestContext => FilterResult[T])
                        (implicit fdb: FilteringDirectiveBuilder[T]): fdb.Out = fdb(f)

  def nop = transformInnerRoute(identityFunc)

  def provide[L <: HList](values: L) = new Directive[L] {
    def happly(f: L => Route) = f(values)
  }
}

object BasicDirectives extends BasicDirectives


sealed abstract class FilteringDirectiveBuilder[T <: HList] {
  type Out <: Directive[T]
  def apply(f: RequestContext => FilterResult[T]): Out
}

object FilteringDirectiveBuilder extends LowerPriorityFilteringDirectiveBuilders {
  implicit val fdb0 = new FilteringDirectiveBuilder[HNil] {
    type Out = Directive0
    def apply(filter: RequestContext => FilterResult[HNil]) = BasicDirectives.transformInnerRoute { inner => ctx =>
      filter(ctx) match {
        case Pass(HNil, transform) => inner(transform(ctx))
        case Reject(rejections) => ctx.reject(rejections: _*)
      }
    }
  }
}

sealed abstract class LowerPriorityFilteringDirectiveBuilders {
  implicit def fdb[L <: HList] = new FilteringDirectiveBuilder[L] {
    type Out = Directive[L]
    def apply(filter: RequestContext => FilterResult[L]) = new Out {
      def happly(inner: L => Route) = { ctx =>
        filter(ctx) match {
          case Pass(values, transform) => inner(values)(transform(ctx))
          case Reject(rejections) => ctx.reject(rejections: _*)
        }
      }
    }
  }
}