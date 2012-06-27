package com.twitter.finagle.protobuf.rpc.impl

import java.util.concurrent.ExecutorService
import com.twitter.finagle.protobuf.rpc.RpcControllerWithOnFailureCallback
import com.twitter.finagle.protobuf.rpc.channel.ProtoBufCodec
import com.twitter.finagle.ChannelClosedException
import com.twitter.finagle.protobuf.rpc.Util

class RpcChannelImpl(cb: ClientBuilder[(String, Message), (String, Message), Any, Any, Any], s: Service, executorService: ExecutorService) extends RpcChannel {

  private val log = LoggerFactory.getLogger(getClass)

  private val futurePool = FuturePool(executorService)

  private val client: com.twitter.finagle.Service[(String, Message), (String, Message)] = cb
    .codec(new ProtoBufCodec(s))
    .unsafeBuild()

  def callMethod(m: MethodDescriptor, controller: RpcController,
    request: Message, responsePrototype: Message,
    done: RpcCallback[Message]): Unit = {

    Util.log("Request", m.getName(), request)
    val req = (m.getName(), request)

    client(req) onSuccess { result =>
      Util.log("Response", m.getName(), result._2)
      futurePool({ done.run(result._2) })
    } onFailure { e =>
      log.warn("Failed. ", e)
      controller.asInstanceOf[RpcControllerWithOnFailureCallback].setFailed(e)
    }
  }

  def release() {
     client.close()
  }
}
