/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.catalyst.expressions

import java.security.{MessageDigest, NoSuchAlgorithmException}
import java.util.zip.CRC32

import org.apache.commons.codec.digest.DigestUtils

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.analysis.TypeCheckResult
import org.apache.spark.sql.catalyst.expressions.codegen._
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String

/**
 * A function that calculates an MD5 128-bit checksum and returns it as a hex string
 * For input of type [[BinaryType]]
 */
@ExpressionDescription(
  usage = "_FUNC_(input) - Returns an MD5 128-bit checksum as a hex string of the input",
  extended = "> SELECT _FUNC_('Spark');\n '8cde774d6f7333752ed72cacddb05126'")
case class Md5(child: Expression) extends UnaryExpression with ImplicitCastInputTypes {

  override def dataType: DataType = StringType

  override def inputTypes: Seq[DataType] = Seq(BinaryType)

  protected override def nullSafeEval(input: Any): Any =
    UTF8String.fromString(DigestUtils.md5Hex(input.asInstanceOf[Array[Byte]]))

  override def genCode(ctx: CodeGenContext, ev: GeneratedExpressionCode): String = {
    defineCodeGen(ctx, ev, c =>
      s"UTF8String.fromString(org.apache.commons.codec.digest.DigestUtils.md5Hex($c))")
  }
}

/**
 * A function that calculates the SHA-2 family of functions (SHA-224, SHA-256, SHA-384, and SHA-512)
 * and returns it as a hex string. The first argument is the string or binary to be hashed. The
 * second argument indicates the desired bit length of the result, which must have a value of 224,
 * 256, 384, 512, or 0 (which is equivalent to 256). SHA-224 is supported starting from Java 8. If
 * asking for an unsupported SHA function, the return value is NULL. If either argument is NULL or
 * the hash length is not one of the permitted values, the return value is NULL.
 */
@ExpressionDescription(
  usage =
    """_FUNC_(input, bitLength) - Returns a checksum of SHA-2 family as a hex string of the input.
      SHA-224, SHA-256, SHA-384, and SHA-512 are supported. Bit length of 0 is equivalent to 256."""
  ,
  extended = "> SELECT _FUNC_('Spark', 0);\n " +
    "'529bc3b07127ecb7e53a4dcf1991d9152c24537d919178022b2c42657f79a26b'")
case class Sha2(left: Expression, right: Expression)
  extends BinaryExpression with Serializable with ImplicitCastInputTypes {

  override def dataType: DataType = StringType
  override def nullable: Boolean = true

  override def inputTypes: Seq[DataType] = Seq(BinaryType, IntegerType)

  protected override def nullSafeEval(input1: Any, input2: Any): Any = {
    val bitLength = input2.asInstanceOf[Int]
    val input = input1.asInstanceOf[Array[Byte]]
    bitLength match {
      case 224 =>
        // DigestUtils doesn't support SHA-224 now
        try {
          val md = MessageDigest.getInstance("SHA-224")
          md.update(input)
          UTF8String.fromBytes(md.digest())
        } catch {
          // SHA-224 is not supported on the system, return null
          case noa: NoSuchAlgorithmException => null
        }
      case 256 | 0 =>
        UTF8String.fromString(DigestUtils.sha256Hex(input))
      case 384 =>
        UTF8String.fromString(DigestUtils.sha384Hex(input))
      case 512 =>
        UTF8String.fromString(DigestUtils.sha512Hex(input))
      case _ => null
    }
  }

  override def genCode(ctx: CodeGenContext, ev: GeneratedExpressionCode): String = {
    val digestUtils = "org.apache.commons.codec.digest.DigestUtils"
    nullSafeCodeGen(ctx, ev, (eval1, eval2) => {
      s"""
        if ($eval2 == 224) {
          try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-224");
            md.update($eval1);
            ${ev.value} = UTF8String.fromBytes(md.digest());
          } catch (java.security.NoSuchAlgorithmException e) {
            ${ev.isNull} = true;
          }
        } else if ($eval2 == 256 || $eval2 == 0) {
          ${ev.value} =
            UTF8String.fromString($digestUtils.sha256Hex($eval1));
        } else if ($eval2 == 384) {
          ${ev.value} =
            UTF8String.fromString($digestUtils.sha384Hex($eval1));
        } else if ($eval2 == 512) {
          ${ev.value} =
            UTF8String.fromString($digestUtils.sha512Hex($eval1));
        } else {
          ${ev.isNull} = true;
        }
      """
    })
  }
}

/**
 * A function that calculates a sha1 hash value and returns it as a hex string
 * For input of type [[BinaryType]] or [[StringType]]
 */
@ExpressionDescription(
  usage = "_FUNC_(input) - Returns a sha1 hash value as a hex string of the input",
  extended = "> SELECT _FUNC_('Spark');\n '85f5955f4b27a9a4c2aab6ffe5d7189fc298b92c'")
case class Sha1(child: Expression) extends UnaryExpression with ImplicitCastInputTypes {

  override def dataType: DataType = StringType

  override def inputTypes: Seq[DataType] = Seq(BinaryType)

  protected override def nullSafeEval(input: Any): Any =
    UTF8String.fromString(DigestUtils.shaHex(input.asInstanceOf[Array[Byte]]))

  override def genCode(ctx: CodeGenContext, ev: GeneratedExpressionCode): String = {
    defineCodeGen(ctx, ev, c =>
      s"UTF8String.fromString(org.apache.commons.codec.digest.DigestUtils.shaHex($c))"
    )
  }
}

/**
 * A function that computes a cyclic redundancy check value and returns it as a bigint
 * For input of type [[BinaryType]]
 */
@ExpressionDescription(
  usage = "_FUNC_(input) - Returns a cyclic redundancy check value as a bigint of the input",
  extended = "> SELECT _FUNC_('Spark');\n '1557323817'")
case class Crc32(child: Expression) extends UnaryExpression with ImplicitCastInputTypes {

  override def dataType: DataType = LongType

  override def inputTypes: Seq[DataType] = Seq(BinaryType)

  protected override def nullSafeEval(input: Any): Any = {
    val checksum = new CRC32
    checksum.update(input.asInstanceOf[Array[Byte]], 0, input.asInstanceOf[Array[Byte]].length)
    checksum.getValue
  }

  override def genCode(ctx: CodeGenContext, ev: GeneratedExpressionCode): String = {
    val CRC32 = "java.util.zip.CRC32"
    nullSafeCodeGen(ctx, ev, value => {
      s"""
        $CRC32 checksum = new $CRC32();
        checksum.update($value, 0, $value.length);
        ${ev.value} = checksum.getValue();
      """
    })
  }
}

/**
 * A function that calculates hash value for a group of expressions.  Note that the `seed` argument
 * is not exposed to users and should only be set inside spark SQL.
 *
 * Internally this function will write arguments into an [[UnsafeRow]], and calculate hash code of
 * the unsafe row using murmur3 hasher with a seed.
 * We should use this hash function for both shuffle and bucket, so that we can guarantee shuffle
 * and bucketing have same data distribution.
 */
case class Murmur3Hash(children: Seq[Expression], seed: Int) extends Expression {
  def this(arguments: Seq[Expression]) = this(arguments, 42)

  override def dataType: DataType = IntegerType

  override def foldable: Boolean = children.forall(_.foldable)

  override def nullable: Boolean = false

  override def checkInputDataTypes(): TypeCheckResult = {
    if (children.isEmpty) {
      TypeCheckResult.TypeCheckFailure("function hash requires at least one argument")
    } else {
      TypeCheckResult.TypeCheckSuccess
    }
  }

  private lazy val unsafeProjection = UnsafeProjection.create(children)

  override def eval(input: InternalRow): Any = {
    unsafeProjection(input).hashCode(seed)
  }

  override def genCode(ctx: CodeGenContext, ev: GeneratedExpressionCode): String = {
    val unsafeRow = GenerateUnsafeProjection.createCode(ctx, children)
    ev.isNull = "false"
    s"""
      ${unsafeRow.code}
      final int ${ev.value} = ${unsafeRow.value}.hashCode($seed);
    """
  }
}
