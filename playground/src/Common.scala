package ldpc

import chisel3._
import chisel3.util._
import scala.io.{BufferedSource,Source}
import java.io._
trait  COMMON{

    val Algorithm       : String    = "MinSum" //译码算法 BF(BIT FLIIPING)
    // "MinSum" "BitFlipping"

    val ColWeight	    : Int       = 4     //列重
    val RowWeight       : Int       = 20    //行重
    val C2VMessageWidth : Int       = if (Algorithm == "BitFlipping") 1
                                      else 4
    val V2CMessageWidth : Int       = if (Algorithm == "BitFlipping") 1
                                      else 4 
    val VaribleWidth    : Int       = if (Algorithm == "BitFlipping") 2
                                      else C2VMessageWidth 
    val MaxC2VMess      : Int       = scala.math.pow(2,C2VMessageWidth).toInt-1
    val MaxV2CMess      : Int       = scala.math.pow(2,V2CMessageWidth).toInt-1
    //check node 信息求和
    val ColumnMessWidth : Int       = log2Ceil(ColWeight*(MaxC2VMess))
    //variable node 信息求和
    val RowMessWidth    : Int       = log2Ceil(RowWeight*(MaxV2CMess))  

    //Matrix Message
    val FilePath        : String    = "./matrix/base_matrix_8/base_matrix_8.dat"
    val IOTablePath     : String    = "./build/Table.h"
    val RowNum          : Int       = 4
    val ColNum          : Int       = 24
    val BlkSize         : Int       = 96
    val RowAddrWidth    : Int       = log2Ceil(RowNum)
    val ColAddrWidth    : Int       = log2Ceil(ColNum)
    val VNum            : Int       = ColNum*BlkSize
    val CNum            : Int       = RowNum*BlkSize

    val IterMax         : Int       = 20
    val CountWidth      : Int       = log2Ceil(IterMax)

    val Alpha           : Double    = 0.75
}
object COMMON extends COMMON {

}
object ReadQCMatrix extends COMMON{
    def ReadQC() : Array[Array[Int]] = {
        val file = Source.fromFile(FilePath)
        val QCMatrix : Array[Array[Int]] = file.getLines().map(_.split(" ")).map(_.map(_.toInt)).toArray
        file.close()
        //Check if it is read right
        /*
        for (i <- 0 until RowNum){
            for(j <- 0 until ColNum){
                print(QCMatrix(i)(j))
                print(" ")
            }
            println("\n")
        }
        */
        return QCMatrix
    }
}
/*
    generate the .h file to be used by CPP
    the IO table
 
*/
object GenerateIO extends COMMON{
    def Gen() : Unit  = {
        val writer = new PrintWriter(new File(IOTablePath))
        //writer.println("VLDPC *top;")
        
        //writer.println("for (size_t i = 0; i < "+VNum+"; i++) {")
        for(i <- 0 until VNum){
            //writer.println("#define io_YnInit("+i+")"+" io_YnInit_ ## "+i)
            
            writer.println("top->io_YnInit_"+i+" = YnInitial(RandomGen(sigma));")
            
        }
        //writer.println("}")
        writer.close()
    }
}
