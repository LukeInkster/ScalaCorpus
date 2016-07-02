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

package whisk.core.loadBalancer

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import spray.json.JsBoolean
import spray.json.JsObject
import whisk.common.ConsulClient
import whisk.common.ConsulKV.LoadBalancerKeys
import whisk.common.ConsulKVReporter
import whisk.common.Logging
import whisk.common.TransactionId
import whisk.common.Verbosity
import whisk.connector.kafka.KafkaConsumerConnector
import whisk.connector.kafka.KafkaProducerConnector
import whisk.core.WhiskConfig
import whisk.core.WhiskConfig.consulServer
import whisk.core.WhiskConfig.kafkaHost
import whisk.core.WhiskConfig.kafkaPartitions
import whisk.core.WhiskConfig.servicePort
import whisk.core.connector.ActivationMessage
import whisk.core.connector.CompletionMessage
import whisk.core.entity.ActivationId
import whisk.core.entity.WhiskActivation
import whisk.utils.ExecutionContextFactory.PromiseExtensions

class LoadBalancerService(config: WhiskConfig, verbosity: Verbosity.Level)(
    implicit val actorSystem: ActorSystem,
    val executionContext: ExecutionContext)
    extends LoadBalancerToKafka
    with Logging {

    /**
     * The two public methods are getInvokerHealth and the inherited doPublish methods.
     */
    def getInvokerHealth(): JsObject = invokerHealth.getInvokerHealthJson()

    override def getInvoker(message: ActivationMessage): Option[Int] = invokerHealth.getInvoker(message)
    override def activationThrottle = _activationThrottle

    override val producer = new KafkaProducerConnector(config.kafkaHost, executionContext)

    private val invokerHealth = new InvokerHealth(config, resetIssueCountByInvoker, () => producer.sentCount())
    private val _activationThrottle = new ActivationThrottle(config)

    // map stores the promise which either completes if an active response is received or
    // after the set timeout expires
    private val queryMap = new TrieMap[ActivationId, Promise[WhiskActivation]]

    // this must happen after the overrides
    setVerbosity(verbosity)

    private var count = 0
    private val overloadThreshold = 5000 // this is the total across all invokers.  Disable by setting to -1.
    private val kv = new ConsulClient(config.consulServer)
    private val reporter = new ConsulKVReporter(kv, 3 seconds, 2 seconds,
        LoadBalancerKeys.hostnameKey,
        LoadBalancerKeys.startKey,
        LoadBalancerKeys.statusKey,
        { () =>
            val issuedCounts = getIssueCountByInvoker()
            val issuedCount = issuedCounts.foldLeft(0)(_ + _._2)
            val health = invokerHealth.getInvokerHealth()
            val invokerCounts = invokerHealth.getInvokerActivationCounts()
            val completedCounts = invokerCounts map { indexCount => indexCount._2 }
            val completedCount = completedCounts.foldLeft(0)(_ + _)
            val inFlight = issuedCount - completedCount
            val overload = JsBoolean(overloadThreshold > 0 && inFlight >= overloadThreshold)
            count = count + 1
            if (count % 10 == 0) {
                warn(this, s"In flight: $issuedCount - $completedCount = $inFlight $overload")
                info(this, s"Issued counts: [${issuedCounts.mkString(", ")}]")
                info(this, s"Completion counts: [${invokerCounts.mkString(", ")}]")
                info(this, s"Invoker health: [${health.mkString(", ")}]")
            }
            Map(LoadBalancerKeys.overloadKey -> overload,
                LoadBalancerKeys.invokerHealth -> getInvokerHealth()) ++
                getUserActivationCounts()
        })

    /**
     * Tries to fill in the result slot (i.e., complete the promise) when a completion message arrives.
     * The promise is removed form the map when the result arrives or upon timeout.
     *
     * @param msg is the kafka message payload as Json
     */
    def processCompletion(msg: CompletionMessage) = {
        implicit val tid = msg.transid
        val aid = msg.response.activationId
        val response = msg.response
        val promise = queryMap.remove(aid)
        promise map { p =>
            p.trySuccess(response)
            info(this, s"processed active response for '$aid'")
        }
    }

    /**
     * Tries for a while to get a WhiskActivation from the fast path instead of hitting the DB.
     * Instead of sleep/poll, the promise is filled in when the completion messages arrives.
     * If for some reason, there is no ack, promise eventually times out and the promise is removed.
     */
    def queryActivationResponse(activationId: ActivationId, transid: TransactionId): Future[WhiskActivation] = {
        implicit val tid = transid
        // either create a new promise or reuse a previous one for this activation if it exists
        queryMap.getOrElseUpdate(activationId, {
            val promise = Promise[WhiskActivation]
            // store the promise to complete on success, and the timed future that completes
            // with the TimeoutException after alloted time has elapsed
            promise after (30 seconds, {
                queryMap.remove(activationId) map { p =>
                    val failedit = p.tryFailure(new ActiveAckTimeout(activationId))
                    if (failedit) info(this, "active response timed out")
                }
            })
        }).future
    }

    val consumer = new KafkaConsumerConnector(config.kafkaHost, "completions", "completed")
    consumer.setVerbosity(verbosity)
    consumer.onMessage((topic, bytes) => {
        val raw = new String(bytes, "utf-8")
        CompletionMessage(raw) match {
            case Success(m) => processCompletion(m)
            case Failure(t) => error(this, s"failed processing message: $raw with $t")
        }
        true
    })

}

object LoadBalancerService {
    def requiredProperties =
        Map(servicePort -> null) ++
            kafkaHost ++
            consulServer ++
            Map(kafkaPartitions -> null) ++
            InvokerHealth.requiredProperties ++
            ActivationThrottle.requiredProperties
}

private case class ActiveAckTimeout(activationId: ActivationId) extends TimeoutException
