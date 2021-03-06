/**
  * 这里封装了一些自定义的隐式转换
  * 可以方便的进行链式函数调用
  * 相比于`fun`(obj)的代码风格我更喜欢`str`.`fun`
  *
  */

package com.speful.implicits

import java.nio.charset.{Charset, StandardCharsets}

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.spark.input.PortableDataStream
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.util.Try


case class StringImplicitor(@transient str : String) {
  lazy val spark = SparkSession.builder.getOrCreate

  /**
    * Usage:
    * "package.to.classA".as[classA].foo.bar
    *
    * @tparam T
    * @return
    */
  def as[T] = Class.forName( str ).getConstructor().newInstance().asInstanceOf[T]

  /**
    * Usage:
    * "select something".go where "cond1" show false
    * @return
    */
  def go: DataFrame = spark sql str
}


case class SparkSessionImplicitor(@transient spark : SparkSession){

  /**
    * load gzip files in given path of default fs
    *
    * Usage:
    * spark gzipDF "path/to/file"
    * @param path
    * @param partitions
    * @return
    */
  def gzipDF(path : String , partitions : Int = 10): DataFrame ={
    import spark.implicits._
    spark.sparkContext.
      binaryFiles( path , partitions ).
      map(_._2).
      flatMap( extractFiles(_).toOption ).
      flatMap( _.map{ case(file , content) => ( file , decode(content) ) } ).
      toDF("file_name_" , "content_")
  }

  private def extractFiles(ps: PortableDataStream, n: Int = 1024) = Try {
    val tar = new TarArchiveInputStream(new GzipCompressorInputStream(ps.open))
    Stream.continually(Option(tar.getNextTarEntry))
      // Read until next exntry is null
      .takeWhile(_.isDefined).flatten
      // Drop directories
      .filter(!_.isDirectory)
      .map(e => {
        (e.getName,
          Stream.continually {
            // Read n bytes
            val buffer = Array.fill[Byte](n)(-1)
            val i = tar.read(buffer, 0, n)
            (i, buffer take i)
          }
            // Take as long as we've read something
            .takeWhile(_._1 > 0).flatMap(_._2)
            .toArray)})
      .toArray
  }

  private def decode( bytes: Array[Byte] , charset: Charset = StandardCharsets.UTF_8) =
    new String(bytes, StandardCharsets.UTF_8)
}


trait Implicitor {
  implicit def to(sql: String): StringImplicitor = StringImplicitor(sql)
  implicit def to(spark : SparkSession) : SparkSessionImplicitor = SparkSessionImplicitor(spark)
}