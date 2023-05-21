package  ldpc
import circt.stage._
import ldpc._

object Elaborate extends App {
  def top = new LDPC() // VNU LDPC CNU
  val useMFC = true // use MLIR-based firrtl compiler
  val generator = Seq(
    chisel3.stage.ChiselGeneratorAnnotation(() => top),
  )
  if (useMFC) {
    (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
  } else {
    (new chisel3.stage.ChiselStage).execute(args, generator)
  }
}
