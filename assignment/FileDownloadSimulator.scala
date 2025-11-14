class DownloadTask(fileName: String, downloadSpeed: Long) extends Thread {
  override def run(): Unit = {
    for (progress <- 10 to 100 by 10) {
      Thread.sleep(downloadSpeed)
      println(s"$fileName: $progress% downloaded")
    }
    println(s"$fileName download completed!")
  }
}

object FileDownloadSimulator {
  def main(args: Array[String]): Unit = {
    val fileA = new DownloadTask("FileA.zip", 500)
    val fileB = new DownloadTask("FileB.mp4", 300)
    val fileC = new DownloadTask("FileC.pdf", 400)

    fileA.start()
    fileB.start()
    fileC.start()

    fileA.join()
    fileB.join()
    fileC.join()

    println("All downloads completed!")
  }
}
