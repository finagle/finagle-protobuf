package com.twitter.finagle.protobuf.rpc.channel

import org.jboss.netty.channel._
import com.google.protobuf.Message
import com.google.protobuf.{Service => ProtobufService}
import com.twitter.conversions.storage.intToStorageUnitableWholeNumber
import com.twitter.finagle._
import com.twitter.finagle.tracing.{Annotation, Trace}
import org.slf4j.LoggerFactory
import org.jboss.netty.handler.codec.frame.FrameDecoder
import org.jboss.netty.buffer.ChannelBuffer
import com.twitter.finagle
import com.twitter.util.Future

object ProtobufCodecFactory {
  private[channel] val logger = LoggerFactory.getLogger(classOf[ProtobufCodecFactory])
}

/**
  * Creates Finagle codecs for protobuf clients and servers.
  */
class ProtobufCodecFactory(val rpcService: ProtobufService, val serviceName: String, val version: Int = 1)
  extends CodecFactory[ProtobufRequest, ProtobufResponse] {

  private[this] val methodService = SimpleMethodService(rpcService.getDescriptorForType)

  val maxFrameSize = 1.megabytes.inBytes.intValue

  def server = { config => new ServerCodec(methodService, rpcService, serviceName, version) }

  def client = { config => new ClientCodec(methodService, rpcService, serviceName, version) }

}

private[channel] trait ValidateVersion {
  def isValid(version: Int): Boolean = {
    version match {
      case 0 => true
      case 1 => true
      case _ => false
    }
  }
}

private[channel] class ServerCodec(val methodService: MethodService, val rpcService: ProtobufService, val
serviceName: String, val version: Int = 1)
  extends Codec[ProtobufRequest, ProtobufResponse] with ValidateVersion {

  import ProtobufCodecFactory._

  logger.info("creating server codec (version: {}): {}", version, serviceName)
  if (!isValid(version)) {
    throw new RuntimeException("invalid protobuf server codec version: " + version + ", service name: " + serviceName
      + ", " + rpcService)
  }

  def pipelineFactory = new ChannelPipelineFactory {
    def getPipeline = {
      val pipeline = Channels.pipeline()
      pipeline.addLast("decoder", createDecoder(version))
      pipeline.addLast("encoder", new ServerSideEncoderV0(methodService))
      pipeline
    }
  }

  def createDecoder(version: Int): FrameDecoder = {
    version match {
      case 0 => new ServerSideDecoderV0(methodService, rpcService)
      case 1 => new ServerSideDecoderV1(methodService, rpcService)
      case _ => throw new RuntimeException("invalid server decoder version: " + version)
    }
  }

  /**
    * Prepare a connection factory. Used to allow codec modifications to the service at the bottom of the stack
    * (connection level).
    * Add tracing filter
    */
  override def prepareConnFactory(
    underlying: ServiceFactory[ProtobufRequest, ProtobufResponse], params: Stack.Params):
  ServiceFactory[ProtobufRequest, ProtobufResponse] = {
    logger.debug("server prepareConnFactory()")
    new ServerTracingFilter(serviceName) andThen super.prepareConnFactory(underlying, params)
  }

  /**
    * Prepare a factory for usage with the codec. Used to allow codec modifications to the service at the top of the
    * network stack.
    */
  override def prepareServiceFactory(
    underlying: ServiceFactory[ProtobufRequest, ProtobufResponse]): ServiceFactory[ProtobufRequest, ProtobufResponse]
  = {
    logger.debug("server prepareServiceFactory()")
    new ServerNetworkFilter(serviceName) andThen super.prepareServiceFactory(underlying)
  }
}

private[channel] class ClientCodec(val methodService: MethodService, val rpcService: ProtobufService, val
serviceName: String, val version: Int = 1)
  extends Codec[ProtobufRequest, ProtobufResponse] with ValidateVersion {

  import ProtobufCodecFactory._

  logger.info("creating client codec (version: {}): {}", version, serviceName)
  if (!isValid(version)) {
    throw new RuntimeException("invalid protobuf client codec version: " + version + ", service name: " + serviceName
      + ", " + rpcService)
  }

  def pipelineFactory = new ChannelPipelineFactory {
    def getPipeline = {
      val pipeline = Channels.pipeline()
      pipeline.addLast("encoder", createEncoder(version))
      pipeline.addLast("decoder", new ClientSideDecoderV0(methodService, rpcService))
      pipeline
    }
  }

  def createEncoder(version: Int): ChannelDownstreamHandler = {
    version match {
      case 0 => new ClientSideEncoderV0(methodService)
      case 1 => new ClientSideEncoderV1(methodService)
      case _ => throw new RuntimeException("invalid server decoder version: " + version)
    }
  }

  /**
    * Prepare a connection factory. Used to allow codec modifications to the service at the bottom of the stack
    * (connection level).
    * Add tracing filter
    */
  override def prepareConnFactory(
    underlying: ServiceFactory[ProtobufRequest, ProtobufResponse], params: Stack.Params):
  ServiceFactory[ProtobufRequest, ProtobufResponse] = {
    logger.debug("client prepareConnFactory()")
    new ClientTracingFilter(serviceName) andThen super.prepareConnFactory(underlying, params)
  }

  /**
    * Prepare a factory for usage with the codec. Used to allow codec modifications to the service at the top of the
    * network stack.
    */
  override def prepareServiceFactory(
    underlying: ServiceFactory[ProtobufRequest, ProtobufResponse]): ServiceFactory[ProtobufRequest, ProtobufResponse]
  = {
    logger.debug("client prepareServiceFactory()")
    new ClientNetworkFilter(serviceName) andThen super.prepareServiceFactory(underlying)
  }
}

private[channel] class ClientTracingFilter(serviceName: String) extends SimpleFilter[ProtobufRequest,
  ProtobufResponse] {

  import ProtobufCodecFactory._

  override def apply(request: ProtobufRequest, service: finagle.Service[ProtobufRequest, ProtobufResponse]):
  Future[ProtobufResponse] = {
    val methodName = request.methodName
    logger.debug(s"client send: tracing(${Trace.isActivelyTracing}, " +
      s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")

    Trace.recordServiceName(serviceName)
    Trace.recordRpc(methodName)

    val startTime: Long = System.currentTimeMillis()
    Trace.record(Annotation.ClientSend())

    service(request) map { response =>
      val endTime = System.currentTimeMillis()
      Trace.record(s"client receive: elapsed: ${endTime - startTime}ms")
      Trace.record(Annotation.ClientRecv())

      logger.trace(s"response: ${response.getClass.getName} -> $response")
      logger.debug(s"client receive: tracing(${Trace.isActivelyTracing}, " +
        s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")

      logger.debug(s"client received: start: $startTime, end: $endTime, elapsed: ${endTime - startTime}")
      response
    }
  }
}

private[channel] class ClientNetworkFilter(serviceName: String) extends SimpleFilter[ProtobufRequest,
  ProtobufResponse] {

  import ProtobufCodecFactory._

  override def apply(request: ProtobufRequest, service: finagle.Service[ProtobufRequest, ProtobufResponse]):
  Future[ProtobufResponse] = {
    val methodName = request.methodName
    logger.debug(s"client network filter: tracing(${Trace.isActivelyTracing}, " +
      s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")

    service(request) map { response =>
      logger.debug(s"client network filter response: tracing(${Trace.isActivelyTracing}, " +
        s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")
      response
    }
  }
}

private[channel] class ServerTracingFilter(serviceName: String) extends SimpleFilter[ProtobufRequest,
  ProtobufResponse] {

  import ProtobufCodecFactory._

  override def apply(request: ProtobufRequest, service: finagle.Service[ProtobufRequest, ProtobufResponse]):
  Future[ProtobufResponse] = {
    val methodName = request.methodName
    logger.debug(s"server receive: traceId(${request.traceId.getOrElse("no trace id")}): is tracing: ${Trace
      .isActivelyTracing}")
    Trace.recordServiceName(serviceName)
    Trace.recordRpc(methodName)

    val startTime = System.currentTimeMillis()
    Trace.record(Annotation.ServerRecv())

    logger.debug(s"server receive: tracing(${Trace.isActivelyTracing}, " +
      s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")

    service(request) map { response =>
      val endTime = System.currentTimeMillis()
      Trace.record(s"server send: elapsed: ${endTime - startTime}ms")
      Trace.record(Annotation.ServerSend())

      logger
        .debug(s"server send: start: $startTime, end: $endTime, elapsed: ${endTime-startTime}ms")
      logger.debug(s"server send: tracing(${Trace.isActivelyTracing}, " +
        s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")
      response
    }
  }
}

private[channel] class ServerNetworkFilter(serviceName: String) extends SimpleFilter[ProtobufRequest,
  ProtobufResponse] {

  import ProtobufCodecFactory._

  override def apply(request: ProtobufRequest, service: finagle.Service[ProtobufRequest, ProtobufResponse]):
  Future[ProtobufResponse] = {
    val methodName = request.methodName
    logger.debug(s"service network filter: tracing(${Trace.isActivelyTracing}, " +
      s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")

    service(request) map { response =>
      logger.debug(s"service network filter response: tracing(${Trace.isActivelyTracing}, " +
        s"id: ${Trace.idOption.getOrElse("no trace id")}): method: $methodName: serviceName: $serviceName")

      response
    }
  }
}

private[channel] class ServerSideDecoderV1(val methodService: MethodService, val rpcService: ProtobufService)
  extends FrameDecoder with ServerProtobufDecoderV1 {

  @throws(classOf[Exception])
  def decode(ctx: ChannelHandlerContext, channel: Channel, buf: ChannelBuffer): Object = {
    decode(ctx, channel, buf, methodService)
  }

  def getPrototype(methodName: String): Message = {
    val method = rpcService.getDescriptorForType.findMethodByName(methodName)
    rpcService.getRequestPrototype(method)
  }
}

private[channel] class ServerSideDecoderV0(val methodService: MethodService, val rpcService: ProtobufService)
  extends FrameDecoder with ServerProtobufDecoderV0 {

  @throws(classOf[Exception])
  def decode(ctx: ChannelHandlerContext, channel: Channel, buf: ChannelBuffer): Object = {
    decode(ctx, channel, buf, methodService)
  }

  def getPrototype(methodName: String): Message = {
    val method = rpcService.getDescriptorForType.findMethodByName(methodName)
    rpcService.getRequestPrototype(method)
  }
}

private[channel] class ServerSideEncoderV0(methodService: MethodService) extends ProtobufEncoderV0(methodService) {
}

private[channel] class ClientSideDecoderV0(val methodService: MethodService, val rpcService: ProtobufService)
  extends FrameDecoder with ClientProtobufDecoderV0 {

  @throws(classOf[Exception])
  def decode(ctx: ChannelHandlerContext, channel: Channel, buf: ChannelBuffer): Object = {
    decode(ctx, channel, buf, methodService)
  }

  def getPrototype(methodName: String): Message = {
    val method = rpcService.getDescriptorForType.findMethodByName(methodName)
    rpcService.getResponsePrototype(method)
  }
}

private[channel] class ClientSideEncoderV1(methodService: MethodService) extends ProtobufEncoderV1(methodService) {
}

private[channel] class ClientSideEncoderV0(methodService: MethodService) extends ProtobufEncoderV0(methodService) {
}


