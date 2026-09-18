import chisel3._

class Main extends Module {
  val io = IO(new Bundle {})
}

class appMain extends App {
  emitSystemVerilog(new Main(), Array("--target-dir", "output"))
}
