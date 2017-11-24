package com.twitter.finagle.protobuf.rpc

import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.protobuf.rpc.channel.{ProtobufResponse, ProtobufRequest}

import scala.annotation.tailrec

/**
  * Helper function for wrapping a Finagle service with a set of filters. Because this protocol does not expose the Finagle
  * service, the [[RpcFactory]] internally applies filters around the service.
  */
object RpcFactoryUtil {

  def applyFilters(service: Service[ProtobufRequest,ProtobufResponse],
                   filters: List[Filter[ProtobufRequest, ProtobufResponse, ProtobufRequest, ProtobufResponse]]): Service[ProtobufRequest,ProtobufResponse] = {
    @tailrec
    def composeFiltersInternal(f: Filter[ProtobufRequest, ProtobufResponse, ProtobufRequest, ProtobufResponse],
                               fl: List[Filter[ProtobufRequest, ProtobufResponse, ProtobufRequest, ProtobufResponse]]): Filter[ProtobufRequest, ProtobufResponse, ProtobufRequest, ProtobufResponse] = {
      if (fl.nonEmpty) {
        composeFiltersInternal(fl.head.andThen(f), fl.tail)
      }
      else {
        f
      }
    }

    if (filters.nonEmpty) {
      val reversed = filters.reverse
      composeFiltersInternal(reversed.head, reversed.tail) andThen service
    } else {
      service
    }
  }
}