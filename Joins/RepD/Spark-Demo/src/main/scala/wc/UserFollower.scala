package wc

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.log4j.LogManager
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.broadcast


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

    // Join inspired from book - High Performance Spark by Rachel Warren and Holden Karau

    val maxId: Int = 55000
    val textFile = sc.textFile(args(0))
    val edges = textFile.map(line => line.split(","))
                        .filter(edge => edge(0).toInt < maxId && edge(1).toInt < maxId)
    val leftDF = edges.map(word => (word(0), word(1))).toDF("user1","user2")
    val rightDF = edges.map(word => (word(0), word(1))).toDF("user2","user3")

    val path2 = rightDF.join(broadcast(leftDF),"user2")

    val checkPath = path2.select("user3","user1").toDF("user1","userDest")
    val triangles = checkPath.join(broadcast(leftDF),"user1").filter($"user2"===$"userDest").count()
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
