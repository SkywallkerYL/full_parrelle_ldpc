#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <stdlib.h>
#include <time.h>
#include <random>

#include "VLDPC.h"
#include "VLDPC___024root.h"
// #include "VLDPC__Dpi.h"
#include "verilated_vcd_c.h"
// #include "svdpi.h"
#include "types.h"
#include "common.h"
#include <string>
//#define WAVE
using namespace std;
// #include "verilated_dpi.h"
/*******Verilator Sim*********/
VerilatedContext *contextp = NULL;
VerilatedVcdC *tfp = NULL;
// static VRiscvCpu* top;
VLDPC *top;

// 初始化
void sim_init()
{
#ifdef WAVE
  contextp = new VerilatedContext;
  // top = new VRiscvCpu;
  tfp = new VerilatedVcdC;
#endif
  top = new VLDPC;
#ifdef WAVE
  contextp->traceEverOn(true);
  top->trace(tfp, 0);
  tfp->open("wave.vcd");
#endif
}
void step_and_dump_wave()
{
  top->eval();
#ifdef WAVE
  contextp->timeInc(1);
  tfp->dump(contextp->time());
#endif
}
void sim_exit()
{
  step_and_dump_wave();
#ifdef WAVE
  tfp->close();
#endif
  delete top;
  delete contextp;
}
void clockntimes(int n)
{

  int temp = n;
  while (temp >= 1)
  {
    top->clock = 0;
    step_and_dump_wave();
    top->clock = 1;
    step_and_dump_wave();
    temp--;
  }
}
void reset(int n)
{
  top->reset = 0b1;
  clockntimes(n);
  top->reset = 0b0;
}
/************Random Gen***************/
static default_random_engine channel(static_cast<unsigned>(time(NULL)));
double RandomGen(double sigma)
{
  normal_distribution<double> nd(0, sigma);
  return nd(channel);
}
/************Yn Initial Table*************/
int YnInitial(double Noise)
{
  //all zero code
  double llr_init = 1.0 + Noise;
#if LLR_INIT_TABLE == 1
  if (llr_init < 0)
    return -1;
  else
    return 1;
#elif LLR_INIT_TABLE == 2
  if(llr_init < -0.3144384671){
    return -4;
  }else if (llr_init < 0){
    return -1;
  }else if (llr_init < 0.3144384671){
    return 1;
  }else{
    return 4;
  }
#endif
}


int main(int argc, char *argv[])
{
  sim_init();
  reset(5);
  /*********SIMULATION*********/
  for (double sigma = SigmaStart; sigma >= SigmaEnd; sigma -= SigmaStep)
  {
    while (1)
    {
      if (top->io_FailNum >= MaxErrorNum && top->io_TotalNum >= MaxDecodeTime)
        break;
      /*********LLR_Initial*********/
      top->io_DecodeFlag = 1;
      #include "../build/Table.h"
      clockntimes(1);
      top->io_DecodeFlag = 0;
      while (!top->io_DecodeDown)
      {
        clockntimes(1);
      }
      // 一次译码完成，冲刷Iter
      top->io_Flush = 1;
      clockntimes(1);
      top->io_Flush = 0;
      //printf("sigma:%f\t ERROR:%ld\t TOTAL:%ld STATE:%d\n",
      //     sigma, top->io_FailNum, top->io_TotalNum,top->io_state);
    }
    double FER = (double)top->io_FailNum / (double)top->io_TotalNum;
    printf("sigma:%f\t ERROR:%ld\t TOTAL:%ld\t FER:%f \n",
           sigma, top->io_FailNum, top->io_TotalNum, FER);
    // 一个sigma的仿真完成，冲刷帧计数器
    top->io_SNRFlush = 1;
    clockntimes(1);
    top->io_SNRFlush = 0;
    //break;
  }

  sim_exit();
  return 0;
}
