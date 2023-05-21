package ldpc

import chisel3._
import chisel3.util._


//C2V

class C2V extends Bundle with COMMON{
    val Message =  Output(UInt(C2VMessageWidth.W))
    //val Valid   =  Output(Bool())
}
//V2C

class V2C extends Bundle with COMMON{
    val Message =  Output(UInt(V2CMessageWidth.W))
    //val waitCNU =  Output(Bool())

}
//V Initial
class VarInit extends Bundle with COMMON{
    val Yn   = Output(UInt(VaribleWidth.W))
    val Flag = Output(Bool())
}