#ifndef COMMON
#define COMMON


#include <stdint.h>
#include <stdbool.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include "types.h"

//这里的矩阵参数其实没什么用
#define BASE_COL_NUM   24
#define BASE_ROW_NUM   4
#define BLK_SIZE  96

#define VN_NUM  BASE_COL_NUM*BLK_SIZE
#define CN_NUM  BASE_ROW_NUM*BLK_SIZE


#define LLR_INIT_TABLE 2
double SigmaStart = 0.5;
double SigmaEnd   = 0.1;
double SigmaStep  = 0.01;

double RATE       = (double)(BASE_COL_NUM-BASE_ROW_NUM)/(double)(BASE_COL_NUM);

int MaxDecodeTime = 100 ;
int MaxErrorNum   = 10    ;


#define word_t uint64_t



#define Assert(cond, format, ...) \
  do { \
    if (!(cond)) { \
        (fflush(stdout), fprintf(stderr, ANSI_FMT(format, ANSI_FG_RED) "\n", ##  __VA_ARGS__)); \
           extern FILE* log_fp; fflush(log_fp); \
      extern void assert_fail_msg(); \
      assert_fail_msg(); \
      assert(cond); \
    } \
  } while (0)

#define panic(format, ...) Assert(0, format, ## __VA_ARGS__)

#define panic_on(cond, s) \
  ({ if (cond) { \
      putstr("AM Panic: "); putstr(s); \
      putstr(" @ " __FILE__ ":" TOSTRING(__LINE__) "  \n"); \
      halt(1); \
    } })
//#define panic(s) panic_on(1, s)
#define log_write(...) IFDEF(CONFIG_TARGET_NATIVE_ELF, \
  do { \
    extern FILE* log_fp; \
    extern bool log_enable(); \
    if (log_enable()) { \
      fprintf(log_fp, __VA_ARGS__); \
      fflush(log_fp); \
    } \
  } while (0) \
)

#define _Log(...) \
  do { \
    printf(__VA_ARGS__); \
    log_write(__VA_ARGS__); \
  } while (0)


#define Log(format, ...) \
    _Log(ANSI_FMT("[%s:%d %s] " format, ANSI_FG_BLUE) "\n", \
        __FILE__, __LINE__, __func__, ## __VA_ARGS__)


#endif