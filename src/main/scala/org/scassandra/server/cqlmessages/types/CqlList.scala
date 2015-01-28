/*
 * Copyright (C) 2014 Christopher Batey and Dogan Narinc
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
package org.scassandra.server.cqlmessages.types

import java.nio.ByteBuffer
import java.util

import akka.util.ByteIterator
import org.apache.cassandra.serializers.{ListSerializer}
import org.apache.cassandra.utils.ByteBufferUtil
import org.scassandra.server.cqlmessages.CqlProtocolHelper._
import org.scassandra.server.cqlmessages.ProtocolVersion
import scala.collection.JavaConversions._

import scala.collection.mutable

//todo change this to a types class
case class CqlList[T](listType : ColumnType[T]) extends ColumnType[Iterable[_]](0x0020, s"list<${listType.stringRep}>") {
   override def readValue(byteIterator: ByteIterator, protocolVersion: ProtocolVersion): Option[Iterable[T]] = {
     val numberOfBytes = byteIterator.getInt
     if (numberOfBytes == -1) {
       None
     } else {
       val bytes = new Array[Byte](numberOfBytes)
       byteIterator.getBytes(bytes)
       Some(ListSerializer.getInstance(listType.serializer).deserializeForNativeProtocol(ByteBuffer.wrap(bytes), protocolVersion.version))
     }
   }

  def writeValue(value: Any) : Array[Byte] = {
    val setSerialiser: ListSerializer[T] = ListSerializer.getInstance(listType.serializer)
    val list: List[T] = value match {
      case _: Set[T] =>
        value.asInstanceOf[Set[T]].toList
      case _: List[T] =>
        value.asInstanceOf[List[T]]
      case _: Seq[T] =>
        value.asInstanceOf[Seq[T]].toList
      case _ =>
        throw new IllegalArgumentException(s"Can't serialise ${value} as List of ${listType}")
    }

    val collectionType: util.List[T] = listType.convertToCorrectCollectionTypeForList(list)

    val serialised: util.List[ByteBuffer] = setSerialiser.serializeValues(collectionType)

    val setContents = serialised.foldLeft(new Array[Byte](0))((acc, byteBuffer) => {
      val current: mutable.ArrayOps[Byte] = ByteBufferUtil.getArray(byteBuffer)
      acc ++ serializeShort(current.size.toShort) ++ current
    })

    serializeInt(setContents.length + 2) ++ serializeShort(list.size.toShort) ++ setContents
  }

  override def convertToCorrectJavaTypeForSerializer(value: Any): Iterable[_] = throw new UnsupportedOperationException("Can't have lists in collections yet")
}
