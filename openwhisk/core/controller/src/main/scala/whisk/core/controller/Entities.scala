/*
 * Copyright 2015-2016 IBM Corporation
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

package whisk.core.controller

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import spray.http.StatusCodes.InternalServerError
import spray.routing.ConjunctionMagnet.fromDirective
import spray.routing.Directive.pimpApply
import spray.routing.Directives
import spray.routing.RequestContext
import spray.routing.directives.OnCompleteFutureMagnet.apply
import whisk.common.TransactionId
import whisk.core.entitlement.Privilege
import whisk.core.entitlement.Privilege.ACTIVATE
import whisk.core.entitlement.Privilege.DELETE
import whisk.core.entitlement.Privilege.PUT
import whisk.core.entitlement.Privilege.READ
import whisk.core.entitlement.Resource
import whisk.core.entity.EntityName
import whisk.core.entity.Namespace
import whisk.core.entity.Parameters
import whisk.core.entity.Subject
import whisk.http.ErrorResponse.terminate
import scala.language.postfixOps
import whisk.core.entity.WhiskAuth
import whisk.core.entity.Subject

/** A trait implementing the basic operations on WhiskEntities in support of the various APIs. */
trait WhiskCollectionAPI
    extends Directives
    with AuthenticatedRouteProvider
    with AuthorizedRouteProvider
    with ReadOps
    with WriteOps {

    /** The core collections require backend services to be injected in this trait. */
    services: WhiskServices =>

    /** Creates an entity, or updates an existing one, in namespace. Terminates HTTP request. */
    protected def create(namespace: Namespace, name: EntityName)(implicit transid: TransactionId): RequestContext => Unit

    /** Activates entity. Examples include invoking an action, firing a trigger, enabling/disabling a rule. */
    protected def activate(user: WhiskAuth, namespace: Namespace, name: EntityName, env: Option[Parameters])(implicit transid: TransactionId): RequestContext => Unit

    /** Removes entity from namespace. Terminates HTTP request. */
    protected def remove(namespace: Namespace, name: EntityName)(implicit transid: TransactionId): RequestContext => Unit

    /** Gets entity from namespace. Terminates HTTP request. */
    protected def fetch(namespace: Namespace, name: EntityName, env: Option[Parameters])(implicit transid: TransactionId): RequestContext => Unit

    /** Gets all entities from namespace. If necessary filter only entities that are shared. Terminates HTTP request. */
    protected def list(namespace: Namespace, excludePrivate: Boolean)(implicit transid: TransactionId): RequestContext => Unit

    /** Indicates if listing entities in collection requires filtering out private entities. */
    protected val listRequiresPrivateEntityFilter = false // currently supported on PACKAGES only

    /** Dispatches resource to the proper handler depending on context. */
    protected override def dispatchOp(user: WhiskAuth, op: Privilege, resource: Resource)(implicit transid: TransactionId) = {
        resource.entity match {
            case Some(EntityName(name)) => op match {
                case READ     => fetch(resource.namespace, name, resource.env)
                case PUT      => create(resource.namespace, name)
                case ACTIVATE => activate(user, resource.namespace, name, resource.env)
                case DELETE   => remove(resource.namespace, name)
                case _        => reject
            }
            case None => op match {
                case READ =>
                    // the entitlement service will authorize any subject to list PACKAGES
                    // in any namespace regardless of ownership but the list operation CANNOT
                    // produce all entities in the requested namespace UNLESS the subject is
                    // entitled to them which for now means they own the namespace. If the
                    // subject does not own the namespace, then exclude packages that are private
                    val checkIfSubjectOwnsResource = if (listRequiresPrivateEntityFilter) {
                        entitlementService.namespaces(user.subject) map {
                            _.contains(resource.namespace.root()) == false
                        }
                    } else Future.successful { false }

                    onComplete(checkIfSubjectOwnsResource) {
                        case Success(excludePrivate) =>
                            info(this, s"[LIST] exclude private entities: required == $excludePrivate")
                            list(resource.namespace, excludePrivate)
                        case Failure(t) =>
                            error(this, s"[LIST] namespaces lookup failed: ${t.getMessage}")
                            terminate(InternalServerError, t.getMessage)

                    }
                case _ => reject
            }
        }
    }

    /** Validates entity name from the matched path segment. */
    protected override final def entityname(s: String) = {
        validate(isEntity(s), s"name '$s' contains illegal characters") & extract(_ => s)
    }

    /** Confirms that a path segment is a valid entity name. Used to reject invalid entity names. */
    protected final def isEntity(n: String) = Try { EntityName(n) } isSuccess
}
