package ldpc

import chisel3._
import chisel3.util._

//CNU 模块  输入 来自校验节点的信息
class CNU extends Module with COMMON{
    val io = IO(new Bundle{
        val FromVNs = Vec(RowWeight,Flipped(new V2C))
        val ToVNs   = Vec(RowWeight,(new C2V)) 
        val state   = Input(UInt(2.W))
        val Cnuvalid = Output(Bool())
    })
    io.Cnuvalid := false.B
    if (Algorithm == "BitFlipping"){
        for (i <- 0 until RowWeight)
            io.ToVNs(i).Message := io.FromVNs.asUInt.xorR
    }
    else if (Algorithm == "MinSum"){ 
        // Title: A 520k (18900,17010) Array Dispersion LDPC decoder Architectures
        //Algorithm : NMS-VSS (normalized min-sum VSS) 
        /*
            Due to Full-Paralle Architectures , there are no group
            min   C2Vmin  = min{|V2C.message|} \v
            sign  C2Vsign = ^SIGN \v
            sign = V2CSIGN_1*V2CSIGN_2*......V2CSIGN_N
            - -> 1  + -> 0  => ^SIGN
            C2V = C2VSign * |C2Vmin| * Alpha //exclude the message from its own
        */
        //C2Vsign
        val C2Vsign = VecInit(Seq.fill(RowWeight)(0.U(1.W)))
        for (i <- 0 until RowWeight){
            C2Vsign(i) := io.FromVNs(i).Message(V2CMessageWidth-1)
        } 
        val C2VABS  = VecInit(Seq.fill(RowWeight)(0.U(V2CMessageWidth.W)))
        for (i <- 0 until RowWeight){
            C2VABS(i) := Mux(C2Vsign(i)===1.U,~io.FromVNs(i).Message+1.U,io.FromVNs(i).Message)
        } 
        val ALLSign = C2Vsign.asUInt.xorR
        //C2Vmin
        /*
        val LessFlag =  Seq.fill(RowWeight)(VecInit(Seq.fill(RowWeight)(0.U(1.W))))
        //val MinAddr  =  Wire(UInt(RowWeight.W))
        //MinAddr := 0.U
        for (i <- 0 until RowWeight){
            for (j <- 0 until RowWeight){
                LessFlag(i)(j) := C2VABS(i) <= C2VABS(j)
            }
            //when((LessFlag(i).asUInt).andR ){
            //    MinAddr := i.U
            //}
        }
        */
        //To Many Wires, out of memory when generate verilog
        //consider multi cycle 
        /*
            with multi cycle ,compute the min1 and min2 Then send it to the VNU with a valid flag
            注意译码的时候从VNU开始，此时传给VNU的消息应该是0
        */
        val min1 = RegInit(((MaxV2CMess>>1).U)(V2CMessageWidth.W))
        val min2 = RegInit(((MaxV2CMess>>1).U)(V2CMessageWidth.W))
        val addr = RegInit(0.U(ColAddrWidth.W))
        val min1addr = RegInit(0.U(ColAddrWidth.W))
        val min2addr = RegInit(0.U(ColAddrWidth.W))
        val cnuready :: cnujump :: cnubusy :: cnuvalid:: Nil = Enum(4)
        when(io.state === cnubusy){
            addr := addr + 1.U
            when(min1 > C2VABS(addr)){
                min1 := C2VABS(addr)
                min1addr := addr
            }.elsewhen(min2 > C2VABS(addr) ){
                min2 := C2VABS(addr)
                min2addr := addr
            }
        }
        when(io.state === cnuvalid){
            addr := 0.U
            min1 := (MaxV2CMess>>1).U
            min2 := (MaxV2CMess>>1).U
        }
        io.Cnuvalid := addr===(RowWeight-1).U
        val C2Vmin = Seq.fill(RowWeight)(Wire(UInt(C2VMessageWidth.W)))
        for (i <- 0 until RowWeight){
            /*
            val minC2V = Wire(UInt(C2VMessageWidth.W))
            minC2V := MaxC2VMess.U >> 1.U
            //sort the min
            for (j <- 0 until RowWeight){
                //if(j != i) {
                    when(((LessFlag(j).asUInt)| (1.U<<i.U)).andR &&(j.U =/= i.U) ){
                        minC2V := C2VABS(j)
                    }
                //}
            }
            */
            C2Vmin(i) := Mux(min1addr =/= i.U,min1,min2) 
        }
        
        //L = S * |L| * alpha
        for (i <- 0 until RowWeight){
            val FixC2V = C2Vmin(i) //* 3.U >> 2.U
            //val FixC2V = C2VABS(MinAddr) * 3.U >> 2.U
            //Exclude own sign
            io.ToVNs(i).Message := Mux((ALLSign^C2Vsign(i))===1.U,~FixC2V+1.U,FixC2V)
            
        }
    }
    
}
