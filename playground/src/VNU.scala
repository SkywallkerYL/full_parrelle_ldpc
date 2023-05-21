package ldpc

import chisel3._
import chisel3.util._

//VNU 模块  输入 来自校验节点的信息
class VNU extends Module with COMMON {
    val io = IO(new Bundle{
        val FromCNs = Vec(ColWeight,Flipped(new C2V))
        val Init    = Flipped(new VarInit)
        //val Success = Input(Bool())
        val ToCNs   = Vec(ColWeight,(new V2C))
        val Varible = Output(UInt(1.W))
        val Cnuvalid = Input(Bool())
    })
    //io.ToCNs.waitCNU := false.B
    if (Algorithm == "BitFlipping"){
        //MTBF algorithm 
        //Title:Multi-threshold bit flipping algorithm for decoding structured LDPC codes
        /*******Initialized*******/
        val Zn  = RegInit(0.U(V2CMessageWidth.W))
        val Zn0 = RegInit(0.U(V2CMessageWidth.W))
        val Flip = Wire(Bool())
        when(io.Init.Flag){
            //0 Yn>=0    1 Yn<0
            Zn  := io.Init.Yn(VaribleWidth-1)&&((io.Init.Yn((VaribleWidth-2),0).orR))
            Zn0 := io.Init.Yn(VaribleWidth-1)&&((io.Init.Yn((VaribleWidth-2),0).orR))
        }.elsewhen(Flip){
            Zn := ~Zn
        }
        //Flip := false.B
        //io.ToCNs.Message := Zn | Tn.orR
        val Tn = RegInit(0.U(CountWidth.W))
        
        
        /********AdderNetwork********/
        /*
            fn is the number of unsatisfied check node
            the sum of Message from CNs
        */
        val MessFromCNs = VecInit(Seq.fill(ColWeight)(0.U(ColumnMessWidth.W)))
        for (i <-0 until ColWeight){
            MessFromCNs(i) := io.FromCNs(i).Message
        }
        val Fn = Wire (UInt(ColumnMessWidth.W))
        Fn := MessFromCNs.reduce(_ + _)
        /**********Comparactor***********/
        /*
            if(fn > Tn)
        */
        Flip := Fn > Tn

        /*********Tn Counter**********/
        /*
            when count is odd,it means that
            zn is diffrent from zn0, 
        */
        
        /*
            Tn Inital Table
                k = 0 , 1, 2 .... floor(column weight/2)-2
                Tn = k + floor(c/2)  (k*a <= |Yn| <= (k+1)*a)
                     floor(c/2)*2-1  (|Yn| >= (floor(c/2)-1)*a)
        */
        val AbsYn = Mux(io.Init.Yn(VaribleWidth-1),~io.Init.Yn+1.U,io.Init.Yn)
        
        when(io.Init.Flag){
            for(k <-0 to ColWeight/2-2){
                when(AbsYn <= ((k+1)*Alpha.toInt).U&&AbsYn>=(k*Alpha.toInt).U){
                    Tn := (k+ColWeight/2).U
                }.elsewhen(AbsYn >= ((ColWeight/2-1)*Alpha.toInt).U){
                    Tn := (ColWeight/2+ColWeight/2-1).U
                }
            }
        }.elsewhen((Zn0^Zn).orR && (Tn >= 0.U(ColumnMessWidth.W))){
            Tn := Tn - 1.U
        }.elsewhen(Tn <= ~0.U(ColumnMessWidth.W)){
            Tn := Tn + 1.U
        }

        /********OutPut********/
        for(i <- 0 until ColWeight)
            io.ToCNs(i).Message := Zn
        io.Varible := Zn
    }
    else if (Algorithm == "MinSum"){
        /*
            APP = Initial + ADD_C2V
            V2C = Initial + ADD_C2V_exclude_own
        */
        //RegInitial
        val InitialReg = RegInit(0.U(V2CMessageWidth.W))
        when(io.Init.Flag){
            InitialReg := io.Init.Yn
        }
        //ADD_C2V
        val MessFromCNs = VecInit(Seq.fill(ColWeight)(0.U(ColumnMessWidth.W)))
        for (i <-0 until ColWeight){
            val sign = io.FromCNs(i).Message(C2VMessageWidth-1)
            MessFromCNs(i) := Fill(ColumnMessWidth-C2VMessageWidth,sign) ## io.FromCNs(i).Message
        }
        val ADD = Wire (UInt(ColumnMessWidth.W))
        ADD := MessFromCNs.reduce(_ + _)
        //V2C Reg
        val V2CReg = Seq.fill(ColWeight)(RegInit(0.U(V2CMessageWidth.W)))
        for(i <- 0 until ColWeight){
            //V2CReg(i) := InitialReg + ADD - MessFromCNs(i)
            io.ToCNs(i).Message := V2CReg(i)
        }
        //About VNUworking
        ///val WaitCNU = RegInit(1.U(1.W))
        for(i <- 0 until ColWeight){
            when(io.Init.Flag){
            V2CReg(i) := io.Init.Yn
            }.elsewhen(io.Cnuvalid){
                //val ADDsub = Wire (UInt(ColumnMessWidth.W))
                //ADDsub := InitialReg + ADD - MessFromCNs(i)
                V2CReg(i) := InitialReg + ADD - MessFromCNs(i)
            }
        }
        //APP < 0???
        io.Varible := ADD.asSInt < 0.S//ADD(ColumnMessWidth-1)===1.U
    }
    
}