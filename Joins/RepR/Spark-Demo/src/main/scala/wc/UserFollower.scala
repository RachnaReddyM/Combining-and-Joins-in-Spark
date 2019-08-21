package wc

import javax.annotation.Resource
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.log4j.LogManager
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.functions.broadcast
import java.io.File
import java.io.PrintWriter


object UserFollower{
  
  def main(args: Array[String]) {
    val logger: org.apache.log4j.Logger = LogManager.getRootLogger
    if (args.length != 2) {
      logger.error("Usage:\nwc.UserFollower <input dir> <output dir>")
      System.exit(1)
    }
    val conf = new SparkConf().setAppName("Word Count")
    val sc = new SparkContext(conf)
    val spark = SparkSession.builder().getOrCreate()
    import spark.implicits._

		// Delete output directory, only to ease local development; will not work on AWS. ===========
//    val hadoopConf = new org.apache.hadoop.conf.Configuration
//    val hdfs = org.apache.hadoop.fs.FileSystem.get(hadoopConf)
//    try { hdfs.delete(new org.apache.hadoop.fs.Path(args(1)), true) } catch { case _: Throwable => {} }
		// ================

    // Custom Join inspired from book- High Performance Spark

    val maxId: Int = 20000
    val textFile = sc.textFile(args(0))
    val edges = textFile.map(line => line.split(","))
                        .filter(edge => edge(0).toInt < maxId && edge(1).toInt < maxId)
    val leftRDD = edges.map(word => (word(0), word(1))).groupBy(_._1).mapValues(_.map(_._2))
    val rightRDD = edges.map(word => (word(1), word(0)))
    val leftRDDB = rightRDD.sparkContext.broadcast(leftRDD.collectAsMap())
    val path2 = rightRDD.flatMap({
      case (k,v1) =>
        leftRDDB.value.get(k).map{
          x=>
            x.map(y=>(y,v1))
        }}).flatMap(z=>z)

     val triangles = path2.flatMap({
      case(k,v1)=>
        leftRDDB.value.get(k).map{
          x=>
            x.map(y=>(y,v1))
        }
    }).flatMap(z=>z).filter({
      case(k,v)=>{k.toInt==v.toInt}
    }).count()


    val finalCount = triangles/3

    println("----------------------------------------------")
    println(finalCount)
    println("----------------------------------------------")

    val outputString = List(finalCount).map(op=>("Count== ",op))
    // idea of writing DF to CSV -
    // https://stackoverflow.com/questions/44537889/write-store-dataframe-in-text-file

    outputString.toDF().coalesce(1)
      .write.mode(saveMode = "overwrite")
      .format("csv")
      .save(args(1))

  }
}
