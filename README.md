This project will show my attempts to solve day-to-day problems that I encounter when developing systems using some of the most popular Scala frameworks. Normally what I do, when learning new frameworks, I try to challenge myself to solve a real problem. I try not to just stick to the `Hello World` examples that come with most of the framework documentation but go further, think of a problem that the new framework I am learning is suited to solve and apply my acquired knowledge to do exactly that. This is a multi-module project. Each module has practical examples for a given Scala framework/toolkit.

I have been tinkering with Scala for more than a year now, developing mostly micro-services using [Spray](http://www.spray.io) then migrated to [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http) after [Spray](http://www.spray.io) was sundowned. The [Akka](http://www.akka.io) toolkit just inspires me: `Actors`, `Streams` and all the tools build on top of these two.

# `akka-streams`

I have been getting my hands dirty with Akka Streams. As stated in the official [documentation](http://doc.akka.io/docs/akka/current/scala/stream/stream-introduction.html), the purpose of `Akka Streams API` is to offer an intuitive and safe way to formulate stream processing setups such that we can then execute them efficiently and with bounded resource usage—no more OutOfMemoryErrors. In order to achieve this our streams need to be able to limit the buffering that they employ, they need to be able to slow down producers if the consumers cannot keep up. This feature is called back-pressure...

I thought of a problem to solve using this noble API.

*Given 100,000,000 random integers in a file, sort them and write the result in file. You can only hold 1000,000 integers in memory

N/B: This is a very popular interview question apparently.*

Solution? - External Sort

*External sorting is required when the data being sorted do not fit into the main memory of a computing device (usually RAM) and instead they must reside in the slower external memory (usually a hard drive). External sorting typically uses a sort-merge strategy. In the sorting phase, chunks of data small enough to fit in main memory are read, sorted, and written out to a temporary file. In the merge phase, the sorted subfiles are combined into a single larger file.*

I adapted this [Java solution](http://www.ashishsharma.me/2011/08/external-merge-sort.html?m=1).

#### Step-by-step

I am going to stream integers from the input file, accumulate them into groups of 1,000,000 integers, sort and write each group to a separate file. I will then merge the separate sorted files into one big sorted file. I will use [custom stream processing stages](http://doc.akka.io/docs/akka/current/scala/stream/stream-customize.html#custom-processing-with-graphstage).  

First of all, I needed the 100,000,000 integers. I created `MassiveFileCreator` object for this purpose.

    val source: Source[Int, NotUsed] = Source.fromIterator(() => Iterator.continually(Random.nextInt(100000000)).take(100000000))

    def lineSink(filePath: String): Sink[String, Future[IOResult]] =
      Flow[String]
        .map(s => ByteString(s + "\n"))
        .toMat(FileIO.toPath(Paths.get(filePath)))(Keep.right)

    source.map(_.toString).runWith(lineSink(filePath))

To run the project, clone the repo and run `sbt update`. To generate a file with 100,000,000 integers, run `sbt "akka-streams/runMain com.okune.streams.MassiveFileCreator \"{fileDir}/100millionintegers.txt\""`

Make sure you replace `fileDir` with actual path to the directory where you would like the file to be stored.

Now that we have an input file, we can start our stream and collect results downstream.
`sbt "akka-streams/runMain com.okune.streams.ExternalSort \"{fileDir}/100millionintegers.txt\" \"fileDir/sortedintegers.txt\""`

Make sure you replace `fileDir` appropriately. The `sortedintegers.txt` will contain the sorted output.

I will keep updating this module and repo with new discoveries.
