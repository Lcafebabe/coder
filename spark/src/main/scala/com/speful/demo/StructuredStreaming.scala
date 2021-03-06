package com.speful.demo

import com.speful.spark.utils.SimpleSpark
import org.apache.spark.sql.streaming.OutputMode


object StructuredStreaming extends App with SimpleSpark{


  import spark.implicits._

  val lines = spark.readStream.
    format("socket").
    option("host" , "master").
    option("port" , 9999).load


  lines.as[String].
    flatMap( _ split " " ).
    //withWatermark("timestamp" , "30 seconds").
    groupBy("value").
    count.
    writeStream.
    outputMode(OutputMode.Update()).
    format("console").
    start.
    awaitTermination

}
