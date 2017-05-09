package io.iohk.ethereum.jsonrpc.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import io.iohk.ethereum.jsonrpc.http.JsonRpcHttpServer.JsonRpcHttpServerConfig
import io.iohk.ethereum.jsonrpc.{JsonRpcController, JsonRpcRequest}
import io.iohk.ethereum.utils.Logger
import org.json4s.{DefaultFormats, native}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

class JsonRpcHttpServer(jsonRpcController: JsonRpcController, config: JsonRpcHttpServerConfig)
                       (implicit val actorSystem: ActorSystem)
  extends Json4sSupport with Logger {

  implicit val serialization = native.Serialization

  implicit val formats = DefaultFormats

  val route: Route = {
    (pathEndOrSingleSlash & post & entity(as[JsonRpcRequest])) { request =>
      handleRequest(request)
    } ~ post {
      extractRequest { r =>
        log.debug(s"got unsupported request with ${r.entity}")
        complete(HttpResponse(StatusCodes.BadRequest))
      }
    }
  }

  def run(): Unit = {
    implicit val materializer = ActorMaterializer()

    val bindingResultF = Http(actorSystem).bindAndHandle(route, config.interface, config.port)

    bindingResultF onComplete {
      case Success(serverBinding) => log.info(s"JSON RPC server listening on ${serverBinding.localAddress}")
      case Failure(ex) => log.error("Cannot start JSON RPC server", ex)
    }
  }

  private def handleRequest(request: JsonRpcRequest) = {
    complete(jsonRpcController.handleRequest(request))
  }

}

object JsonRpcHttpServer {

  trait JsonRpcHttpServerConfig {
    val enabled: Boolean
    val interface: String
    val port: Int
  }

}