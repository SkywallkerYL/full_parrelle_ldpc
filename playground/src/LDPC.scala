package ldpc

import chisel3._
import chisel3.util._
import scala.io.{BufferedSource,Source}
//译码器顶层 
/*
    读取矩阵文件  并且根据QC矩阵 对CNU和VNU的接口进行连线
    接受初始化的信道参数 返回译码结果

*/
class LDPC extends Module with COMMON {
    val io = IO(new Bundle{
        val DecodeFlag  = Input (Bool())
        val Flush       = Input (Bool())
        val SNRFlush    = Input (Bool())
        val YnInit      = Input (Vec(VNum,UInt(VaribleWidth.W)))

        val Success     = Output(Bool())
        val DecodeDown  = Output(Bool())
        val SuccessNum  = Output(UInt(64.W))
        val FailNum     = Output(UInt(64.W))
        val TotalNum    = Output(UInt(64.W))
        val state       = Output(UInt(2.W))
    })
    io.state := 0.U


    val QCMatrix : Array[Array[Int]] = ReadQCMatrix.ReadQC()
    GenerateIO.Gen()
    val VNUs = Seq.fill(VNum)(Module(new VNU))
    val CNUs = Seq.fill(CNum)(Module(new CNU))
    //Initialize 输入默认连接0
    for (i <- 0 until VNum) {
        for (j <-0 until ColWeight){
            VNUs(i).io.FromCNs(j).Message := 0.U
        }
        VNUs(i).io.Init.Yn := io.YnInit(i)
        VNUs(i).io.Init.Flag := io.DecodeFlag
        VNUs(i).io.Cnuvalid := true.B
    }
    //VNU传递给CNU的端口默认为最大值，不然会导致最小和算法未连接的CNU接收到的一直是0，即最小值一直是0
    for (i <- 0 until CNum) {
        for (j <-0 until RowWeight){
            CNUs(i).io.FromVNs(j).Message := (MaxV2CMess>>1).U
            
        }
        CNUs(i).io.state := 0.U
    }
    /************Connect CNUs and VNUs according to the matrix**********/
    for (i <- 0 until VNum) {
        var Cind  = 0
        //check the matrix member 
        for (j <-0 until RowNum){
            val z : Int = QCMatrix(j)(i/BlkSize)
            if(z>=0){
                var Vind = 0
                for (k <- 0 until ColNum){
                    
                    val z0 : Int = QCMatrix(j)(k)
                    if(z0>=0 &&k < i/BlkSize) Vind +=1
                }
                assert(Vind < RowWeight)
                VNUs(i).io.FromCNs(Cind) <>  CNUs(j*BlkSize+(i%BlkSize+10*BlkSize-z)%BlkSize).io.ToVNs(Vind)                
                Cind+=1
            }
        }
        assert(Cind <= ColWeight)//Cind should be less than colweight
    }
    for (i <- 0 until CNum) {
        var Vind  = 0
        //check the matrix member 
        for (j <-0 until ColNum){
            val z : Int = QCMatrix(i/BlkSize)(j)
            if(z>=0){
                
                //calculate the index of the CNU for the VNU
                var Cind = 0
                for (k <- 0 until RowNum){
                    
                    val z0 : Int = QCMatrix(k)(j)
                    if(z0>=0 &&k < i/BlkSize) Cind +=1
                }
                assert(Cind < ColWeight)
                CNUs(i).io.FromVNs(Vind) <>  VNUs(j*BlkSize+(i%BlkSize+z)%BlkSize).io.ToCNs(Cind)
                Vind+=1
            }
        }
        assert(Vind <= RowWeight) //Vind should be less than rowweight
    }
    /***********Decode State Machine***********/
    val ready :: busy :: Nil = Enum(2)
    val state = RegInit(ready)
    val DecodeDown = Wire(Bool())
    //io.Busy := false.B
    switch(state){
        is(ready) {
            when (io.DecodeFlag){
                state := busy
            }
        }
        is(busy) {
            //io.Busy := true.B
            when(DecodeDown || io.Flush || io.SNRFlush){
                state := ready
            }
        }
    }
    val Cnuvalid = Wire(Bool())
    Cnuvalid := false.B
    //Minsum算法引入多周期  需要状态机来控制VNU和CNU模块的交互
    if(Algorithm == "MinSum"){
    val cnuready :: cnujump :: cnubusy :: cnuvalid:: Nil = Enum(4)
    //val Cnuvalid = Wire(Bool())
    
    val cnustate = RegInit(cnuready)
    Cnuvalid := cnustate === cnuvalid
    switch(cnustate){
        is(cnuready){
            //延迟一个周期等待数据写入VNU
            when(io.DecodeFlag){
                cnustate := cnujump
            }
        }
        is(cnujump){
            cnustate := cnubusy
        }
        is(cnubusy){
            //cnuvalid在addr=最大的addr之后的一个周期拉高
            //addr寄存器在cnubusy的条件下才+
            when(CNUs(0).io.Cnuvalid){
                cnustate := cnuvalid
            }
        }
        is(cnuvalid){
            //这个周期cnu的addr遍历完成，正好把数据写入到Min1 min2 同时发给vnu
            when(DecodeDown||io.Flush||io.SNRFlush) {
                cnustate := cnuready
            }.otherwise{
                //译码完成就跳转到ready 否则跳转给busy,下个周期正好数据写入了vnu，然后cnu就又开始
                //遍历所有的VNU消息，选出最小值和次小值
                cnustate := cnubusy
            }
        }
    }
    for (i <- 0 until CNum) {
        CNUs(i).io.state := cnustate
    }
    for (i <- 0 until VNum) {
        VNUs(i).io.Cnuvalid := cnustate === cnuvalid
    }
    io.state := cnustate
    }
    /************IterCount*************/
    //itercount在cnuvalid拉高的条件下+ Bitflipping就无所谓
    val IterCount = RegInit(0.U(CountWidth.W))
    DecodeDown := (IterCount === IterMax.U || io.Success)&& state===busy
    io.DecodeDown := DecodeDown
    if(Algorithm == "BitFlipping"){
       IterCount := Mux(DecodeDown||io.Flush||io.SNRFlush,0.U,IterCount+1.U) 
    }else if(Algorithm == "MinSum"){
        when(DecodeDown||io.Flush||io.SNRFlush){
            IterCount := 0.U 
        }.elsewhen(CNUs(0).io.Cnuvalid){
            IterCount := IterCount+1.U 
        }
    }
    
    /************SuccessCount************/
    val SuccessCount = RegInit(0.U(64.W))
    val SuccessDecode = (io.Success && state===busy)
    when(SuccessDecode){
        SuccessCount := SuccessCount + 1.U
    }.elsewhen(io.SNRFlush){
        SuccessCount := 0.U
    }
    io.SuccessNum := SuccessCount
    /************FailCount************/
    val FailCount  = RegInit(0.U(64.W))
    val FailDecode = (IterCount === IterMax.U && !io.Success && state===busy)
    when(FailDecode){
        FailCount := FailCount + 1.U
    }.elsewhen(io.SNRFlush){
        FailCount := 0.U
    }
    io.FailNum := FailCount
    /************TotalCount************/
    val TotalCount = RegInit(0.U(64.W))
    when(DecodeDown && state===busy){
        TotalCount := TotalCount + 1.U
    }.elsewhen(io.SNRFlush){
        TotalCount := 0.U
    }
    io.TotalNum := TotalCount

    //假定全0码字
    val DecodeRes = VecInit(Seq.fill(VNum)(0.U(1.W)))
    for(i <-0 until VNum){
        DecodeRes(i) := VNUs(i).io.Varible
    }
    if(Algorithm == "BitFlipping"){
        io.Success := !(DecodeRes.asUInt.orR)
    }
    else if (Algorithm == "MinSum"){
        io.Success := !(DecodeRes.asUInt.orR) && Cnuvalid
    }
    
        
    /*********Debug*********/
    //when(DecodeDown){
    //    printf(p"success=${io.Success} Iter=${IterCount}\n")
    //}
}