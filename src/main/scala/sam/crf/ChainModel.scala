package sam.crf

import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import java.io.File
class ChainModel(domainFile : String, labelDomainSize : Int, ff : (String=>Array[Int])) {
  
  val featuresDomain = new FeaturesDomain(domainFile)
  val labelDomain = new LabelDomain(labelDomainSize)
  
  val weights = new Weights(featuresDomain, labelDomain)
  val chains = new ArrayBuffer[Chain]()
  var datasetSize = 0

	def recursiveListFiles(f: File): Array[File] = {
	  val these = f.listFiles
	  if(!these.filter(_.isDirectory).isEmpty)
	  	these.filter(_.isDirectory).flatMap(recursiveListFiles)
	  else these
	}
	
	def loadChains(dir : String) {
		val files = recursiveListFiles(new File(dir))
    var count = 0
		for(file <- files; if count < 30) {
			chains += new Chain(weights,ff).loadChain(file.getAbsolutePath())
      count += 1
    }
	}

  def loadChainsTrain(dir : String) {
    val files = recursiveListFiles(new File(dir))
    for(file <- files)
      chains += new Chain(weights,ff).loadChain(file.getAbsolutePath())
  }

  def setTransitionWeights(file : String) {
		var count = 0
    var countj = 0
		for(line <- Source.fromFile(file).getLines()) {
			weights.transWeights(count)(countj) = line.toDouble
			count += 1
      if(count % labelDomainSize == 0) { countj += 1; count = 0 }
		}
	}
	
	def setObservationWeights(file : String) {
		val lines = Source.fromFile(file).getLines().toArray
		var count = 0
		for(f <-  0 until featuresDomain.features.size) {
			for(v <- 0 until (featuresDomain.features(f).size) ) {
        for(k <- 0 until labelDomain.until) {
				  weights.obsWeights(f)(v)(k) = lines(count).toDouble
				  count += 1
        }
			}
		}
	}
	
	def test(dir : String) {
    val gw = weights.getWeights
    weights.setWeights(gw)
    val gw2 = weights.getWeights
    assert((0 until gw.length).forall(i => gw(i) == gw2(i)), "Weights change by setting.")
		loadChains(dir)
    datasetSize = chains.length
    for(chain <- chains) {
			val sp = new LogSumProduct(chain)
			sp.inferUpDown()
			sp.setToMaxMarginal()
			sp.printMarginals(1)
      sp.printMarginals(2)
      println("")
		}
	}

  def train(dir : String) {
    loadChains(dir)
    datasetSize = chains.length
    val trainer = new LBFGSTrainer(this)
    trainer.optimize()
  }

  def saveWeights(dir : String) {
    // Save the weights to disk
  }

}

class FeaturesDomain(val file : String) {
	var features = new ArrayBuffer[Array[Int]]()
	for(line <- Source.fromFile(file).getLines()) {
		var split = line.split("->")
		features.append((split(0).toInt to split(1).toInt).toArray)
	}
	def size = features.size
}

class LabelDomain(val until : Int) {
	var labels = 1 to until
}

class Weights(val features : FeaturesDomain, val labels : LabelDomain) {
	val obsWeights = new Array[Array[Array[Double]]](features.size)
	var count = 0

  for(feature <- features.features) {
	obsWeights(count) = Array.ofDim[Double](feature.size,labels.until)
    count += 1
  }

  val transWeights = Array.ofDim[Double](labels.until,labels.until)

  val dimension = obsWeights.map(_.length * labels.until).sum + transWeights.length * transWeights.length
  val obsWeightsSize = obsWeights.map(_.length * labels.until).sum

  def setWeights(wts : Array[Double]) {
    //Set the weights from the wts vector here.
    var count = 0
    for(f <-  0 until features.features.size) {
      for(v <- 0 until features.features(f).size ) {
        for(k <- 0 until labels.until) {
          obsWeights(f)(v)(k) = wts(count)
          count += 1
        }
      }
    }
    for(i <- 0 until transWeights.length; j <- 0 until transWeights(0).length) {
      transWeights(i)(j) = wts(count)
      count += 1
    }
  }

  def getWeights : Array[Double] = flatObs ++ flatTrans

  def flatTrans : Array[Double] = transWeights.flatten

  def flatObs : Array[Double] = obsWeights.flatten.flatten
	
  def apply(klass : Int, feature : Int, value : Int) : Double = {
	  obsWeights(feature)(value-features.features(feature)(0))(klass)
  }

  def index(feature : Int, klass : Int, value : Int) : Int = {
    val featureCount = obsWeights.take(feature).map(_.length).sum * labels.until
    featureCount + value*labels.until + klass
  }

  def transIndex(klass1 : Int, klass2 : Int) : Int = {
    obsWeightsSize + labels.until*klass1 + klass2
  }
	
	def apply(y1 : Int, yPlus : Int) : Double = {
		transWeights(y1)(yPlus)
	}

	def print() {
		for(f <- obsWeights) {
			for(i <- f) {
				println(i)
			}
			println("")
		}
		println("Transition:")
		for(tw <- transWeights) {
      for(t <- tw) {
        println(t)
      }
		}
		println("")
	}

  def printWithName() {
    var to = 0
    var count = 0
    for(f <- obsWeights) {
      println("Feature: " + count)
      var count2 = 0
      for(i <- f) {
        if(count2 % labels.until == 0) println("Label: " + count2/labels.until)
        println("Feature value: " + count2 % labels.until)
        println(i)
        count2 += 1
        to += 1
      }
      println("")
      count += 1
    }
    println("Transition:")
    for(tw <- transWeights) {
      for(t <- tw) {
        println(t)
        to += 1
      }
    }
    println("Total: " + to )
    println("")
  }
	
}